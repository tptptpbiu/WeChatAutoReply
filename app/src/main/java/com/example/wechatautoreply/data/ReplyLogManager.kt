package com.example.wechatautoreply.data

import android.content.Context
import com.example.wechatautoreply.model.ReplyLog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 回复日志管理器
 */
class ReplyLogManager(private val context: Context) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    private val logFile: File
        get() = File(context.filesDir, "reply_logs.json")

    /**
     * 获取所有日志
     */
    fun getLogs(): List<ReplyLog> {
        return try {
            if (logFile.exists()) {
                json.decodeFromString<List<ReplyLog>>(logFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 添加日志
     */
    fun addLog(log: ReplyLog) {
        val logs = getLogs().toMutableList()
        logs.add(0, log) // 最新的在前面
        // 只保留最近 200 条日志
        while (logs.size > 200) {
            logs.removeAt(logs.size - 1)
        }
        logFile.writeText(json.encodeToString(logs))
    }

    /**
     * 清除所有日志
     */
    fun clearLogs() {
        logFile.delete()
    }

    /**
     * 获取今日回复数量
     */
    fun getTodayReplyCount(): Int {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        return getLogs().count { it.timestamp >= todayStart && it.success }
    }
}
