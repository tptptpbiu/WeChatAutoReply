package com.example.wechatautoreply.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 设置管理器
 * 管理所有配置项
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = 
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        // 总开关
        const val KEY_ENABLED = "enabled"
        
        // AI 模型设置
        const val KEY_SELECTED_MODEL = "selected_model"
        const val KEY_INFERENCE_THREADS = "inference_threads"
        
        // 回复设置
        const val KEY_MIN_DELAY = "min_delay"           // 最小回复延迟（秒）
        const val KEY_MAX_DELAY = "max_delay"           // 最大回复延迟（秒）
        const val KEY_MAX_DAILY_REPLIES = "max_daily_replies"  // 每日最大回复数
        const val KEY_MAX_PER_MINUTE = "max_per_minute"  // 每分钟最大回复数
        
        // 工作时间
        const val KEY_WORK_HOUR_START = "work_hour_start"
        const val KEY_WORK_HOUR_END = "work_hour_end"
        
        // 敏感词
        const val KEY_SENSITIVE_WORDS = "sensitive_words"
        
        // 默认值
        const val DEFAULT_MODEL_ID = "qwen2.5-1.5b-q4"
        const val DEFAULT_THREADS = 4
        const val DEFAULT_MIN_DELAY = 2
        const val DEFAULT_MAX_DELAY = 6
        const val DEFAULT_MAX_DAILY = 100
        const val DEFAULT_MAX_PER_MIN = 3
        const val DEFAULT_WORK_START = 8
        const val DEFAULT_WORK_END = 23
        const val DEFAULT_SENSITIVE_WORDS = "转账,密码,银行卡,红包,验证码,付款,支付"
    }

    // ========== 总开关 ==========
    
    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    // ========== AI 模型设置 ==========

    var selectedModelId: String
        get() = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()

    var inferenceThreads: Int
        get() = prefs.getInt(KEY_INFERENCE_THREADS, DEFAULT_THREADS)
        set(value) = prefs.edit().putInt(KEY_INFERENCE_THREADS, value).apply()

    // ========== 回复设置 ==========

    var minDelay: Int
        get() = prefs.getInt(KEY_MIN_DELAY, DEFAULT_MIN_DELAY)
        set(value) = prefs.edit().putInt(KEY_MIN_DELAY, value).apply()

    var maxDelay: Int
        get() = prefs.getInt(KEY_MAX_DELAY, DEFAULT_MAX_DELAY)
        set(value) = prefs.edit().putInt(KEY_MAX_DELAY, value).apply()

    var maxDailyReplies: Int
        get() = prefs.getInt(KEY_MAX_DAILY_REPLIES, DEFAULT_MAX_DAILY)
        set(value) = prefs.edit().putInt(KEY_MAX_DAILY_REPLIES, value).apply()

    var maxPerMinute: Int
        get() = prefs.getInt(KEY_MAX_PER_MINUTE, DEFAULT_MAX_PER_MIN)
        set(value) = prefs.edit().putInt(KEY_MAX_PER_MINUTE, value).apply()

    // ========== 工作时间 ==========

    var workHourStart: Int
        get() = prefs.getInt(KEY_WORK_HOUR_START, DEFAULT_WORK_START)
        set(value) = prefs.edit().putInt(KEY_WORK_HOUR_START, value).apply()

    var workHourEnd: Int
        get() = prefs.getInt(KEY_WORK_HOUR_END, DEFAULT_WORK_END)
        set(value) = prefs.edit().putInt(KEY_WORK_HOUR_END, value).apply()

    /**
     * 当前是否在工作时间
     */
    fun isWithinWorkHours(): Boolean {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return hour in workHourStart..workHourEnd
    }

    // ========== 敏感词 ==========

    var sensitiveWords: String
        get() = prefs.getString(KEY_SENSITIVE_WORDS, DEFAULT_SENSITIVE_WORDS) 
            ?: DEFAULT_SENSITIVE_WORDS
        set(value) = prefs.edit().putString(KEY_SENSITIVE_WORDS, value).apply()

    /**
     * 获取敏感词列表
     */
    fun getSensitiveWordList(): List<String> {
        return sensitiveWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 检查消息是否包含敏感词
     */
    fun containsSensitiveWord(message: String): Boolean {
        return getSensitiveWordList().any { message.contains(it) }
    }
}
