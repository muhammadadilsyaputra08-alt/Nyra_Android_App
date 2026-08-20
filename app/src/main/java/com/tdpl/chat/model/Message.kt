package com.tdpl.chat.model

data class Message(
    val id: Long = System.nanoTime(),
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

enum class Role { USER, ASSISTANT, SYSTEM }
