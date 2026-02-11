package com.example.wechatautoreply.service

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.wechatautoreply.ai.LlamaClient
import com.example.wechatautoreply.data.ContactManager
import com.example.wechatautoreply.data.ReplyLogManager
import com.example.wechatautoreply.data.SettingsManager
import com.example.wechatautoreply.model.ChatMessage
import com.example.wechatautoreply.model.ReplyLog
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 微信通知监听服务
 * 
 * 核心工作流程：
 * 1. 监听系统通知
 * 2. 过滤微信消息通知
 * 3. 提取发送者和消息内容
 * 4. 匹配白名单联系人
 * 5. 调用本地 AI 生成回复（内嵌引擎，无需网络）
 * 6. 通过通知快捷回复发送
 */
class WeChatNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "WeChatAutoReply"
        private const val WECHAT_PACKAGE = "com.tencent.mm"
        
        // 服务状态回调
        var onStatusChanged: ((Boolean) -> Unit)? = null
        var isRunning = false
            private set
    }

    private lateinit var contactManager: ContactManager
    private lateinit var replyLogManager: ReplyLogManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var llamaClient: LlamaClient
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 频率限制：记录每个联系人最近的回复时间
    private val recentReplies = ConcurrentHashMap<String, MutableList<Long>>()
    
    // 今日回复计数
    private var dailyReplyCount = 0

    override fun onCreate() {
        super.onCreate()
        contactManager = ContactManager(this)
        replyLogManager = ReplyLogManager(this)
        settingsManager = SettingsManager(this)
        llamaClient = LlamaClient.getInstance(this)
        dailyReplyCount = replyLogManager.getTodayReplyCount()
        
        // 预加载模型
        scope.launch {
            try {
                llamaClient.initialize()
                Log.i(TAG, "AI 模型预加载完成")
            } catch (e: Exception) {
                Log.e(TAG, "AI 模型预加载失败: ${e.message}")
            }
        }
        
        isRunning = true
        onStatusChanged?.invoke(true)
        Log.i(TAG, "通知监听服务已启动")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        isRunning = false
        onStatusChanged?.invoke(false)
        Log.i(TAG, "通知监听服务已停止")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isRunning = true
        onStatusChanged?.invoke(true)
        Log.i(TAG, "通知监听器已连接")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isRunning = false
        onStatusChanged?.invoke(false)
        Log.i(TAG, "通知监听器已断开")
    }

    /**
     * 收到新通知时触发
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        
        // 1. 只处理微信通知
        if (sbn.packageName != WECHAT_PACKAGE) return
        
        // 2. 检查总开关
        if (!settingsManager.isEnabled) return
        
        // 3. 检查 AI 是否就绪
        if (!llamaClient.isReady()) {
            Log.d(TAG, "AI 模型未就绪，跳过")
            return
        }
        
        // 4. 检查是否在工作时间
        if (!settingsManager.isWithinWorkHours()) {
            Log.d(TAG, "当前不在工作时间，跳过")
            return
        }
        
        // 5. 检查今日回复上限
        if (dailyReplyCount >= settingsManager.maxDailyReplies) {
            Log.d(TAG, "今日回复已达上限: $dailyReplyCount")
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        
        // 6. 提取发送者和消息
        val sender = extractSender(extras) ?: return
        val message = extractMessage(extras) ?: return
        
        Log.d(TAG, "收到微信消息 - 发送者: $sender, 消息: $message")
        
        // 7. 匹配白名单联系人
        val contact = contactManager.findContactByName(sender)
        if (contact == null) {
            Log.d(TAG, "发送者 $sender 不在白名单中，跳过")
            return
        }
        
        // 8. 检查敏感词
        if (settingsManager.containsSensitiveWord(message)) {
            Log.w(TAG, "消息包含敏感词，跳过自动回复: $message")
            replyLogManager.addLog(ReplyLog(
                contactName = sender,
                receivedMessage = message,
                repliedMessage = "[包含敏感词，已跳过]",
                success = false
            ))
            return
        }
        
        // 9. 检查频率限制
        if (!checkRateLimit(sender)) {
            Log.d(TAG, "回复频率过高，跳过: $sender")
            return
        }
        
        // 10. 获取通知的回复 Action
        val replyAction = findReplyAction(notification)
        if (replyAction == null) {
            Log.w(TAG, "未找到回复 Action，无法自动回复")
            return
        }
        
        // 11. 异步处理：生成 AI 回复并发送
        scope.launch {
            try {
                processAutoReply(contact, sender, message, replyAction)
            } catch (e: Exception) {
                Log.e(TAG, "自动回复失败: ${e.message}", e)
                replyLogManager.addLog(ReplyLog(
                    contactName = sender,
                    receivedMessage = message,
                    repliedMessage = "[回复失败: ${e.message}]",
                    success = false
                ))
            }
        }
    }

    /**
     * 处理自动回复的完整流程
     */
    private suspend fun processAutoReply(
        contact: com.example.wechatautoreply.model.Contact,
        sender: String,
        message: String,
        replyAction: Notification.Action
    ) {
        // 获取聊天历史
        val chatHistory = contactManager.getChatHistory(contact.id)
        
        // 调用本地 AI 生成回复（完全离线！）
        Log.d(TAG, "正在生成 AI 回复...")
        val reply = llamaClient.generateReply(
            contactName = sender,
            style = contact.style,
            chatHistory = chatHistory,
            newMessage = message
        )
        Log.d(TAG, "AI 回复: $reply")
        
        // 随机延迟（模拟真人打字）
        val delayMs = (settingsManager.minDelay * 1000L..settingsManager.maxDelay * 1000L).random()
        Log.d(TAG, "延迟 ${delayMs}ms 后发送")
        delay(delayMs)
        
        // 发送回复
        sendReply(replyAction, reply)
        
        // 保存聊天历史
        contactManager.addChatMessage(contact.id, ChatMessage(role = "user", content = message))
        contactManager.addChatMessage(contact.id, ChatMessage(role = "assistant", content = reply))
        
        // 记录日志
        replyLogManager.addLog(ReplyLog(
            contactName = sender,
            receivedMessage = message,
            repliedMessage = reply,
            success = true
        ))
        
        // 更新计数
        dailyReplyCount++
        recordReply(sender)
        
        Log.i(TAG, "已自动回复 $sender: $reply")
    }

    /**
     * 从通知中提取发送者名称
     */
    private fun extractSender(extras: Bundle): String? {
        val title = extras.getCharSequence("android.title")?.toString()
        if (title.isNullOrBlank()) return null
        
        // 过滤群聊消息
        if (title.matches(Regex(".*\\(\\d+\\).*"))) {
            Log.d(TAG, "群聊消息，跳过: $title")
            return null
        }
        
        return title
    }

    /**
     * 从通知中提取消息内容
     */
    private fun extractMessage(extras: Bundle): String? {
        val text = extras.getCharSequence("android.text")?.toString()
        if (text.isNullOrBlank()) return null
        
        val nonTextPatterns = listOf(
            "[图片]", "[语音]", "[视频]", "[文件]", 
            "[位置]", "[链接]", "[名片]", "[音乐]",
            "[表情]", "[动画表情]", "[红包]", "[转账]"
        )
        if (nonTextPatterns.any { text.contains(it) }) {
            Log.d(TAG, "非文本消息，跳过: $text")
            return null
        }
        
        return text
    }

    /**
     * 查找通知中的回复 Action
     */
    private fun findReplyAction(notification: Notification): Notification.Action? {
        return notification.actions?.find { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
    }

    /**
     * 通过通知快捷回复发送消息
     */
    private fun sendReply(action: Notification.Action, replyText: String) {
        try {
            val remoteInputs = action.remoteInputs ?: return
            val intent = Intent()
            val bundle = Bundle()
            
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            action.actionIntent.send(this, 0, intent)
            
            Log.d(TAG, "回复发送成功: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "发送回复失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 检查频率限制
     */
    private fun checkRateLimit(sender: String): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000
        
        val replies = recentReplies.getOrPut(sender) { mutableListOf() }
        replies.removeAll { it < oneMinuteAgo }
        
        return replies.size < settingsManager.maxPerMinute
    }

    /**
     * 记录回复时间
     */
    private fun recordReply(sender: String) {
        val replies = recentReplies.getOrPut(sender) { mutableListOf() }
        replies.add(System.currentTimeMillis())
    }
}
