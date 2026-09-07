package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class IsmctsOnlyProvider : AiProvider {
    override val id = "ismcts_only"
    override val displayName = "ISMCTS (offline)"

    override suspend fun isAvailable() = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> {
        val userMessage = messages.lastOrNull { it.role == AiMessage.Role.USER }?.content ?: return Result.Success("")
        val action =
            runCatching {
                val json = Json.parseToJsonElement(userMessage).jsonObject
                json["candidates"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("action")
                    ?.jsonPrimitive
                    ?.content ?: ""
            }.getOrElse { "" }
        return Result.Success(action)
    }
}
