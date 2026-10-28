package me.kbai.zhenxunui.tool

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.model.LabConversation
import me.kbai.zhenxunui.model.LabMessage
import java.io.File
import java.util.UUID

object LabStore {
    private const val SP_NAME = "lab_store"
    private const val KEY_CONVERSATIONS = "conversations"
    private const val KEY_MESSAGES_PREFIX = "messages_"
    private const val FILE_CONVERSATIONS = "lab_conversations.json"
    private const val DIR_MESSAGES = "lab_messages"

    private var mPrefs: android.content.SharedPreferences? = null
    private var mAppContext: Context? = null
    private var mMessagesDir: File? = null
    private var mConversationsFile: File? = null

    private val mMessageCache = HashMap<String, MutableList<LabMessage>>()

    private val _conversations = MutableStateFlow<List<LabConversation>>(emptyList())
    val conversations: StateFlow<List<LabConversation>> = _conversations

    private val _botMessageEvents = MutableSharedFlow<LabMessage>(extraBufferCapacity = 64)
    val botMessageEvents: SharedFlow<LabMessage> = _botMessageEvents

    fun ensureInit(context: Context) {
        if (mMessagesDir != null) return
        synchronized(this) {
            if (mMessagesDir != null) return
            val app = context.applicationContext
            mAppContext = app
            val dir = File(app.filesDir, DIR_MESSAGES).apply { mkdirs() }
            mMessagesDir = dir
            mConversationsFile = File(app.filesDir, FILE_CONVERSATIONS)
            mPrefs = app.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            loadConversations()
            loadAllMessages(dir)
            LabBotConnection.ensureInit(app)
        }
    }

    fun nextGroupIndex() = _conversations.value.count { it.isGroup } + 1

    fun defaultBotName(): String =
        mAppContext?.getString(R.string.lab_bot_name) ?: "Zhenxun"

    fun addGroup(
        title: String,
        accountName: String?,
        accountAvatar: String,
        botName: String,
        botAvatar: String
    ): LabConversation {
        val index = nextGroupIndex()
        val conversation = LabConversation(
            id = UUID.randomUUID().toString(),
            isGroup = true,
            title = title,
            accountId = "10000$index",
            accountName = accountName?.takeIf { it.isNotBlank() }
                ?: mAppContext?.getString(R.string.lab_group_default_account, index)
                ?: "Member $index",
            accountAvatar = accountAvatar,
            peerId = "80000$index",
            botName = botName,
            botAvatar = botAvatar
        )
        saveConversations(_conversations.value + conversation)
        return conversation
    }

    fun addFriend(name: String, qq: String, avatar: String, botName: String, botAvatar: String): LabConversation {
        val conversation = LabConversation(
            id = UUID.randomUUID().toString(),
            isGroup = false,
            title = name,
            accountId = qq,
            accountName = name,
            accountAvatar = avatar,
            peerId = qq,
            botName = botName,
            botAvatar = botAvatar
        )
        saveConversations(_conversations.value + conversation)
        return conversation
    }

    fun isFriendQqExists(qq: String) =
        _conversations.value.any { !it.isGroup && it.accountId == qq }

    fun findConversation(id: String) = _conversations.value.firstOrNull { it.id == id }

    fun deleteConversation(id: String) {
        val conversation = findConversation(id) ?: return
        saveConversations(_conversations.value.filter { it.id != id })
        clearMessages(id)
        val avatar = conversation.accountAvatar
        if (avatar.startsWith("/")) {
            runCatching { File(avatar).delete() }.onFailure { logE(it) }
        }
    }

    fun botNameOf(conversation: LabConversation): String =
        conversation.botName ?: defaultBotName()

    fun botAvatarOf(conversation: LabConversation): String =
        conversation.botAvatar ?: LabAvatarResolver.BUILTIN_BOT

    fun peerIdOf(conversation: LabConversation): String =
        conversation.peerId ?: conversation.accountId

    fun findGroupByPeerId(peerId: String) =
        _conversations.value.firstOrNull {
            it.isGroup && (it.peerId ?: it.accountId) == peerId
        }

    fun findFriendByUserId(userId: String) =
        _conversations.value.firstOrNull { !it.isGroup && it.accountId == userId }

    fun messages(conversationId: String): List<LabMessage> =
        synchronized(mMessageCache) {
            mMessageCache[conversationId]?.toList() ?: emptyList()
        }

    fun appendMessage(message: LabMessage) {
        synchronized(mMessageCache) {
            mMessageCache.getOrPut(message.conversationId) { mutableListOf() }
                .add(message)
            persistMessages(message.conversationId)
        }
    }

    fun clearMessages(conversationId: String) {
        synchronized(mMessageCache) {
            mMessageCache.remove(conversationId)
            runCatching {
                val file = messageFile(conversationId)
                if (file.exists()) file.delete()
            }.onFailure { logE(it) }
        }
    }

