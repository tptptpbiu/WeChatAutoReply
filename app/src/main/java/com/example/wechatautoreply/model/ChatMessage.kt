package com.example.wechatautoreply.model

import kotlinx.serialization.Serializable

/**
 * 聊天消息
 */
@Serializable
data class ChatMessage(
    val role: String,       // "user" 或 "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
