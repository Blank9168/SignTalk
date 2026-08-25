package com.example.signtalk.recognition

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Thin wrapper around Android's built-in TextToSpeech for gesture -> speech output. */
class SpeechOutput(context: Context) {

    private var isReady = false
    private var pendingRate = 1.0f

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        isReady = status == TextToSpeech.SUCCESS
        if (isReady) {
            tts.language = Locale.getDefault()
            tts.setSpeechRate(pendingRate)
        }
    }

    fun setRate(rate: Float) {
        pendingRate = rate
        if (isReady) tts.setSpeechRate(rate)
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "signtalk_utterance")
    }

    fun setOnDone(onDone: () -> Unit) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onDone()
            override fun onError(utteranceId: String?) = Unit
        })
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
