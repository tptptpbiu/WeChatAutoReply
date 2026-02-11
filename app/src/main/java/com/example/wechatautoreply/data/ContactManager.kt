package com.example.wechatautoreply.data

import android.content.Context
import com.example.wechatautoreply.model.ChatMessage
import com.example.wechatautoreply.model.Contact
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 联系人管理器
 * 管理自动回复联系人白名单和每个联系人的聊天历史
 */
class ContactManager(private val context: Context) {

    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }
    
    private val contactsFile: File
        get() = File(context.filesDir, "contacts.json")
    
    private val chatHistoryDir: File
        get() = File(context.filesDir, "chat_history").also { it.mkdirs() }

    // ========== 联系人管理 ==========
    
    /**
     * 获取所有联系人
     */
    fun getContacts(): List<Contact> {
        return try {
            if (contactsFile.exists()) {
                json.decodeFromString<List<Contact>>(contactsFile.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存联系人列表
     */
    fun saveContacts(contacts: List<Contact>) {
        contactsFile.writeText(json.encodeToString(contacts))
    }

    /**
     * 添加联系人
     */
    fun addContact(contact: Contact) {
        val contacts = getContacts().toMutableList()
        contacts.add(contact)
        saveContacts(contacts)
    }

    /**
     * 删除联系人
     */
    fun removeContact(contactId: String) {
        val contacts = getContacts().toMutableList()
        contacts.removeAll { it.id == contactId }
        saveContacts(contacts)
        // 同时删除聊天历史
        getChatHistoryFile(contactId).delete()
    }

    /**
     * 更新联系人
     */
    fun updateContact(contact: Contact) {
        val contacts = getContacts().toMutableList()
        val index = contacts.indexOfFirst { it.id == contact.id }
        if (index >= 0) {
            contacts[index] = contact
            saveContacts(contacts)
        }
    }

    /**
     * 切换联系人启用状态
     */
    fun toggleContact(contactId: String): Boolean {
        val contacts = getContacts().toMutableList()
        val index = contacts.indexOfFirst { it.id == contactId }
        if (index >= 0) {
            contacts[index] = contacts[index].copy(enabled = !contacts[index].enabled)
            saveContacts(contacts)
            return contacts[index].enabled
        }
        return false
    }

    /**
     * 获取启用的联系人列表
     */
    fun getEnabledContacts(): List<Contact> {
        return getContacts().filter { it.enabled }
    }

    /**
     * 根据名称查找联系人（模糊匹配）
     */
    fun findContactByName(senderName: String): Contact? {
        return getEnabledContacts().find { contact ->
            senderName.contains(contact.name) || contact.name.contains(senderName)
        }
    }

    // ========== 聊天历史管理 ==========

    private fun getChatHistoryFile(contactId: String): File {
        return File(chatHistoryDir, "${contactId}.json")
    }

    /**
     * 获取联系人的聊天历史
     */
    fun getChatHistory(contactId: String): List<ChatMessage> {
        return try {
            val file = getChatHistoryFile(contactId)
            if (file.exists()) {
                json.decodeFromString<List<ChatMessage>>(file.readText())
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 添加聊天消息并保留最近 20 条
     */
    fun addChatMessage(contactId: String, message: ChatMessage) {
        val history = getChatHistory(contactId).toMutableList()
        history.add(message)
        // 保留最近 20 条，避免上下文太长
        while (history.size > 20) {
            history.removeAt(0)
        }
        val file = getChatHistoryFile(contactId)
        file.writeText(json.encodeToString(history))
    }

    /**
     * 清除联系人的聊天历史
     */
    fun clearChatHistory(contactId: String) {
        getChatHistoryFile(contactId).delete()
    }
}
