package com.agentos.shell

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

internal class VoiceOutputController(
    context: Context,
    private val onFinished: () -> Unit,
) {
    private var ready = false
    private var failed = false
    private var closed = false
    private var pending: Pair<String, String>? = null
    private val gate = UtteranceGate()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (closed) return@post
                ready = status == TextToSpeech.SUCCESS
                if (ready) {
                    textToSpeech.language = Locale.getDefault()
                    textToSpeech.setOnUtteranceProgressListener(listener)
                    pending?.let { (id, text) -> play(id, text) }
                    pending = null
                } else {
                    failed = true
                    pending = null
                    if (gate.cancel()) onFinished()
                }
            }
        }
    }

    fun speak(text: String) {
        if (closed) return
        val id = gate.start()
        if (failed) { finished(id); return }
        if (!ready) {
            pending = id to text
            return
        }
        play(id, text)
    }

    private fun play(id: String, text: String) {
        if (textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) finished(id)
    }

    private fun finished(id: String?) {
        main.post { if (gate.finish(id)) onFinished() }
    }

    fun stop() {
        pending = null
        val wasSpeaking = gate.cancel()
        if (ready) textToSpeech.stop()
        if (wasSpeaking) onFinished()
    }

    fun close() {
        closed = true
        stop()
        textToSpeech.shutdown()
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
    }
}
