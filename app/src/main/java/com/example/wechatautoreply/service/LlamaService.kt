package com.example.wechatautoreply.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.wechatautoreply.App
import com.example.wechatautoreply.R
import com.example.wechatautoreply.ui.MainActivity

/**
 * 前台服务
 * 用于保活通知监听，防止被系统杀掉
 */
class LlamaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setContentTitle("微信智能回复助手")
            .setContentText("自动回复服务运行中...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
        return START_STICKY
    }
}
