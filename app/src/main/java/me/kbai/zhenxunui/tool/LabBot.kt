package me.kbai.zhenxunui.tool

import android.content.Context
import me.kbai.zhenxunui.R

object LabBot {
    private val mHelpKeywords = listOf("帮助", "help", "菜单")
    private val mGreetKeywords = listOf("你好", "hello", "hi", "在吗")
    private val mCheckInKeywords = listOf("签到", "打卡")
    private val mPluginKeywords = listOf("插件", "功能")

    fun reply(context: Context, text: String): String {
        val lower = text.lowercase()
        return when {
            mHelpKeywords.any { lower.contains(it) } ->
                context.getString(R.string.lab_reply_help)
            mGreetKeywords.any { lower.contains(it) } ->
                context.getString(R.string.lab_reply_hello)
            mCheckInKeywords.any { lower.contains(it) } ->
                context.getString(R.string.lab_reply_checkin)
            mPluginKeywords.any { lower.contains(it) } ->
                context.getString(R.string.lab_reply_plugin)
            else -> context.getString(R.string.lab_reply_default, text)
        }
    }
}
