"""
Shared MediaPipe hand-landmark extraction utility.

Mirrors the feature format used by the original SignTalk ai/ pipeline:
  - Up to 2 hands x 21 landmarks x (x, y, z) = 126 features per frame
  - A hand that isn't detected in a given frame is zero-filled
  - Left/right hand slots are assigned by MediaPipe's handedness label so the
    same physical hand lands in the same feature slice across frames

Landmarks are HAND-RELATIVE, not raw frame coordinates: each hand's 21
points are re-centered on its own wrist and scaled by its own wrist-to-
middle-finger-MCP distance. Raw MediaPipe coordinates are normalized to the
*video frame* (0..1 across the whole image), so a model trained on one
camera's framing (distance, hand position on screen) effectively memorizes
"where on screen the hand was" rather than "what shape the hand made" -- it
falls apart on a different camera/distance/framing. Hand-relative
normalization removes that dependency: the same gesture produces
(approximately) the same feature vector regardless of where the hand sits
in frame or how big it appears.
"""

import numpy as np
import mediapipe as mp

NUM_HANDS = 2
LANDMARKS_PER_HAND = 21
COORDS_PER_LANDMARK = 3
FEATURES_PER_FRAME = NUM_HANDS * LANDMARKS_PER_HAND * COORDS_PER_LANDMARK  # 126

mp_hands = mp.solutions.hands

WRIST_IDX = 0
MIDDLE_MCP_IDX = 9  # stable reference point for hand "size"
MIN_SCALE = 1e-4


def normalize_hand(coords, aspect=1.0):
    """coords: (21, 3) raw MediaPipe landmarks for one hand.

    aspect = frame_width / frame_height of the image the landmarks came from.
    MediaPipe returns x normalized by the frame WIDTH and y by the frame HEIGHT,
    so the raw numbers are anisotropic: the same hand looks ~3x "narrower" in a
    16:9 landscape clip than in a 9:16 portrait phone frame. Multiplying x (and
    z, which MediaPipe scales like x) by the aspect ratio puts both axes in the
    same units (pixels / frame height) before normalizing. Without this, a model
    trained on landscape clips got 16% on portrait phone clips (94% on its own
    validation split); with it, 53% on the same unseen phone clips.

    Re-centers on the wrist and scales by the wrist-to-middle-finger-MCP
    distance (xy only -- z from MediaPipe's single-camera depth estimate is
    noisier and not needed to define hand scale). Returns (21, 3).
    """
    coords = np.array(coords, dtype=np.float32, copy=True)
    coords[:, 0] *= aspect
    coords[:, 2] *= aspect
    wrist = coords[WRIST_IDX]
    centered = coords - wrist
    scale = np.linalg.norm(centered[MIDDLE_MCP_IDX, :2])
    if scale < MIN_SCALE:
        return centered  # degenerate hand box; avoid dividing by ~0
    return centered / scale


class HandLandmarkExtractor:
    """Wraps mediapipe.solutions.hands.Hands for per-frame feature extraction."""

    def __init__(self, static_image_mode=False, min_detection_confidence=0.5,
                 min_tracking_confidence=0.5):
        self._hands = mp_hands.Hands(
            static_image_mode=static_image_mode,
            max_num_hands=NUM_HANDS,
            min_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence,
        )

    def extract(self, frame_bgr):
        """Run MediaPipe Hands on one BGR frame; return a (126,) float32 vector.

        Slot 0 = Left hand (21*3=63), slot 1 = Right hand (21*3=63), as
        reported by MediaPipe's handedness classification. Undetected hands
        are zero-filled. Each detected hand is normalized relative to its
        own wrist position and size, in aspect-corrected units (see normalize_hand).
        """
        import cv2

        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        results = self._hands.process(frame_rgb)
        frame_h, frame_w = frame_bgr.shape[:2]
        aspect = frame_w / float(frame_h)

        left = np.zeros(LANDMARKS_PER_HAND * COORDS_PER_LANDMARK, dtype=np.float32)
        right = np.zeros(LANDMARKS_PER_HAND * COORDS_PER_LANDMARK, dtype=np.float32)

        if results.multi_hand_landmarks and results.multi_handedness:
            hands = []
            for hand_landmarks, handedness in zip(
                results.multi_hand_landmarks, results.multi_handedness
            ):
                coords = np.array(
                    [[lm.x, lm.y, lm.z] for lm in hand_landmarks.landmark],
                    dtype=np.float32,
                )
                label = handedness.classification[0].label  # "Left" or "Right"
                hands.append((label, coords[WRIST_IDX, 0], normalize_hand(coords, aspect).flatten()))

            if len(hands) == 2 and hands[0][0] == hands[1][0]:
                # MediaPipe sometimes gives both hands the same label; the old code
                # then let the second hand silently overwrite the first. Fall back
                # to image position: on an unmirrored frame the "Left" slot is the
                # hand at smaller x.
                hands.sort(key=lambda h: h[1])
                left, right = hands[0][2], hands[1][2]
            else:
                for label, _, feats in hands:
                    if label == "Left":
                        left = feats
                    else:
                        right = feats

        return np.concatenate([left, right])

    def close(self):
        self._hands.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()


def trim_to_hand_frames(frames, min_frames=3):
    """Drop frames where no hand was detected (all-zero rows).

    Clips start/end with hands down, so ~65% of raw frames were zero-filled.
    The live app only ever feeds hand-visible frames to the model, so training
    on zero-padded sequences caused a big live-vs-validation accuracy gap
    (92.8% val -> ~65% on hand-only input). Training and inference must see
    the same thing: hand-only frames, resampled to a fixed length. Idempotent.
    """
    frames = np.asarray(frames, dtype=np.float32)
    if frames.ndim != 2 or frames.shape[0] == 0:
        return frames
    keep = np.abs(frames).sum(axis=1) > 0
    return frames[keep] if keep.sum() >= min_frames else frames


def sample_to_fixed_length(frames, target_len=30):
    """Uniformly resample a (T, 126) sequence to (target_len, 126).

    Videos have variable frame counts; the LSTM expects a fixed sequence
    length, matching the 30-frame sequences used by the original pipeline.
    """
    frames = np.asarray(frames, dtype=np.float32)
    frames = trim_to_hand_frames(frames)
    t = frames.shape[0]
    if t == 0:
        return np.zeros((target_len, FEATURES_PER_FRAME), dtype=np.float32)
    if t == target_len:
        return frames
    indices = np.linspace(0, t - 1, target_len)
    indices = np.round(indices).astype(int)
    indices = np.clip(indices, 0, t - 1)
    return frames[indices]
