package com.bettafish.flarent.data

import com.bettafish.flarent.models.AiChatRequest
import com.bettafish.flarent.models.AiChatResponse
import com.bettafish.flarent.network.FlarumService

class AiRepositoryImpl(
    private val service: FlarumService,
) : AiRepository {

    override suspend fun sendMessage(
        message: String,
        history: List<com.bettafish.flarent.models.ChatHistoryItem>,
    ): Result<AiChatResponse> = runCatching {
        service.aiChat(AiChatRequest(message, history))
    }
}
