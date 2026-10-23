package me.kbai.zhenxunui.model

import com.google.gson.annotations.SerializedName

data class BotConnectLog(
    @SerializedName("bot_id")
    val botId: String,
    @SerializedName("connect_time")
    val connectTime: String,
    val type: Int
)

data class BotConnectLogPage(
    val total: Int,
    val data: List<BotConnectLog>
)

data class GetConnectLog(
    val index: Int = 1,
    val size: Int = 100
) {
    companion object {
        val DEFAULT = GetConnectLog()
    }
}
