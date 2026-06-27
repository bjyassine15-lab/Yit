package com.example.ui

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.AudioController
import com.example.data.local.ChatMessage
import com.example.data.remote.GeminiLiveApi
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.WebSocket

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    val messages: StateFlow<List<ChatMessage>>

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isContinuousVoiceMode = MutableStateFlow(false)
    val isContinuousVoiceMode = _isContinuousVoiceMode.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText = _speechText.asStateFlow()

    private val _isApiKeyMissing = MutableStateFlow(false)
    val isApiKeyMissing = _isApiKeyMissing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var audioController: AudioController? = null
    private var webSocket: WebSocket? = null
    
    private var currentAssistantReply = java.lang.StringBuilder()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ChatRepository(database.chatDao())
        
        messages = repository.allMessages.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Verify API key is present
        val testApiKey = BuildConfig.GEMINI_API_KEY
        if (testApiKey.isEmpty() || testApiKey == "MY_GEMINI_API_KEY") {
            _isApiKeyMissing.value = true
        }
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || isApiKeyMissing.value) return

        _inputText.value = ""
        
        viewModelScope.launch {
            repository.insertMessage(text, isUser = true)
            
            if (_isContinuousVoiceMode.value && webSocket != null) {
                // Send via Live API WebSocket
                GeminiLiveApi.sendText(webSocket!!, text)
            } else {
                // Standard REST generation
                _isLoading.value = true
                val reply = repository.getGeminiResponse(text)
                _isLoading.value = false
                repository.insertMessage(reply, isUser = false)
            }
        }
    }

    fun toggleContinuousVoiceMode(enabled: Boolean) {
        if (isApiKeyMissing.value) return
        
        if (enabled) {
            startLiveSession()
        } else {
            stopLiveSession()
        }
    }

    private fun startLiveSession() {
        _isContinuousVoiceMode.value = true
        _speechText.value = "جاري الاتصال بأحمد (Live API)..."
        _errorMessage.value = null
        
        audioController = AudioController(getApplication())
        currentAssistantReply.clear()

        webSocket = GeminiLiveApi.connect(
            apiKey = BuildConfig.GEMINI_API_KEY,
            onMessage = { msg ->
                // Handle Setup Complete
                if (msg.setupComplete != null) {
                    _speechText.value = "متصل، يمكنك التحدث الآن بحرية!"
                }

                // Handle Audio and Text Response
                msg.serverContent?.modelTurn?.parts?.forEach { part ->
                    // Decode and play Live Audio Stream
                    part.inlineData?.let { inlineData ->
                        if (inlineData.mimeType.startsWith("audio/pcm")) {
                            val pcmData = Base64.decode(inlineData.data, Base64.DEFAULT)
                            audioController?.playAudio(pcmData)
                        }
                    }
                    
                    // Decode Live Text Stream
                    part.text?.let { text ->
                        currentAssistantReply.append(text)
                        _speechText.value = currentAssistantReply.toString()
                    }
                }
                
                // End of turn
                if (msg.serverContent?.turnComplete == true) {
                    val finalReply = currentAssistantReply.toString().trim()
                    if (finalReply.isNotEmpty()) {
                        viewModelScope.launch {
                            repository.insertMessage(finalReply, isUser = false)
                        }
                        currentAssistantReply.clear()
                    }
                }
            },
            onClose = {
                _isContinuousVoiceMode.value = false
                _speechText.value = ""
                audioController?.release()
            },
            onError = { t ->
                _errorMessage.value = "انقطع الاتصال: ${t.localizedMessage}"
                _isContinuousVoiceMode.value = false
                audioController?.release()
            }
        )

        // Start reading Microphone data and sending it to the WebSocket
        audioController?.startRecording { pcmData ->
            webSocket?.let { ws ->
                GeminiLiveApi.sendAudio(ws, pcmData)
            }
        }
    }

    private fun stopLiveSession() {
        _isContinuousVoiceMode.value = false
        _speechText.value = ""
        webSocket?.close(1000, "User ended session")
        webSocket = null
        audioController?.release()
        audioController = null
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveSession()
    }
}
