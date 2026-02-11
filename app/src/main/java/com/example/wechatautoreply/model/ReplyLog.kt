package com.example.wechatautoreply.model

import kotlinx.serialization.Serializable

/**
 * 回复日志
 */
@Serializable
data class ReplyLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val contactName: String,         // 联系人名称
    val receivedMessage: String,     // 收到的消息
    val repliedMessage: String,      // 回复的消息
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true      // 是否成功发送
)
