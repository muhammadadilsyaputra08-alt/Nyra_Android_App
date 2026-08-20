package com.tdpl.chat.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: Long = System.nanoTime(),
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

@Serializable
enum class Role { USER, ASSISTANT, SYSTEM }