    fun deleteMessage(message: LabMessage): Boolean {
        synchronized(mMessageCache) {
            val list = mMessageCache[message.conversationId] ?: return false
            val index = list.indexOfFirst {
                it.time == message.time &&
                    it.userId == message.userId &&
                    it.text == message.text &&
                    it.fromBot == message.fromBot
            }
            if (index < 0) return false
            list.removeAt(index)
            persistMessages(message.conversationId)
        }
        return true
    }

    fun emitBotMessage(
        conversation: LabConversation,
        text: String,
        reply: String? = null,
        images: List<String>? = null,
        videos: List<String>? = null
    ) {
        val message = LabMessage(
            conversationId = conversation.id,
            userId = "lab_bot",
            name = botNameOf(conversation),
            avatar = botAvatarOf(conversation),
            text = text,
            fromBot = true,
            time = System.currentTimeMillis(),
            reply = reply,
            images = images,
            videos = videos
        )
        appendMessage(message)
        _botMessageEvents.tryEmit(message)
    }

    private fun decodeMessages(json: String): List<LabMessage> =
        Constants.gson.fromJson(json, Array<LabMessage>::class.java)
            ?.filterNotNull().orEmpty()

    private fun decodeConversations(json: String): List<LabConversation> =
        Constants.gson.fromJson(json, Array<LabConversation>::class.java)
            ?.filterNotNull().orEmpty()

    private fun messageFile(conversationId: String): File =
        File(mMessagesDir, "$conversationId.json")

    private fun persistMessages(conversationId: String) {
        runCatching {
            val list = mMessageCache[conversationId] ?: emptyList()
            val file = messageFile(conversationId)
            val tmp = File(mMessagesDir, "${conversationId}.json.tmp")
            tmp.writeText(Constants.gson.toJson(list))
            if (file.exists() && !file.delete()) {
                logE(IllegalStateException("cannot replace ${file.name}"))
            }
            if (!tmp.renameTo(file)) {
                logE(IllegalStateException("cannot rename ${tmp.name}"))
            }
        }.onFailure { logE(it) }
    }

    private fun loadAllMessages(dir: File) {
        synchronized(mMessageCache) {
            mMessageCache.clear()
            val migrated = mutableMapOf<String, List<LabMessage>>()
            mPrefs?.all?.keys?.filter { it.startsWith(KEY_MESSAGES_PREFIX) }?.forEach { key ->
                val id = key.removePrefix(KEY_MESSAGES_PREFIX)
                val list = runCatching {
                    mPrefs!!.getString(key, null)?.let { json ->
                        decodeMessages(json)
                    }
                }.onFailure { logE(it) }.getOrNull() ?: emptyList()
                if (list.isNotEmpty()) migrated[id] = list
            }
            dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { file ->
                val id = file.name.removeSuffix(".json")
                val list = runCatching {
                    decodeMessages(file.readText())
                }.onFailure { logE(it) }.getOrNull() ?: emptyList()
                if (list.isNotEmpty()) {
                    mMessageCache[id] = list.toMutableList()
                }
            }
            migrated.forEach { (id, list) ->
                if (mMessageCache[id].isNullOrEmpty()) {
                    mMessageCache[id] = list.toMutableList()
                    persistMessages(id)
                }
            }
            if (migrated.isNotEmpty()) {
                val editor = mPrefs?.edit() ?: return
                mPrefs?.all?.keys
                    ?.filter { it.startsWith(KEY_MESSAGES_PREFIX) }
                    ?.forEach { editor.remove(it) }
                editor.apply()
            }
        }
    }

    private fun loadConversations() {
        var list: List<LabConversation>? = null
        mConversationsFile?.let { file ->
            if (file.exists()) {
                list = runCatching {
                    decodeConversations(file.readText())
                }.onFailure { logE(it) }.getOrNull()
            }
        }
        if (list == null) {
            list = runCatching {
                mPrefs?.getString(KEY_CONVERSATIONS, null)?.let { json ->
                    decodeConversations(json)
                }
            }.onFailure { logE(it) }.getOrNull()
            list?.let {
                persistConversationsFile(it)
                mPrefs?.edit()?.remove(KEY_CONVERSATIONS)?.apply()
            }
        }
        _conversations.value = list.orEmpty()
    }

    private fun saveConversations(list: List<LabConversation>) {
        _conversations.value = list
        persistConversationsFile(list)
    }

    private fun persistConversationsFile(list: List<LabConversation>) {
        val file = mConversationsFile ?: return
        runCatching {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(Constants.gson.toJson(list))
            if (file.exists() && !file.delete()) {
                logE(IllegalStateException("cannot replace ${file.name}"))
            }
            if (!tmp.renameTo(file)) {
                logE(IllegalStateException("cannot rename ${tmp.name}"))
            }
        }.onFailure { logE(it) }
    }
}
