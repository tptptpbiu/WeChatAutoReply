package com.example.wechatautoreply.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 模型下载和管理器
 * 
 * 负责从 Hugging Face 下载 GGUF 模型文件到手机本地
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "models"

        /**
         * 预置模型列表
         */
        val AVAILABLE_MODELS = listOf(
            ModelInfo(
                id = "qwen2.5-1.5b-q4",
                name = "Qwen2.5 1.5B（推荐）",
                description = "中文对话最佳，约 1GB",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                downloadUrl = "https://hf-mirror.com/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                sizeBytes = 1_100_000_000L
            ),
            ModelInfo(
                id = "qwen2.5-0.5b-q4",
                name = "Qwen2.5 0.5B（轻量）",
                description = "极致轻量，约 400MB，效果稍弱",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                downloadUrl = "https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                sizeBytes = 400_000_000L
            ),
            ModelInfo(
                id = "qwen2.5-3b-q4",
                name = "Qwen2.5 3B（高质量）",
                description = "效果最好，约 2GB，需要 6GB+ 内存",
                fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                downloadUrl = "https://hf-mirror.com/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_000_000_000L
            )
        )
    }

    private val modelsDir: File = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 下载进度（0-100）
    private val _downloadProgress = MutableStateFlow(-1)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    // 下载状态
    private val _downloadState = MutableStateFlow(DownloadState.IDLE)
    val downloadState: StateFlow<DownloadState> = _downloadState

    /**
     * 获取模型文件路径（如果已下载）
     */
    fun getModelPath(modelInfo: ModelInfo): String? {
        val file = File(modelsDir, modelInfo.fileName)
        return if (file.exists() && file.length() > 0) file.absolutePath else null
    }

    /**
     * 获取模型文件路径（通过 ID）
     */
    fun getModelPathById(modelId: String): String? {
        val modelInfo = AVAILABLE_MODELS.find { it.id == modelId } ?: return null
        return getModelPath(modelInfo)
    }

    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(modelInfo: ModelInfo): Boolean {
        val file = File(modelsDir, modelInfo.fileName)
        return file.exists() && file.length() > modelInfo.sizeBytes * 0.9 // 允许 10% 误差
    }

    /**
     * 获取已下载的模型列表
     */
    fun getDownloadedModels(): List<ModelInfo> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(it) }
    }

    /**
     * 下载模型
     */
    suspend fun downloadModel(modelInfo: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, modelInfo.fileName)
        val tempFile = File(modelsDir, "${modelInfo.fileName}.tmp")

        try {
            _downloadState.value = DownloadState.DOWNLOADING
            _downloadProgress.value = 0

            Log.i(TAG, "开始下载模型: ${modelInfo.name}")
            Log.i(TAG, "下载地址: ${modelInfo.downloadUrl}")
            Log.i(TAG, "目标路径: ${targetFile.absolutePath}")

            val request = Request.Builder()
                .url(modelInfo.downloadUrl)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "下载失败: HTTP ${response.code}")
                _downloadState.value = DownloadState.FAILED
                return@withContext false
            }

            val body = response.body ?: run {
                Log.e(TAG, "响应体为空")
                _downloadState.value = DownloadState.FAILED
                return@withContext false
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            Log.i(TAG, "文件大小: ${totalBytes / 1024 / 1024}MB")

            body.byteStream().use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // 更新进度
                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes).toInt()
                            _downloadProgress.value = progress
                        }
                    }
                }
            }

            // 下载完成，重命名
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            Log.i(TAG, "模型下载完成: ${targetFile.absolutePath}")
            _downloadState.value = DownloadState.COMPLETED
            _downloadProgress.value = 100
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "下载异常: ${e.message}", e)
            tempFile.delete()
            _downloadState.value = DownloadState.FAILED
            _downloadProgress.value = -1
            return@withContext false
        }
    }

    /**
     * 删除模型文件
     */
    fun deleteModel(modelInfo: ModelInfo): Boolean {
        val file = File(modelsDir, modelInfo.fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    /**
     * 获取已用存储空间
     */
    fun getUsedStorage(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0
    }

    /**
     * 格式化文件大小
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
            else -> "%.1f GB".format(bytes.toDouble() / 1024 / 1024 / 1024)
        }
    }

    enum class DownloadState {
        IDLE, DOWNLOADING, COMPLETED, FAILED
    }
}

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long
)
