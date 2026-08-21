package com.tdpl.chat.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val characterId: String? = null,
    val title: String = "Percakapan baru",
    val pinned: Boolean = false,
    val messages: List<Message> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
