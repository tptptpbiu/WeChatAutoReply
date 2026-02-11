package com.example.wechatautoreply.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatautoreply.R
import com.example.wechatautoreply.data.ReplyLogManager
import com.example.wechatautoreply.databinding.ActivityLogBinding
import com.example.wechatautoreply.databinding.ItemLogBinding
import com.example.wechatautoreply.model.ReplyLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private lateinit var replyLogManager: ReplyLogManager
    private lateinit var adapter: LogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "回复日志"
            setDisplayHomeAsUpEnabled(true)
        }

        replyLogManager = ReplyLogManager(this)
        setupRecyclerView()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "清除所有日志")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            AlertDialog.Builder(this)
                .setTitle("清除所有日志")
                .setMessage("确定要清除所有回复日志吗？")
                .setPositiveButton("清除") { _, _ ->
                    replyLogManager.clearLogs()
                    refreshList()
                    Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        val logs = replyLogManager.getLogs()
        adapter = LogAdapter(logs.toMutableList())
        binding.recyclerLogs.layoutManager = LinearLayoutManager(this)
        binding.recyclerLogs.adapter = adapter
        
        binding.tvLogCount.text = "共 ${logs.size} 条记录"
        binding.tvEmptyHint.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshList() {
        val logs = replyLogManager.getLogs()
        adapter.updateList(logs)
        binding.tvLogCount.text = "共 ${logs.size} 条记录"
        binding.tvEmptyHint.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
    }

    // ========== RecyclerView Adapter ==========

    class LogAdapter(
        private var logs: MutableList<ReplyLog>
    ) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

        class ViewHolder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLogBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            holder.binding.apply {
                tvContactName.text = log.contactName
                tvTime.text = dateFormat.format(Date(log.timestamp))
                tvReceived.text = "收到: ${log.receivedMessage}"
                tvReplied.text = "回复: ${log.repliedMessage}"
                tvStatusIcon.text = if (log.success) "✅" else "❌"
            }
        }

        override fun getItemCount() = logs.size

        fun updateList(newLogs: List<ReplyLog>) {
            logs.clear()
            logs.addAll(newLogs)
            notifyDataSetChanged()
        }
    }
}
