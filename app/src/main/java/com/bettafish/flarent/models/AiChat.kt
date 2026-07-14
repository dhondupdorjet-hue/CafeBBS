package com.bettafish.flarent.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** AI 聊天请求 */
@Serializable
data class AiChatRequest(
    val message: String,
    val history: List<ChatHistoryItem> = emptyList(),
)

/** 对话历史中的一条 */
@Serializable
data class ChatHistoryItem(
    val role: String, // "user" | "assistant"
    val content: String,
)

/** AI 聊天响应 */
@Serializable
data class AiChatResponse(
    val reply: String,
    @SerialName("tokens_used")
    val tokensUsed: Int = 0,
    @SerialName("daily_used")
    val dailyUsed: Int = 0,
    @SerialName("daily_remaining")
    val dailyRemaining: Int = 0,
    @SerialName("daily_limit")
    val dailyLimit: Int = 0,
    val error: String? = null,
)
