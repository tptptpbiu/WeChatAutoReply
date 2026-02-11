package com.example.wechatautoreply.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wechatautoreply.data.ContactManager
import com.example.wechatautoreply.databinding.ActivityContactsBinding
import com.example.wechatautoreply.databinding.ItemContactBinding
import com.example.wechatautoreply.model.Contact

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var contactManager: ContactManager
    private lateinit var adapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "联系人管理"
            setDisplayHomeAsUpEnabled(true)
        }

        contactManager = ContactManager(this)
        
        setupRecyclerView()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        adapter = ContactAdapter(
            contacts = contactManager.getContacts().toMutableList(),
            onToggle = { contact ->
                contactManager.toggleContact(contact.id)
                refreshList()
            },
            onEdit = { contact ->
                showEditDialog(contact)
            },
            onDelete = { contact ->
                showDeleteDialog(contact)
            }
        )
        binding.recyclerContacts.layoutManager = LinearLayoutManager(this)
        binding.recyclerContacts.adapter = adapter
        
        updateEmptyView()
    }

    private fun setupListeners() {
        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }
    }

    /**
     * 显示添加联系人对话框
     */
    private fun showAddDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 0)
        }
        
        val nameInput = EditText(this).apply {
            hint = "联系人微信名称（如：cc）"
        }
        layout.addView(nameInput)
        
        val styleInput = EditText(this).apply {
            hint = "回复风格（如：朋友之间随意聊天）"
            setText("朋友之间随意聊天，回复简短自然")
            minLines = 2
        }
        layout.addView(styleInput)

        AlertDialog.Builder(this)
            .setTitle("添加自动回复联系人")
            .setView(layout)
            .setPositiveButton("添加") { _, _ ->
                val name = nameInput.text.toString().trim()
                val style = styleInput.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 检查是否已存在
                val existing = contactManager.getContacts().find { it.name == name }
                if (existing != null) {
                    Toast.makeText(this, "联系人已存在", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val contact = Contact(
                    name = name,
                    style = style.ifEmpty { "朋友之间随意聊天，回复简短自然" }
                )
                contactManager.addContact(contact)
                refreshList()
                Toast.makeText(this, "已添加：$name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示编辑联系人对话框
     */
    private fun showEditDialog(contact: Contact) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 0)
        }
        
        val nameInput = EditText(this).apply {
            hint = "联系人微信名称"
            setText(contact.name)
        }
        layout.addView(nameInput)
        
        val styleInput = EditText(this).apply {
            hint = "回复风格"
            setText(contact.style)
            minLines = 2
        }
        layout.addView(styleInput)

        AlertDialog.Builder(this)
            .setTitle("编辑联系人")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput.text.toString().trim()
                val style = styleInput.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                contactManager.updateContact(contact.copy(
                    name = name,
                    style = style.ifEmpty { "朋友之间随意聊天，回复简短自然" }
                ))
                refreshList()
                Toast.makeText(this, "已更新：$name", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("清除聊天记录") { _, _ ->
                contactManager.clearChatHistory(contact.id)
                Toast.makeText(this, "已清除 ${contact.name} 的聊天记录", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteDialog(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle("删除联系人")
            .setMessage("确定要删除「${contact.name}」吗？\n聊天记录也会被清除。")
            .setPositiveButton("删除") { _, _ ->
                contactManager.removeContact(contact.id)
                refreshList()
                Toast.makeText(this, "已删除：${contact.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshList() {
        adapter.updateList(contactManager.getContacts())
        updateEmptyView()
    }

    private fun updateEmptyView() {
        val contacts = contactManager.getContacts()
        binding.tvContactCount.text = "共 ${contacts.size} 个联系人，${contacts.count { it.enabled }} 个已启用"
    }

    // ========== RecyclerView Adapter ==========

    class ContactAdapter(
        private var contacts: MutableList<Contact>,
        private val onToggle: (Contact) -> Unit,
        private val onEdit: (Contact) -> Unit,
        private val onDelete: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemContactBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemContactBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            holder.binding.apply {
                tvName.text = contact.name
                tvStyle.text = contact.style
                switchEnabled.isChecked = contact.enabled
                tvStatus.text = if (contact.enabled) "✅ 已启用" else "⏸️ 已暂停"
                
                switchEnabled.setOnCheckedChangeListener { _, _ ->
                    onToggle(contact)
                }
                
                root.setOnClickListener {
                    onEdit(contact)
                }
                
                root.setOnLongClickListener {
                    onDelete(contact)
                    true
                }
            }
        }

        override fun getItemCount() = contacts.size

        fun updateList(newContacts: List<Contact>) {
            contacts.clear()
            contacts.addAll(newContacts)
            notifyDataSetChanged()
        }
    }
}
