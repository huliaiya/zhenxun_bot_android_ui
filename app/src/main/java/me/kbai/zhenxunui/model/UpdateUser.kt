package me.kbai.zhenxunui.model

import com.google.gson.annotations.SerializedName

data class UpdateUser(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("is_ban")
    val isBan: Boolean
)
