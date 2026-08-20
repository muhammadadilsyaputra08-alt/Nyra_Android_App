package com.tdpl.chat.data

import android.content.Context
import com.tdpl.chat.model.ChatSession
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Chat history persistence — <filesDir>/sessions.json. Purely local: no
 * network, no cloud sync. Same lifecycle as the model file itself (wiped on
 * uninstall, untouched by app updates).
 */
class SessionRepository(context: Context) {

    private val file = File(context.filesDir, "sessions.json")
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<ChatSession> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ChatSession.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    fun saveAll(sessions: List<ChatSession>) {
        runCatching {
            file.writeText(json.encodeToString(ListSerializer(ChatSession.serializer()), sessions))
        }
    }
}
