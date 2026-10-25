package me.kbai.zhenxunui.tool

import android.net.Uri
import me.kbai.zhenxunui.R

object LabAvatarResolver {
    const val BUILTIN_GROUP_MEMBER = "builtin:group"
    const val BUILTIN_FRIEND = "builtin:friend"
    const val BUILTIN_BOT = "builtin:bot"

    fun resolve(avatar: String): Any = when (avatar) {
        BUILTIN_GROUP_MEMBER -> R.drawable.ic_lab_group_avatar
        BUILTIN_FRIEND -> R.drawable.ic_lab_friend_avatar
        BUILTIN_BOT -> R.drawable.ic_lab_bot_avatar
        else -> Uri.parse(avatar)
    }

    fun isBuiltin(avatar: String) = avatar.startsWith("builtin:")
}
