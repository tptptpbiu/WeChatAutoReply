package com.example.wechatautoreply.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启动接收器
 * 系统启动后自动恢复通知监听服务
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("WeChatAutoReply", "设备已启动，通知监听服务将自动恢复")
            // NotificationListenerService 会在权限授予后由系统自动恢复
            // 这里不需要手动启动
        }
    }
}
