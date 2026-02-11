package com.example.wechatautoreply.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wechatautoreply.ai.ModelInfo
import com.example.wechatautoreply.ai.ModelManager
import com.example.wechatautoreply.data.SettingsManager
import com.example.wechatautoreply.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var modelManager: ModelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "设置"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager(this)
        modelManager = ModelManager(this)
        
        loadSettings()
        setupModelCards()
        setupListeners()
        observeDownloadProgress()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadSettings() {
        binding.apply {
            // 推理线程数
            etThreads.setText(settingsManager.inferenceThreads.toString())
            
            // 回复延迟
            etMinDelay.setText(settingsManager.minDelay.toString())
            etMaxDelay.setText(settingsManager.maxDelay.toString())
            
            // 频率限制
            etMaxDaily.setText(settingsManager.maxDailyReplies.toString())
            etMaxPerMinute.setText(settingsManager.maxPerMinute.toString())
            
            // 工作时间
            etWorkStart.setText(settingsManager.workHourStart.toString())
            etWorkEnd.setText(settingsManager.workHourEnd.toString())
            
            // 敏感词
            etSensitiveWords.setText(settingsManager.sensitiveWords)
        }
    }

    /**
     * 设置模型选择卡片
     */
    private fun setupModelCards() {
        val selectedId = settingsManager.selectedModelId
        
        // Qwen 1.5B（推荐）
        val model1 = ModelManager.AVAILABLE_MODELS[0]
        setupSingleModelCard(
            model1, selectedId,
            binding.tvModel1Name, binding.tvModel1Desc, binding.tvModel1Status,
            binding.btnModel1Action, binding.cardModel1
        )
        
        // Qwen 0.5B（轻量）
        val model2 = ModelManager.AVAILABLE_MODELS[1]
        setupSingleModelCard(
            model2, selectedId,
            binding.tvModel2Name, binding.tvModel2Desc, binding.tvModel2Status,
            binding.btnModel2Action, binding.cardModel2
        )
        
        // Qwen 3B（高质量）
        val model3 = ModelManager.AVAILABLE_MODELS[2]
        setupSingleModelCard(
            model3, selectedId,
            binding.tvModel3Name, binding.tvModel3Desc, binding.tvModel3Status,
            binding.btnModel3Action, binding.cardModel3
        )
    }

    private fun setupSingleModelCard(
        model: ModelInfo,
        selectedId: String,
        tvName: android.widget.TextView,
        tvDesc: android.widget.TextView,
        tvStatus: android.widget.TextView,
        btnAction: android.widget.Button,
        card: View
    ) {
        tvName.text = model.name
        tvDesc.text = model.description
        
        val isDownloaded = modelManager.isModelDownloaded(model)
        val isSelected = model.id == selectedId
        
        when {
            isDownloaded && isSelected -> {
                tvStatus.text = "✅ 当前使用中"
                btnAction.text = "已选择"
                btnAction.isEnabled = false
            }
            isDownloaded -> {
                tvStatus.text = "📦 已下载"
                btnAction.text = "使用此模型"
                btnAction.isEnabled = true
                btnAction.setOnClickListener {
                    settingsManager.selectedModelId = model.id
                    Toast.makeText(this, "已切换到 ${model.name}", Toast.LENGTH_SHORT).show()
                    setupModelCards() // 刷新
                }
            }
            else -> {
                tvStatus.text = "⬇️ 未下载 (${modelManager.formatSize(model.sizeBytes)})"
                btnAction.text = "下载"
                btnAction.isEnabled = true
                btnAction.setOnClickListener {
                    downloadModel(model)
                }
            }
        }
        
        // 长按删除
        card.setOnLongClickListener {
            if (isDownloaded) {
                AlertDialog.Builder(this)
                    .setTitle("删除模型")
                    .setMessage("确定删除 ${model.name}？\n将释放 ${modelManager.formatSize(model.sizeBytes)} 存储空间")
                    .setPositiveButton("删除") { _, _ ->
                        modelManager.deleteModel(model)
                        if (model.id == settingsManager.selectedModelId) {
                            settingsManager.selectedModelId = ""
                        }
                        setupModelCards()
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            true
        }
    }

    /**
     * 下载模型
     */
    private fun downloadModel(model: ModelInfo) {
        binding.downloadProgressLayout.visibility = View.VISIBLE
        binding.tvDownloadName.text = "正在下载: ${model.name}"
        binding.progressBar.progress = 0
        binding.tvDownloadPercent.text = "0%"
        
        lifecycleScope.launch {
            val success = modelManager.downloadModel(model)
            binding.downloadProgressLayout.visibility = View.GONE
            
            if (success) {
                // 自动选中刚下载的模型
                settingsManager.selectedModelId = model.id
                setupModelCards()
                Toast.makeText(this@SettingsActivity, "${model.name} 下载完成！", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SettingsActivity, "下载失败，请检查网络后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 监听下载进度
     */
    private fun observeDownloadProgress() {
        lifecycleScope.launch {
            modelManager.downloadProgress.collectLatest { progress ->
                if (progress in 0..100) {
                    binding.progressBar.progress = progress
                    binding.tvDownloadPercent.text = "$progress%"
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnReset.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun saveSettings() {
        try {
            binding.apply {
                // 推理线程数
                settingsManager.inferenceThreads = etThreads.text.toString().toIntOrNull() 
                    ?: SettingsManager.DEFAULT_THREADS
                
                // 回复延迟
                val minDelay = etMinDelay.text.toString().toIntOrNull() ?: SettingsManager.DEFAULT_MIN_DELAY
                val maxDelay = etMaxDelay.text.toString().toIntOrNull() ?: SettingsManager.DEFAULT_MAX_DELAY
                
                if (minDelay > maxDelay) {
                    Toast.makeText(this@SettingsActivity, "最小延迟不能大于最大延迟", Toast.LENGTH_SHORT).show()
                    return
                }
                settingsManager.minDelay = minDelay
                settingsManager.maxDelay = maxDelay
                
                // 频率限制
                settingsManager.maxDailyReplies = etMaxDaily.text.toString().toIntOrNull() 
                    ?: SettingsManager.DEFAULT_MAX_DAILY
                settingsManager.maxPerMinute = etMaxPerMinute.text.toString().toIntOrNull() 
                    ?: SettingsManager.DEFAULT_MAX_PER_MIN
                
                // 工作时间
                val workStart = etWorkStart.text.toString().toIntOrNull() ?: SettingsManager.DEFAULT_WORK_START
                val workEnd = etWorkEnd.text.toString().toIntOrNull() ?: SettingsManager.DEFAULT_WORK_END
                
                if (workStart < 0 || workStart > 23 || workEnd < 0 || workEnd > 23) {
                    Toast.makeText(this@SettingsActivity, "工作时间必须在 0-23 之间", Toast.LENGTH_SHORT).show()
                    return
                }
                settingsManager.workHourStart = workStart
                settingsManager.workHourEnd = workEnd
                
                // 敏感词
                settingsManager.sensitiveWords = etSensitiveWords.text.toString().trim()
            }
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetToDefaults() {
        binding.apply {
            etThreads.setText(SettingsManager.DEFAULT_THREADS.toString())
            etMinDelay.setText(SettingsManager.DEFAULT_MIN_DELAY.toString())
            etMaxDelay.setText(SettingsManager.DEFAULT_MAX_DELAY.toString())
            etMaxDaily.setText(SettingsManager.DEFAULT_MAX_DAILY.toString())
            etMaxPerMinute.setText(SettingsManager.DEFAULT_MAX_PER_MIN.toString())
            etWorkStart.setText(SettingsManager.DEFAULT_WORK_START.toString())
            etWorkEnd.setText(SettingsManager.DEFAULT_WORK_END.toString())
            etSensitiveWords.setText(SettingsManager.DEFAULT_SENSITIVE_WORDS)
        }
        Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show()
    }
}
