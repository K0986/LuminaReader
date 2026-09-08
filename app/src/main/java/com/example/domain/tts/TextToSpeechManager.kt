package com.example.domain.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSentenceIndex = MutableStateFlow(0)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    private val _sentencesFlow = MutableStateFlow<List<String>>(emptyList())
    val sentencesFlow: StateFlow<List<String>> = _sentencesFlow.asStateFlow()

    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft: StateFlow<Int?> = _sleepTimerMinutesLeft.asStateFlow()

    var onPageFinishedListener: (() -> Unit)? = null

    private var sentences = listOf<String>()
    private var currentIndex = 0
    private var speed = 1.0f
    private var pitch = 1.0f

    private val mainHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        if (currentIndex < sentences.size - 1) {
                            currentIndex++
                            _currentSentenceIndex.value = currentIndex
                            speakCurrent()
                        } else {
                            stop()
                            onPageFinishedListener?.invoke()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    mainHandler.post { stop() }
                }
            })
        }
    }

    fun setSpeed(rate: Float) {
        speed = rate
        tts?.setSpeechRate(rate)
    }

    fun setPitch(p: Float) {
        pitch = p
        tts?.setPitch(p)
    }

    fun setContent(text: String, startFromIndex: Int = 0) {
        // Break into sentences by punctuation (. ! ? \n)
        val rawSentences = text.split("(?<=[.!?])\\s+|\n{2,}".toRegex())
            .map { it.trim() }
            .filter { it.isNotBlank() }
        sentences = if (rawSentences.isNotEmpty()) rawSentences else if (text.isNotBlank()) listOf(text.trim()) else emptyList()
        _sentencesFlow.value = sentences
        currentIndex = startFromIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        _currentSentenceIndex.value = currentIndex
    }

    /**
     * Speaks arbitrary text (e.g. user selected phrase or word) immediately.
     */
    fun speakText(text: String) {
        if (!isInitialized || text.isBlank()) return
        setContent(text, startFromIndex = 0)
        play()
    }

    /**
     * Finds the sentence containing selectedText and speaks from there.
     */
    fun speakFromSelection(fullText: String, selectedText: String) {
        if (sentences.isEmpty() || !sentences.joinToString(" ").contains(selectedText.take(20))) {
            setContent(fullText)
        }
        val cleanSelection = selectedText.trim()
        val foundIndex = sentences.indexOfFirst { it.contains(cleanSelection, ignoreCase = true) }
        if (foundIndex >= 0) {
            currentIndex = foundIndex
            _currentSentenceIndex.value = foundIndex
        }
        play()
    }

    fun jumpToSentence(index: Int) {
        if (index in sentences.indices) {
            currentIndex = index
            _currentSentenceIndex.value = index
            if (_isPlaying.value) {
                speakCurrent()
            }
        }
    }

    fun play() {
        if (!isInitialized || sentences.isEmpty()) return
        speakCurrent()
    }

    fun pause() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        cancelSleepTimer()
    }

    fun nextSentence() {
        if (currentIndex < sentences.size - 1) {
            currentIndex++
            _currentSentenceIndex.value = currentIndex
            if (_isPlaying.value) {
                speakCurrent()
            }
        }
    }

    fun previousSentence() {
        if (currentIndex > 0) {
            currentIndex--
            _currentSentenceIndex.value = currentIndex
            if (_isPlaying.value) {
                speakCurrent()
            }
        }
    }

    private fun speakCurrent() {
        if (currentIndex in sentences.indices) {
            val sentence = sentences[currentIndex]
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sent_$currentIndex")
            tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, params, "sent_$currentIndex")
            _isPlaying.value = true
        }
    }

    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        _sleepTimerMinutesLeft.value = minutes
        var remaining = minutes

        sleepTimerRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    stop()
                    _sleepTimerMinutesLeft.value = null
                } else {
                    _sleepTimerMinutesLeft.value = remaining
                    mainHandler.postDelayed(this, 60_000)
                }
            }
        }
        mainHandler.postDelayed(sleepTimerRunnable!!, 60_000)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let { mainHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        _sleepTimerMinutesLeft.value = null
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
