package com.bettafish.flarent.ui.pages.aiChat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bettafish.flarent.data.AiRepository
import com.bettafish.flarent.models.ChatHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val role: String,       // "user" | "assistant"
    val content: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

data class AiChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val dailyRemaining: Int? = null,
    val dailyLimit: Int = 0,
    val errorMessage: String? = null,
)

class AiChatViewModel(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    /** 发送消息 */
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_uiState.value.isLoading) return

        // 1. 添加用户消息
        val userMsg = ChatMessage(role = "user", content = trimmed)
        val currentMessages = _uiState.value.messages + userMsg
        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            isLoading = true,
            errorMessage = null,
        )

        // 2. 构建历史上下文（最近20条）
        val history = currentMessages
            .takeLast(20)
            .filter { !it.isError }
            .map { ChatHistoryItem(it.role, it.content) }

        // 3. 发送请求
        viewModelScope.launch {
            aiRepository.sendMessage(trimmed, history)
                .onSuccess { response ->
                    if (response.error != null) {
                        // 服务端返回了业务错误（如额度用完）
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + ChatMessage(
                                role = "assistant",
                                content = response.error,
                                isError = true,
                            ),
                            isLoading = false,
                            dailyRemaining = response.dailyRemaining,
                            dailyLimit = response.dailyLimit,
                            errorMessage = null,
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + ChatMessage(
                                role = "assistant",
                                content = response.reply,
                            ),
                            isLoading = false,
                            dailyRemaining = response.dailyRemaining,
                            dailyLimit = response.dailyLimit,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + ChatMessage(
                            role = "assistant",
                            content = "抱歉，出了点问题：${e.message ?: "未知错误"}",
                            isError = true,
                        ),
                        isLoading = false,
                        errorMessage = null,
                    )
                }
        }
    }

    /** 清空对话 */
    fun clearChat() {
        _uiState.value = AiChatUiState()
    }
}
