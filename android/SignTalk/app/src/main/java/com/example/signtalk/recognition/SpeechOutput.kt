package com.example.signtalk.recognition

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Thin wrapper around Android's built-in TextToSpeech for gesture -> speech output.
 *
 * Each utterance is spoken in the language of the sign it came from: Filipino (fil-PH) for
 * FSL signs, whose labels are Filipino words ("Isa", "Magandang Umaga"), and English (en-US)
 * for ASL signs ("Thank You"). If the phone has no Filipino voice installed, it falls back
 * to the device default and then English, so something is always spoken.
 */
class SpeechOutput(context: Context) {

    enum class Language(val locale: Locale) {
        FILIPINO(Locale.forLanguageTag("fil-PH")),
        ENGLISH(Locale.US)
    }

    private var isReady = false
    private var pendingRate = 1.0f
    private var utteranceCounter = 0
    private var onStartListener: ((utteranceId: String, startedAtMs: Long) -> Unit)? = null
    private var onDoneListener: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            tts.setSpeechRate(pendingRate)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onStartListener?.invoke(utteranceId.orEmpty(), SystemClock.elapsedRealtime())
                }
                override fun onDone(utteranceId: String?) {
                    onDoneListener?.invoke()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = Unit
            })
        }
    }

    fun setRate(rate: Float) {
        pendingRate = rate
        if (isReady) tts.setSpeechRate(rate)
    }

    /**
     * Speaks [text] in [language], replacing whatever is currently being spoken.
     * Returns the utterance id (for latency logging), or null if TTS isn't ready.
     */
    fun speak(text: String, language: Language = Language.ENGLISH): String? =
        enqueue(text, language, TextToSpeech.QUEUE_FLUSH)

    /**
     * Speaks a sequence of words, each in its own language (a transcript can mix FSL and ASL
     * signs), as one continuous output.
     */
    fun speakSequence(parts: List<Pair<String, Language>>) {
        parts.forEachIndexed { index, (text, language) ->
            enqueue(text, language, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD)
        }
    }

    fun stop() {
        if (isReady) tts.stop()
    }

    private fun enqueue(text: String, language: Language, queueMode: Int): String? {
        if (!isReady || text.isBlank()) return null
        applyLanguage(language)
        val id = "signtalk_${utteranceCounter++}"
        tts.speak(text, queueMode, null, id)
        return id
    }

    private fun applyLanguage(language: Language) {
        val candidates = listOf(language.locale, Locale.getDefault(), Locale.US)
        for (locale in candidates) {
            val availability = tts.isLanguageAvailable(locale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = locale
                return
            }
        }
    }

    /** Called when an utterance actually starts playing (used to measure speech latency). */
    fun setOnStart(listener: (utteranceId: String, startedAtMs: Long) -> Unit) {
        onStartListener = listener
    }

    fun setOnDone(onDone: () -> Unit) {
        onDoneListener = onDone
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
