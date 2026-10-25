package me.kbai.zhenxunui.tool

import android.content.Context
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.model.LabMessage
import java.util.UUID

object LabStore {
    private const val SP_NAME = "lab_store"
    private const val KEY_CONVERSATIONS = "conversations"
    private const val KEY_MESSAGES_PREFIX = "messages_"

    private var mPrefs: android.content.SharedPreferences? = null
    private var mAppContext: Context? = null

    private val _conversations = MutableStateFlow<List<LabConversation>>(emptyList())
    val conversations: StateFlow<List<LabConversation>> = _conversations

    fun ensureInit(context: Context) {
        if (mPrefs != null) return
        synchronized(this) {
            if (mPrefs != null) return
            val prefs = context.applicationContext.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            mPrefs = prefs
            mAppContext = context.applicationContext
            _conversations.value = readConversations(prefs)
        }
    }

    fun addGroup(): LabConversation {
        val list = _conversations.value
        val index = list.count { it.isGroup } + 1
        val context = mAppContext
        val conversation = LabConversation(
            id = UUID.randomUUID().toString(),
            isGroup = true,
            title = context?.getString(R.string.lab_group_default_name, index)
                ?: "Lab Group $index",
            accountId = "80000$index",
            accountName = context?.getString(R.string.lab_group_default_account, index)
                ?: "Member $index",
            accountAvatar = LabAvatarResolver.BUILTIN_GROUP_MEMBER
        )
        saveConversations(list + conversation)
        return conversation
    }

    fun addFriend(name: String, qq: String, avatar: String): LabConversation {
        val conversation = LabConversation(
            id = UUID.randomUUID().toString(),
            isGroup = false,
            title = name,
            accountId = qq,
            accountName = name,
            accountAvatar = avatar
        )
        saveConversations(_conversations.value + conversation)
        return conversation
    }

    fun isFriendQqExists(qq: String) =
        _conversations.value.any { !it.isGroup && it.accountId == qq }

    fun findConversation(id: String) = _conversations.value.firstOrNull { it.id == id }

    fun messages(conversationId: String): List<LabMessage> {
        val prefs = mPrefs ?: return emptyList()
        return runCatching {
            prefs.getString(KEY_MESSAGES_PREFIX + conversationId, null)
                ?.let { json ->
                    val type = object : TypeToken<List<LabMessage>>() {}.type
                    Constants.gson.fromJson<List<LabMessage>>(json, type)
                }
                .orEmpty()
        }.onFailure { logE(it) }.getOrDefault(emptyList())
    }

    fun appendMessage(message: LabMessage) {
        val prefs = mPrefs ?: return
        val list = messages(message.conversationId) + message
        runCatching {
            prefs.edit()
                .putString(KEY_MESSAGES_PREFIX + message.conversationId, Constants.gson.toJson(list))
                .apply()
        }.onFailure { logE(it) }
    }

    private fun readConversations(prefs: android.content.SharedPreferences): List<LabConversation> =
        runCatching {
            prefs.getString(KEY_CONVERSATIONS, null)
                ?.let { json ->
                    val type = object : TypeToken<List<LabConversation>>() {}.type
                    Constants.gson.fromJson<List<LabConversation>>(json, type)
                }
                .orEmpty()
        }.onFailure { logE(it) }.getOrDefault(emptyList())

    private fun saveConversations(list: List<LabConversation>) {
        _conversations.value = list
        val prefs = mPrefs ?: return
        runCatching {
            prefs.edit()
                .putString(KEY_CONVERSATIONS, Constants.gson.toJson(list))
                .apply()
        }.onFailure { logE(it) }
    }
}
