package com.example.signtalk.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Renders one FSL sign as a two-hand landmark animation performed by a real,
 * connected arm (shoulder -> elbow -> wrist) reaching out from an illustrated
 * person, instead of a bare skeleton or a hand floating disconnected from a
 * static portrait. The person portrait is a static bundled image
 * (`avatar_woman.png` / `avatar_man.png` in assets); only the *hand* is ever
 * backed by real tracked data -- a real 30-frame sequence in the same
 * 126-feature layout (63 left-hand + 63 right-hand floats, each hand
 * centered on its own wrist and scaled -- see
 * [com.example.signtalk.recognition.LandmarkNormalizer] /
 * `ai/dataset/landmarks.py`) that both live recognition and
 * [com.example.signtalk.recognition.SignLandmarksProvider] use.
 *
 * The arm itself is NOT tracked data -- this dataset only ever recorded each
 * hand's own shape, never where the hand was relative to the shoulder/face,
 * so there's no real signal for "this sign reaches to the forehead" vs.
 * "stays at chest height." The arm always reaches to the same fixed
 * chest-height spot the hand already animates in; a classic 2-bone IK solve
 * (shoulder + target -> elbow, via the law of cosines) picks a natural elbow
 * bend for whatever that reach happens to be, so the arm always looks
 * anatomically plausible even though its *target* is fixed rather than
 * per-sign. The shoulder position and arm segment lengths below were
 * measured directly (pixel analysis, not guessed) from both a reference
 * animation the user supplied and this app's own bundled avatar images --
 * the two matched closely, which is what makes reusing these proportions
 * here trustworthy (see docs/MOBILE_APP_NOTES.md for the analysis).
 *
 * The portrait is drawn "contain"-fit (the whole image always visible, no
 * cropping) so it can't accidentally clip the person; every anchor below is
 * expressed as a *fraction* of the portrait's own pixel size so it lands in
 * the same natural spot regardless of view size or which portrait is shown.
 */
class SignAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        private const val LANDMARKS_PER_HAND = 21
        private const val COORDS_PER_LANDMARK = 3
        private const val FRAME_INTERVAL_MS = 90L // ~11fps

        // Fraction of the portrait's own width/height where hands should be
        // held -- below the hoodie's neckline, centered on the torso, above
        // where this pose's own arms/sleeves become visible at the sides.
        private const val CHEST_ANCHOR_X_FRAC = 0.50f
        private const val CHEST_ANCHOR_Y_FRAC = 0.716f
        // Max distance, as a fraction of the portrait's own height, that any
        // single landmark of a hand is ever allowed to sit from that hand's
        // anchor point. This is a CAP, not a fixed size -- see the auto-fit
        // note on drawHand() for why a fixed per-pixel scale doesn't work
        // for this dataset (real per-sign landmark spread varies by roughly
        // 35x between a closed hand and a wide open/waving one, and a few
        // source samples have clearly corrupted, wildly out-of-range
        // coordinates). 0.20 matches this view's previous fixed hand scale,
        // so a typical/compact sign still renders at very close to its old
        // size; only signs whose real spread would exceed that footprint
        // get shrunk to fit, which is what keeps every sign's fingers from
        // reaching up over the character's collar and into their face.
        private const val HAND_MAX_REACH_FRAC = 0.20f
        // How far apart two hands sit, as a fraction of the portrait's width.
        private const val TWO_HAND_OFFSET_FRAC = 0.13f

        // Shoulder position + arm segment lengths, measured (not guessed) from
        // both the user's reference animation and this app's own avatar
        // portraits -- see the class doc above.
        private const val SHOULDER_Y_FRAC = 0.510f
        private const val SHOULDER_HALF_WIDTH_FRAC = 0.066f // fraction of drawW
        private const val UPPER_ARM_FRAC = 0.167f // fraction of drawH
        private const val FOREARM_FRAC = 0.151f // fraction of drawH
        private const val SLEEVE_WIDTH_FRAC = 0.09f // fraction of drawH

        // Finger chains start at the base knuckle, NOT the wrist -- the wrist
        // end is instead covered by a rounded palm blob (see drawHand()), so
        // fingers visually grow out of one continuous glove shape rather than
        // each being its own separate rod meeting a bare point.
        private val THUMB = intArrayOf(1, 2, 3, 4)
        private val INDEX = intArrayOf(5, 6, 7, 8)
        private val MIDDLE = intArrayOf(9, 10, 11, 12)
        private val RING = intArrayOf(13, 14, 15, 16)
        private val PINKY = intArrayOf(17, 18, 19, 20)
        private val FINGERS = arrayOf(THUMB, INDEX, MIDDLE, RING, PINKY)
        // Stroke width per finger segment, base(near palm) -> tip -- deliberately
        // chunky with only a light taper: a rounded "cartoon glove" capsule
        // look, not thin bone-like rods.
        private val SEGMENT_WIDTHS = floatArrayOf(34f, 30f, 26f)
    }

    private val avatarBitmaps: List<Bitmap> by lazy {
        listOf("avatar_woman.png", "avatar_man.png").mapNotNull { name ->
            try {
                context.assets.open(name).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        }
    }
    private var avatarIndex = 0

    private var sequence: Array<FloatArray>? = null
    private var frameIndex = 0
    private var isRunning = false

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            val frames = sequence
            if (frames == null || !isRunning) return
            frameIndex = (frameIndex + 1) % frames.size
            invalidate()
            handler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Sampled directly from the current avatar_woman.png / avatar_man.png
    // (skin ~#F8AB91, outline ~near-black) so the animated arm+hand reads as
    // *this* character's own limb, not a generic one. The sleeve is
    // deliberately NOT the hoodie's own body color (~#63C9C3, sampled
    // directly off the illustration): a same-color sleeve drawn on top of
    // the hoodie is invisible except for its thin outline, which is exactly
    // why the arm was reported as not showing up at all. A darker shade of
    // the same teal reads as a natural fold/shadow on the fabric instead of
    // a mismatched color, while still standing out clearly against the body.
    private val skinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F8AB91")
        style = Paint.Style.FILL
    }
    private val skinOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val sleevePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#4D9C98")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sleeveOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.GRAY
        textSize = 38f
        textAlign = Paint.Align.CENTER
    }

    fun setSequence(newSequence: Array<FloatArray>?) {
        sequence = newSequence
        frameIndex = 0
        invalidate()
    }

    fun play() {
        if (isRunning || sequence == null) return
        isRunning = true
        handler.postDelayed(tickRunnable, FRAME_INTERVAL_MS)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }

    /** Cycles to the next bundled avatar portrait. */
    fun toggleAvatar() {
        if (avatarBitmaps.isEmpty()) return
        avatarIndex = (avatarIndex + 1) % avatarBitmaps.size
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val bitmap = avatarBitmaps.getOrNull(avatarIndex)
        if (bitmap == null) {
            canvas.drawText("Avatar image unavailable", w / 2f, h / 2f, emptyPaint)
            return
        }

        // "Contain" fit: scale the whole portrait to fit inside the view without
        // cropping, centered -- so it never accidentally clips the person.
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()
        val scale = minOf(w / bw, h / bh)
        val drawW = bw * scale
        val drawH = bh * scale
        val offsetX = (w - drawW) / 2f
        val offsetY = (h - drawH) / 2f
        val destRect = RectF(offsetX, offsetY, offsetX + drawW, offsetY + drawH)
        canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), destRect, bitmapPaint)

        // Map every anchor (a fraction of the portrait's own pixel size) into
        // view space using that same transform, so everything lands in the
        // same natural spot regardless of view size or which portrait is shown.
        val centerX = offsetX + 0.5f * drawW
        val anchorX = offsetX + CHEST_ANCHOR_X_FRAC * drawW
        val anchorY = offsetY + CHEST_ANCHOR_Y_FRAC * drawH
        val handMaxReachPx = HAND_MAX_REACH_FRAC * drawH
        val twoHandOffset = TWO_HAND_OFFSET_FRAC * drawW

        val shoulderY = offsetY + SHOULDER_Y_FRAC * drawH
        val shoulderHalfWidth = SHOULDER_HALF_WIDTH_FRAC * drawW
        val leftShoulderX = centerX - shoulderHalfWidth
        val rightShoulderX = centerX + shoulderHalfWidth
        val upperArmLen = UPPER_ARM_FRAC * drawH
        val forearmLen = FOREARM_FRAC * drawH
        val sleeveWidth = SLEEVE_WIDTH_FRAC * drawH

        val frame = sequence?.getOrNull(frameIndex)
        if (frame == null) {
            canvas.drawText("No animation available", w / 2f, offsetY + drawH + 24f, emptyPaint)
            return
        }

        val left = extractHand(frame, 0)
        val right = extractHand(frame, LANDMARKS_PER_HAND * COORDS_PER_LANDMARK)
        val leftPresent = !isEmptyHand(left)
        val rightPresent = !isEmptyHand(right)

        // Arms are drawn before hands so the hand's own shape covers the
        // wrist end of the sleeve, hiding the seam between them.
        when {
            leftPresent && rightPresent -> {
                val leftHandX = anchorX - twoHandOffset
                val rightHandX = anchorX + twoHandOffset
                drawArm(canvas, leftShoulderX, shoulderY, leftHandX, anchorY, upperArmLen, forearmLen, centerX, sleeveWidth)
                drawArm(canvas, rightShoulderX, shoulderY, rightHandX, anchorY, upperArmLen, forearmLen, centerX, sleeveWidth)
                drawHand(canvas, left, leftHandX, anchorY, handMaxReachPx)
                drawHand(canvas, right, rightHandX, anchorY, handMaxReachPx)
            }
            leftPresent -> {
                // One-handed signs don't record which physical hand the signer
                // used (see RecognitionEngine's hand-slot canonicalization note),
                // so the screen side here is an arbitrary but consistent choice.
                drawArm(canvas, rightShoulderX, shoulderY, anchorX, anchorY, upperArmLen, forearmLen, centerX, sleeveWidth)
                drawHand(canvas, left, anchorX, anchorY, handMaxReachPx)
            }
            rightPresent -> {
                drawArm(canvas, rightShoulderX, shoulderY, anchorX, anchorY, upperArmLen, forearmLen, centerX, sleeveWidth)
                drawHand(canvas, right, anchorX, anchorY, handMaxReachPx)
            }
            else -> Unit
        }
    }

    /**
     * Classic 2-bone IK: given a shoulder anchor and a hand target, solves for
     * the elbow position via the law of cosines, then picks whichever of the
     * two valid elbow solutions bends *away* from the body's centerline --
     * the anatomically natural direction for reaching toward the chest
     * (matches the elbow poses in the reference animation the user supplied).
     */
    private fun solveElbow(
        shoulderX: Float, shoulderY: Float,
        targetX: Float, targetY: Float,
        l1: Float, l2: Float,
        centerX: Float
    ): FloatArray {
        val dx = targetX - shoulderX
        val dy = targetY - shoulderY
        val rawDistance = hypot(dx, dy)
        val distance = rawDistance.coerceIn(abs(l1 - l2) + 0.5f, l1 + l2 - 0.5f)
        val baseAngle = atan2(dy, dx)
        val cosAngleA = ((l1 * l1 + distance * distance - l2 * l2) / (2f * l1 * distance)).coerceIn(-1f, 1f)
        val angleA = acos(cosAngleA)
        val angle1 = baseAngle + angleA
        val angle2 = baseAngle - angleA
        val elbow1X = shoulderX + l1 * cos(angle1)
        val elbow2X = shoulderX + l1 * cos(angle2)
        val angle = if (shoulderX < centerX) {
            if (elbow1X < elbow2X) angle1 else angle2
        } else {
            if (elbow1X > elbow2X) angle1 else angle2
        }
        return floatArrayOf(shoulderX + l1 * cos(angle), shoulderY + l1 * sin(angle))
    }

    /** Draws one sleeve-colored arm (shoulder -> elbow -> hand target), outlined to match the avatar art. */
    private fun drawArm(
        canvas: Canvas,
        shoulderX: Float, shoulderY: Float,
        handX: Float, handY: Float,
        l1: Float, l2: Float,
        centerX: Float,
        sleeveWidth: Float
    ) {
        val (elbowX, elbowY) = solveElbow(shoulderX, shoulderY, handX, handY, l1, l2, centerX).let { it[0] to it[1] }
        val outline = Paint(sleeveOutlinePaint).apply { strokeWidth = sleeveWidth + 5f }
        val fill = Paint(sleevePaint).apply { strokeWidth = sleeveWidth }
        canvas.drawLine(shoulderX, shoulderY, elbowX, elbowY, outline)
        canvas.drawLine(elbowX, elbowY, handX, handY, outline)
        canvas.drawLine(shoulderX, shoulderY, elbowX, elbowY, fill)
        canvas.drawLine(elbowX, elbowY, handX, handY, fill)
    }

    private fun extractHand(frame: FloatArray, offset: Int): FloatArray =
        frame.copyOfRange(offset, offset + LANDMARKS_PER_HAND * COORDS_PER_LANDMARK)

    private fun isEmptyHand(hand: FloatArray): Boolean = hand.all { it == 0f }

    /**
     * Draws one hand as a rounded palm blob + five chunky capsule-shaped
     * fingers, using real landmark data.
     *
     * Auto-fit scale: this dataset's per-sign hand spread (each landmark's
     * normalized distance from its own wrist) varies enormously between
     * signs -- a closed fist sample might barely reach past the palm, while
     * an open/waving sign can reach 2-3x farther, and a handful of source
     * samples have clearly corrupted coordinates reaching 30-75x farther
     * (almost certainly a near-zero hand-size denominator during that
     * frame's normalization, not a real hand shape). A single fixed pixel
     * scale can't serve both ends of that range: sized for a typical sign
     * it sends the wide/corrupted ones flying off past the character's own
     * face and off-screen; sized to contain those, it shrinks every normal
     * sign to a speck. So instead of a fixed scale, solve for the largest
     * scale that keeps every one of this hand's 21 landmarks within
     * [maxReachPx] of the anchor -- a typical, compact sign still renders
     * at close to full size, while a wide-reaching or corrupted sample
     * automatically shrinks just enough to stay within that same footprint.
     */
    private fun drawHand(canvas: Canvas, hand: FloatArray, cx: Float, cy: Float, maxReachPx: Float) {
        var maxExtent = 1f
        for (i in 0 until LANDMARKS_PER_HAND) {
            val ex = abs(hand[i * 3])
            val ey = abs(hand[i * 3 + 1])
            if (ex > maxExtent) maxExtent = ex
            if (ey > maxExtent) maxExtent = ey
        }
        val scale = maxReachPx / maxExtent
        fun x(i: Int) = cx + hand[i * 3] * scale
        fun y(i: Int) = cy + hand[i * 3 + 1] * scale

        // skinOutlinePaint is a STROKE paint; a filled copy is needed for the
        // solid discs (palm blob + finger joints) below.
        val outlineFill = Paint(skinOutlinePaint).apply { style = Paint.Style.FILL }

        // Rounded palm blob, centered on the wrist and sized (measured per
        // frame, not a fixed guess) to always reach past every finger's own
        // base knuckle -- so the whole hand reads as fingers growing out of
        // one solid rounded glove shape, never bare rods meeting a point,
        // however open, closed, or oddly-scaled this particular sign is.
        var palmRadius = SEGMENT_WIDTHS.first()
        for (i in intArrayOf(5, 9, 13, 17)) {
            val d = hypot(x(i) - x(0), y(i) - y(0))
            if (d > palmRadius) palmRadius = d
        }
        palmRadius += 6f
        canvas.drawCircle(x(0), y(0), palmRadius + 4f, outlineFill)
        canvas.drawCircle(x(0), y(0), palmRadius, skinPaint)

        // Each finger: a chain of thick, round-capped capsule segments from
        // its base knuckle to its tip -- deliberately chunky with only a
        // light taper (never thin rods), with a filled joint disc at every
        // knuckle along the way (not just the tip) so bends stay smooth and
        // rounded instead of reading as jointed bones.
        for (finger in FINGERS) {
            for (seg in 0 until finger.size - 1) {
                val a = finger[seg]
                val b = finger[seg + 1]
                val segWidth = SEGMENT_WIDTHS.getOrElse(seg) { SEGMENT_WIDTHS.last() }
                val outline = Paint(skinOutlinePaint).apply {
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = segWidth + 6f
                }
                val fill = Paint(skinPaint).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = segWidth
                }
                canvas.drawLine(x(a), y(a), x(b), y(b), outline)
                canvas.drawLine(x(a), y(a), x(b), y(b), fill)
            }
            for ((idx, point) in finger.withIndex()) {
                val width = SEGMENT_WIDTHS.getOrElse(idx.coerceAtMost(SEGMENT_WIDTHS.size - 1)) { SEGMENT_WIDTHS.last() }
                canvas.drawCircle(x(point), y(point), width / 2f + 3f, outlineFill)
                canvas.drawCircle(x(point), y(point), width / 2f, skinPaint)
            }
        }
    }
}
