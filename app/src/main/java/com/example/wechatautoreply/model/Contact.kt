package com.example.wechatautoreply.model

import kotlinx.serialization.Serializable

/**
 * 自动回复联系人
 */
@Serializable
data class Contact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,                    // 联系人名称（用于匹配微信通知）
    var enabled: Boolean = true,         // 是否启用自动回复
    var style: String = "朋友之间随意聊天，回复简短自然", // 回复风格
    var createdAt: Long = System.currentTimeMillis()
)
