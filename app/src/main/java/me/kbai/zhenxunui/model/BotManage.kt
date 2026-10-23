package me.kbai.zhenxunui.model

import com.google.gson.annotations.SerializedName

data class BotBlockModule(
    @SerializedName("bot_id")
    val botId: String,
    @SerializedName("block_plugins")
    val blockPlugins: List<String>,
    @SerializedName("block_tasks")
    val blockTasks: List<String>,
    @SerializedName("all_plugins")
    val allPlugins: List<ModuleItem>,
    @SerializedName("all_tasks")
    val allTasks: List<ModuleItem>
)

data class ModuleItem(
    val module: String,
    val name: String
)

data class BotManageUpdate(
    @SerializedName("bot_id")
    val botId: String,
    @SerializedName("block_plugins")
    val blockPlugins: List<String>,
    @SerializedName("block_tasks")
    val blockTasks: List<String>
)

data class BotStatusParam(
    @SerializedName("bot_id")
    val botId: String,
    val status: Boolean
)
