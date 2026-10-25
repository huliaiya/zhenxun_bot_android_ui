package me.kbai.zhenxunui.model

data class LabConversation(
    val id: String,
    val isGroup: Boolean,
    val title: String,
    val accountId: String,
    val accountName: String,
    val accountAvatar: String,
    val peerId: String? = null,
    val botName: String? = null,
    val botAvatar: String? = null
)

data class LabMessage(
    val conversationId: String,
    val userId: String,
    val name: String,
    val avatar: String,
    val text: String,
    val fromBot: Boolean,
    val time: Long,
    val reply: String? = null,
    val images: List<String>? = null,
    val videos: List<String>? = null
)
