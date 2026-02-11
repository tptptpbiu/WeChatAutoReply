package com.example.wechatautoreply.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.wechatautoreply.R
import com.example.wechatautoreply.ai.LlamaClient
import com.example.wechatautoreply.ai.ModelManager
import com.example.wechatautoreply.data.ContactManager
import com.example.wechatautoreply.data.ReplyLogManager
import com.example.wechatautoreply.data.SettingsManager
import com.example.wechatautoreply.databinding.ActivityMainBinding
import com.example.wechatautoreply.service.LlamaService
import com.example.wechatautoreply.service.WeChatNotificationService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsManager: SettingsManager
    private lateinit var contactManager: ContactManager
    private lateinit var replyLogManager: ReplyLogManager
    private lateinit var modelManager: ModelManager
    private lateinit var llamaClient: LlamaClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)
        contactManager = ContactManager(this)
        replyLogManager = ReplyLogManager(this)
        modelManager = ModelManager(this)
        llamaClient = LlamaClient.getInstance(this)

        setupUI()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupUI() {
        binding.switchMaster.isChecked = settingsManager.isEnabled
        updateStatus()
    }

    private fun setupListeners() {
        // 总开关
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 检查模型是否已下载
                val modelId = settingsManager.selectedModelId
                val modelPath = modelManager.getModelPathById(modelId)
                if (modelPath == null) {
                    binding.switchMaster.isChecked = false
                    Toast.makeText(this, "请先下载 AI 模型", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, SettingsActivity::class.java))
                    return@setOnCheckedChangeListener
                }
                
                // 检查通知权限
                if (!isNotificationListenerEnabled()) {
                    binding.switchMaster.isChecked = false
                    checkAndRequestPermissions()
                    return@setOnCheckedChangeListener
                }
                
                settingsManager.isEnabled = true
                startKeepAliveService()
                
                // 初始化 AI 引擎
                lifecycleScope.launch {
                    binding.tvStatusDesc.text = "正在加载 AI 模型..."
                    val success = llamaClient.initialize()
                    if (success) {
                        Toast.makeText(this@MainActivity, "AI 模型加载成功！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "AI 模型加载失败", Toast.LENGTH_SHORT).show()
                    }
                    updateStatus()
                }
            } else {
                settingsManager.isEnabled = false
                stopKeepAliveService()
            }
            updateStatus()
        }

        // 联系人管理
        binding.cardContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
        }

        // 设置（包含模型下载）
        binding.cardSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 回复日志
        binding.cardLogs.setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        // 通知权限
        binding.cardPermission.setOnClickListener {
            openNotificationListenerSettings()
        }

        // AI 模型状态
        binding.cardModelStatus.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * 更新界面状态
     */
    private fun updateStatus() {
        val isEnabled = settingsManager.isEnabled
        val isServiceRunning = WeChatNotificationService.isRunning
        val hasPermission = isNotificationListenerEnabled()
        val contactCount = contactManager.getEnabledContacts().size
        val todayReplies = replyLogManager.getTodayReplyCount()
        val isModelReady = llamaClient.isReady()
        val hasModel = modelManager.getModelPathById(settingsManager.selectedModelId) != null

        // 状态卡片
        when {
            isEnabled && isServiceRunning && hasPermission && isModelReady -> {
                binding.tvStatus.text = "🟢 运行中"
                binding.tvStatusDesc.text = "自动回复服务正常运行"
                binding.cardStatus.setCardBackgroundColor(getColor(R.color.status_running))
            }
            isEnabled && !hasPermission -> {
                binding.tvStatus.text = "🟡 需要授权"
                binding.tvStatusDesc.text = "请开启通知访问权限"
                binding.cardStatus.setCardBackgroundColor(getColor(R.color.status_warning))
            }
            isEnabled && !hasModel -> {
                binding.tvStatus.text = "🟡 需要模型"
                binding.tvStatusDesc.text = "请先下载 AI 模型"
                binding.cardStatus.setCardBackgroundColor(getColor(R.color.status_warning))
            }
            isEnabled && !isModelReady -> {
                binding.tvStatus.text = "🟡 模型加载中"
                binding.tvStatusDesc.text = "AI 模型正在加载..."
                binding.cardStatus.setCardBackgroundColor(getColor(R.color.status_warning))
            }
            else -> {
                binding.tvStatus.text = "⚫ 已关闭"
                binding.tvStatusDesc.text = "自动回复已暂停"
                binding.cardStatus.setCardBackgroundColor(getColor(R.color.status_stopped))
            }
        }

        // 统计信息
        binding.tvContactCount.text = "$contactCount 人"
        binding.tvTodayReplies.text = "$todayReplies 条"
        binding.tvDailyLimit.text = "${settingsManager.maxDailyReplies} 条"

        // 权限状态
        binding.tvPermissionStatus.text = if (hasPermission) "✅ 已授权" else "❌ 未授权"

        // AI 模型状态
        val selectedModel = ModelManager.AVAILABLE_MODELS.find { it.id == settingsManager.selectedModelId }
        binding.tvModelStatus.text = when {
            isModelReady -> "✅ ${selectedModel?.name ?: "已加载"}"
            hasModel -> "📦 已下载，未加载"
            else -> "❌ 未下载，点击去设置"
        }

        // 最近回复
        val recentLogs = replyLogManager.getLogs().take(3)
        if (recentLogs.isEmpty()) {
            binding.tvRecentReplies.text = "暂无回复记录"
        } else {
            binding.tvRecentReplies.text = recentLogs.joinToString("\n\n") { log ->
                "${log.contactName}: ${log.receivedMessage}\n→ ${log.repliedMessage}"
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(ComponentName(this, WeChatNotificationService::class.java).flattenToString()) == true
    }

    private fun checkAndRequestPermissions() {
        AlertDialog.Builder(this)
            .setTitle("需要通知访问权限")
            .setMessage("为了能够读取微信消息并自动回复，需要开启通知访问权限。\n\n请在设置中找到「微信智能回复」并开启。")
            .setPositiveButton("去设置") { _, _ ->
                openNotificationListenerSettings()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun startKeepAliveService() {
        val intent = Intent(this, LlamaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopKeepAliveService() {
        stopService(Intent(this, LlamaService::class.java))
    }
}
