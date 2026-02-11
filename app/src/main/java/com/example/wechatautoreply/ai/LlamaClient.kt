package com.example.wechatautoreply.ai

import android.content.Context
import android.util.Log
import com.example.wechatautoreply.data.SettingsManager
import com.example.wechatautoreply.model.ChatMessage

/**
 * AI 客户端 - 统一对外接口
 * 
 * 内部使用 LlamaEngine（本地推理引擎）
 * 模型文件由 ModelManager 管理
 */
class LlamaClient(private val context: Context) {

    companion object {
        private const val TAG = "LlamaClient"
        
        // 单例
        @Volatile
        private var instance: LlamaClient? = null
        
        fun getInstance(context: Context): LlamaClient {
            return instance ?: synchronized(this) {
                instance ?: LlamaClient(context.applicationContext).also { instance = it }
            }
        }
    }

    private val engine = LlamaEngine()
    private val modelManager = ModelManager(context)
    private val settingsManager = SettingsManager(context)
    
    private var isInitialized = false

    /**
     * 初始化引擎并加载模型
     */
    suspend fun initialize(): Boolean {
        if (!isInitialized) {
            engine.init()
            isInitialized = true
        }
        
        // 如果模型已经加载了，不需要重复加载
        if (engine.isModelLoaded()) {
            return true
        }
        
        // 获取选中的模型路径
        val modelId = settingsManager.selectedModelId
        val modelPath = modelManager.getModelPathById(modelId)
        
        if (modelPath == null) {
            Log.e(TAG, "模型未下载: $modelId")
            return false
        }
        
        Log.i(TAG, "加载模型: $modelPath")
        return engine.loadModel(
            modelPath = modelPath,
            nThreads = settingsManager.inferenceThreads,
            nCtx = LlamaEngine.DEFAULT_CTX_SIZE
        )
    }

    /**
     * 检查是否准备就绪
     */
    fun isReady(): Boolean = engine.isModelLoaded()

    /**
     * 生成回复
     */
    suspend fun generateReply(
        contactName: String,
        style: String,
        chatHistory: List<ChatMessage>,
        newMessage: String
    ): String {
        // 确保已初始化
        if (!isReady()) {
            val success = initialize()
            if (!success) {
                Log.e(TAG, "AI 引擎未就绪，返回默认回复")
                return "在的"
            }
        }

        return engine.generateReply(
            contactName = contactName,
            style = style,
            chatHistory = chatHistory,
            newMessage = newMessage
        )
    }

    /**
     * 释放资源
     */
    fun release() {
        engine.release()
        isInitialized = false
    }
}
