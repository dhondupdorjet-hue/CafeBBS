package com.bettafish.flarent.data

import com.bettafish.flarent.models.AiChatRequest
import com.bettafish.flarent.models.AiChatResponse

interface AiRepository {
    suspend fun sendMessage(
        message: String,
        history: List<com.bettafish.flarent.models.ChatHistoryItem>,
    ): Result<AiChatResponse>
}
