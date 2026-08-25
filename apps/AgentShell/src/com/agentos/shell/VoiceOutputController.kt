package com.agentos.shell

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

internal class VoiceOutputController(
    context: Context,
    private val onFinished: () -> Unit,
) {
    private var ready = false
    private var pending: String? = null
    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                textToSpeech.language = Locale.getDefault()
                textToSpeech.setOnUtteranceProgressListener(listener)
                pending?.let(::speak)
                pending = null
            } else {
                pending = null
                onFinished()
            }
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        if (textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID) == TextToSpeech.ERROR) {
            onFinished()
        }
    }

    fun stop() {
        pending = null
        if (ready) textToSpeech.stop()
        onFinished()
    }

    fun close() {
        stop()
        textToSpeech.shutdown()
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = onFinished()
        override fun onStop(utteranceId: String?, interrupted: Boolean) = onFinished()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onFinished()
    }

    companion object {
        private const val UTTERANCE_ID = "agentos-response"
    }
}
