package com.example.wechatautoreply.ai

import android.util.Log
import com.example.wechatautoreply.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 本地 LLM 推理引擎
 * 
 * 通过 JNI 调用 llama.cpp，在手机本地运行 AI 模型
 * 无需网络，完全离线推理
 */
class LlamaEngine {

    companion object {
        private const val TAG = "LlamaEngine"
        
        // 默认参数
        const val DEFAULT_THREADS = 4
        const val DEFAULT_CTX_SIZE = 2048
        const val DEFAULT_MAX_TOKENS = 256
        const val DEFAULT_TEMPERATURE = 0.7f

        init {
            System.loadLibrary("llama-jni")
        }
    }

    // 保证同一时间只有一个推理任务
    private val mutex = Mutex()
    
    // ========== Native 方法 ==========
    
    private external fun nativeInit()
    private external fun nativeLoadModel(modelPath: String, nThreads: Int, nCtx: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeIsLoaded(): Boolean
    private external fun nativeFree()

    // ========== 公开接口 ==========

    /**
     * 初始化引擎
     */
    fun init() {
        nativeInit()
        Log.i(TAG, "引擎已初始化")
    }

    /**
     * 加载模型
     * @param modelPath GGUF 模型文件的绝对路径
     * @param nThreads 推理线程数（默认 4，可根据手机 CPU 核数调整）
     * @param nCtx 上下文长度（默认 2048）
     */
    suspend fun loadModel(
        modelPath: String,
        nThreads: Int = DEFAULT_THREADS,
        nCtx: Int = DEFAULT_CTX_SIZE
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            Log.i(TAG, "开始加载模型: $modelPath")
            val success = nativeLoadModel(modelPath, nThreads, nCtx)
            if (success) {
                Log.i(TAG, "模型加载成功")
            } else {
                Log.e(TAG, "模型加载失败")
            }
            return@withContext success
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean = nativeIsLoaded()

    /**
     * 生成智能回复
     * 
     * @param contactName 联系人名称
     * @param style 回复风格描述
     * @param chatHistory 聊天历史
     * @param newMessage 新收到的消息
     * @return AI 生成的回复文本
     */
    suspend fun generateReply(
        contactName: String,
        style: String,
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!nativeIsLoaded()) {
                Log.e(TAG, "模型未加载，返回默认回复")
                return@withContext "在的"
            }

            // 构建 ChatML 格式的 prompt
            val prompt = buildPrompt(contactName, style, chatHistory, newMessage)
            Log.d(TAG, "Prompt 长度: ${prompt.length}")

            return@withContext try {
                val result = nativeGenerate(prompt, DEFAULT_MAX_TOKENS, DEFAULT_TEMPERATURE)
                val cleaned = cleanResponse(result)
                if (cleaned.isBlank()) "在的" else cleaned
            } catch (e: Exception) {
                Log.e(TAG, "生成回复失败: ${e.message}", e)
                "在的"
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        nativeFree()
        Log.i(TAG, "引擎资源已释放")
    }

    // ========== 内部方法 ==========

    /**
     * 构建 ChatML 格式的 Prompt
     * Qwen 模型使用 ChatML 格式
     */
    private fun buildPrompt(
        contactName: String,
        style: String,
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String {
        val sb = StringBuilder()

        // System prompt
        sb.append("<|im_start|>system\n")
        sb.append("你现在是用户本人，正在用微信和\"${contactName}\"聊天。\n")
        sb.append("回复风格要求：$style\n")
        sb.append("重要规则：\n")
        sb.append("1. 只输出回复内容，不要加引号、不要解释\n")
        sb.append("2. 回复要简短自然，像真人发微信一样\n")
        sb.append("3. 可以用语气词（嗯、哈哈、好的、行）\n")
        sb.append("4. 不要用过于正式的语言\n")
        sb.append("5. 回复长度控制在 1-3 句话\n")
        sb.append("<|im_end|>\n")

        // 历史对话
        for (msg in chatHistory) {
            when (msg.role) {
                "user" -> {
                    sb.append("<|im_start|>user\n")
                    sb.append(msg.content)
                    sb.append("<|im_end|>\n")
                }
                "assistant" -> {
                    sb.append("<|im_start|>assistant\n")
                    sb.append(msg.content)
                    sb.append("<|im_end|>\n")
                }
            }
        }

        // 新消息
        sb.append("<|im_start|>user\n")
        sb.append(newMessage)
        sb.append("<|im_end|>\n")

        // 等待 AI 回复
        sb.append("<|im_start|>assistant\n")

        return sb.toString()
    }

    /**
     * 清理 AI 输出
     * 去掉多余的空白、标记等
     */
    private fun cleanResponse(response: String): String {
        var cleaned = response.trim()

        // 去掉可能残留的特殊标记
        cleaned = cleaned.replace("<|im_end|>", "")
        cleaned = cleaned.replace("<|im_start|>", "")
        cleaned = cleaned.replace("assistant", "")

        // 去掉开头结尾的引号
        cleaned = cleaned.trim()
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }

        // 限制长度（微信消息不需要太长）
        if (cleaned.length > 200) {
            val lastPunctuation = cleaned.take(200).lastIndexOfAny(charArrayOf('。', '！', '？', '~', '.', '!', '?'))
            cleaned = if (lastPunctuation > 0) {
                cleaned.substring(0, lastPunctuation + 1)
            } else {
                cleaned.take(200)
            }
        }

        return cleaned.trim()
    }
}
