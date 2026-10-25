package me.kbai.zhenxunui.model

data class LabConversation(
    val id: String,
    val isGroup: Boolean,
    val title: String,
    val accountId: String,
    val accountName: String,
    val accountAvatar: String
)

data class LabMessage(
    val conversationId: String,
    val userId: String,
    val name: String,
    val avatar: String,
    val text: String,
    val fromBot: Boolean,
    val time: Long
)
