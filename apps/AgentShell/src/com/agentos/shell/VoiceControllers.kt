package com.agentos.shell

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

internal class VoiceInputController(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val applicationContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
            onError("当前系统没有可用的语音识别引擎")
            return
        }
        val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(applicationContext)
            .also { recognizer = it }
        speechRecognizer.setRecognitionListener(listener)
        speechRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
        onListening(true)
    }

    fun stop() {
        recognizer?.stopListening()
        onListening(false)
    }

    fun close() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle) {
            onListening(false)
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isEmpty()) onError("没有识别到语音") else onResult(text)
        }

        override fun onError(error: Int) {
            onListening(false)
            onError(if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) "没有麦克风权限" else "语音识别失败，请重试")
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

internal class VoiceOutputController(context: Context) {
    private var ready = false
    private var pending: String? = null
    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                textToSpeech.language = Locale.getDefault()
                pending?.let(::speak)
                pending = null
            }
        }
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "agentos-response")
    }

    fun close() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
