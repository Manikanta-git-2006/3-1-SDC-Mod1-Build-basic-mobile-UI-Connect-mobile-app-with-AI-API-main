package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.Content
import com.example.data.GeminiRequest
import com.example.data.Part
import com.example.data.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Error(val message: String) : UiState
}

class AssistantViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        // 1. Add user message
        val userMsg = ChatMessage(text = trimmed, isUser = true)
        val currentList = _messages.value + userMsg
        _messages.value = currentList

        // 2. Query Gemini
        _uiState.value = UiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.value = UiState.Error("API Key is missing or invalid. Please configure your key in the Secrets panel.")
                    return@launch
                }

                // Convert chat history to Gemini request format
                val contents = currentList.map { msg ->
                    Content(
                        role = if (msg.isUser) "user" else "model",
                        parts = listOf(Part(text = msg.text))
                    )
                }

                val systemInstruction = Content(
                    parts = listOf(Part(text = "You are a friendly, helpful, and highly intelligent AI Assistant. Keep your answers clear, elegant, and perfectly structured."))
                )

                val request = GeminiRequest(
                    contents = contents,
                    systemInstruction = systemInstruction
                )

                val response = RetrofitClient.service.generateContent(apiKey, request)
                val aiText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (aiText != null) {
                    _messages.value = _messages.value + ChatMessage(text = aiText, isUser = false)
                    _uiState.value = UiState.Idle
                } else {
                    _uiState.value = UiState.Error("Received empty response from Gemini API.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.Error(e.localizedMessage ?: "Network error. Please try again.")
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _uiState.value = UiState.Idle
    }
}
