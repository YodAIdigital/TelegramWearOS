package app.yodai.telewear.audio

import android.content.Context
import android.speech.tts.TextToSpeech

/**
 * Read-aloud for messages via the on-watch TTS engine. Constructed lazily —
 * the engine only spins up the first time someone taps "Speak".
 */
class TextSpeaker(context: Context) {

    private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        // If init hasn't finished yet the utterance is dropped; the engine is
        // usually ready well within the first second after first construction.
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "telewear_speak")
    }

    fun stop() {
        if (ready) runCatching { tts.stop() }
    }
}
