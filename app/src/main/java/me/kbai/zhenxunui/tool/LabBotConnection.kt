package me.kbai.zhenxunui.tool

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.extends.logI
import me.kbai.zhenxunui.model.LabConversation
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class LabBotConnectionConfig(
    val url: String,
    val token: String,
    val selfId: String
)

object LabBotConnection {
    private const val RECONNECT_DELAY_MS = 5_000L
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    sealed class Status {
        data object Idle : Status()
        data object Connecting : Status()
        data object Connected : Status()
        data class Failed(val reason: String) : Status()
    }

    private val mScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private var mConfig: LabBotConnectionConfig? = null
    private var mWebSocket: WebSocket? = null
    private var mManualClosed = false
    private var mReconnectJob: Job? = null
    private var mHeartbeatJob: Job? = null
    private val mMessageId = AtomicInteger((0..999999).random())

    fun config() = mConfig

    fun isConnected() = _status.value == Status.Connected

    fun ensureInit(context: Context) {
        if (mConfig != null) return
        synchronized(this) {
            if (mConfig != null) return
            val prefs = context.applicationContext
                .getSharedPreferences("lab_store", Context.MODE_PRIVATE)
            val json = prefs.getString("bot_connection", null) ?: return
            runCatching {
                val obj = JSONObject(json)
                val config = LabBotConnectionConfig(
                    obj.optString("url"),
                    obj.optString("token"),
                    obj.optString("selfId")
                )
                if (config.url.isNotBlank()) {
                    mConfig = config
                    connect()
                }
            }.onFailure { logE(it) }
        }
    }

    fun saveConfig(context: Context, config: LabBotConnectionConfig?) {
        val prefs = context.applicationContext
            .getSharedPreferences("lab_store", Context.MODE_PRIVATE)
        if (config == null) {
            prefs.edit().remove("bot_connection").apply()
        } else {
            prefs.edit()
                .putString(
                    "bot_connection",
                    JSONObject()
                        .put("url", config.url)
                        .put("token", config.token)
                        .put("selfId", config.selfId)
                        .toString()
                )
                .apply()
        }
        disconnect()
        mConfig = config
        if (config != null) connect()
    }

    fun disconnect() {
        mManualClosed = true
        mReconnectJob?.cancel()
        mHeartbeatJob?.cancel()
        mWebSocket?.close(1000, "lab closed")
        mWebSocket = null
        _status.value = Status.Idle
    }

    private fun connect() {
        val config = mConfig ?: return
        if (_status.value == Status.Connecting || _status.value == Status.Connected) return
        mManualClosed = false
        _status.value = Status.Connecting

        val request = Request.Builder()
            .url(config.url)
            .apply {
                if (config.selfId.isNotBlank()) {
                    header("X-Self-ID", config.selfId)
                }
                if (config.token.isNotBlank()) {
                    header("Authorization", "Bearer ${config.token}")
                    url("${config.url}${if (config.url.contains('?')) '&' else '?'}access_token=${config.token}")
                }
            }
            .build()

        mWebSocket = mClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logI("Lab bot ws connected.")
                _status.value = Status.Connected
                webSocket.send(buildLifecycleEvent().toString())
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mScope.launch { onBotPayload(text, webSocket) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _status.value = Status.Idle
                stopHeartbeat()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logE("Lab bot ws failure: $t")
                _status.value = Status.Failed(t.message ?: "unknown")
                stopHeartbeat()
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (mManualClosed || mConfig == null) return
        mReconnectJob?.cancel()
        mReconnectJob = mScope.launch {
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        mHeartbeatJob = mScope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                mWebSocket?.send(buildHeartbeatEvent().toString())
            }
        }
    }

    private fun stopHeartbeat() {
        mHeartbeatJob?.cancel()
        mHeartbeatJob = null
    }

    fun sendUserMessage(conversation: LabConversation, text: String): Boolean {
        val webSocket = mWebSocket ?: return false
        if (_status.value != Status.Connected) return false
        val event = if (conversation.isGroup) {
            buildMessageEvent(
                messageType = "group",
                senderId = conversation.accountId,
                nickname = conversation.accountName,
                peerKey = "group_id",
                peerValue = LabStore.peerIdOf(conversation),
                text = text
            )
        } else {
            buildMessageEvent(
                messageType = "private",
                senderId = conversation.accountId,
                nickname = conversation.accountName,
                peerKey = "user_id",
                peerValue = conversation.accountId,
                text = text
            )
        }
        return webSocket.send(event.toString())
    }

    private fun onBotPayload(text: String, webSocket: WebSocket) {
        runCatching {
            val payload = JSONObject(text)
            if (payload.has("action")) {
                handleAction(payload, webSocket)
            }
        }.onFailure { logE(it) }
    }

    private fun handleAction(payload: JSONObject, webSocket: WebSocket) {
        val action = payload.optString("action")
        val params = payload.optJSONObject("params") ?: JSONObject()
        val echo = payload.opt("echo")

        fun reply(data: Any? = null) {
            webSocket.send(
                JSONObject()
                    .put("status", "ok")
                    .put("retcode", 0)
                    .put("data", data ?: JSONObject())
                    .put("echo", echo)
                    .toString()
            )
        }

        when (action) {
            "send_msg", "send_group_msg", "send_private_msg" -> {
                val message = extractMessageText(params.opt("message"))
                val peerId = when {
                    action == "send_group_msg" || params.optString("message_type") == "group" ->
                        params.optString("group_id")
                    else -> params.optString("user_id")
                }
                val conversation = LabStore.findGroupByPeerId(peerId)
                    ?: LabStore.findFriendByUserId(peerId)
                conversation?.let { LabStore.emitBotMessage(it, message) }
                reply(JSONObject().put("message_id", mMessageId.incrementAndGet()))
            }
            "get_login_info" -> reply(
                JSONObject()
                    .put("user_id", numericOrString(mConfig?.selfId.orEmpty()))
                    .put("nickname", LabStore.defaultBotName())
            )
            "get_group_list", "get_friend_list" -> reply(JSONArray())
            "get_version_info" -> reply(
                JSONObject()
                    .put("app_name", "zhenxun-android-lab")
                    .put("app_version", "v11")
            )
            "get_status" -> reply(
                JSONObject().put("online", true).put("good", true)
            )
            else -> webSocket.send(
                JSONObject()
                    .put("status", "failed")
                    .put("retcode", 1400)
                    .put("data", JSONObject())
                    .put("echo", echo)
                    .toString()
            )
        }
    }

    private fun extractMessageText(message: Any?): String = when (message) {
        is String -> convertCqCode(message)
        is JSONArray -> buildString {
            for (i in 0 until message.length()) {
                val segment = message.optJSONObject(i) ?: continue
                when (val type = segment.optString("type")) {
                    "text" -> append(segment.optJSONObject("data")?.optString("text").orEmpty())
                    "at" -> append("@${segment.optJSONObject("data")?.optString("qq").orEmpty()} ")
                    "image" -> append("[图片]")
                    "face" -> append("[表情]")
                    "record" -> append("[语音]")
                    "video" -> append("[视频]")
                    "forward" -> append("[合并转发]")
                    "json" -> append("[卡片]")
                    "reply" -> append("[回复]")
                    else -> append("[$type]")
                }
            }
        }.trim()
        else -> ""
    }

    private fun convertCqCode(raw: String): String {
        val regex = Regex("\\[CQ:([a-zA-Z]+)(?:,[^\\]]*)?\\]")
        return regex.replace(raw) { match ->
            when (match.groupValues[1].lowercase()) {
                "image" -> "[图片]"
                "face" -> "[表情]"
                "at" -> "[@]"
                "record" -> "[语音]"
                "video" -> "[视频]"
                "forward" -> "[合并转发]"
                "json" -> "[卡片]"
                "reply" -> "[回复]"
                else -> "[${match.groupValues[1]}]"
            }
        }.trim()
    }

    private fun buildMessageEvent(
        messageType: String,
        senderId: String,
        nickname: String,
        peerKey: String,
        peerValue: String,
        text: String
    ): JSONObject {
        val config = mConfig
        val event = JSONObject()
            .put("time", System.currentTimeMillis() / 1000)
            .put("self_id", numericOrString(config?.selfId.orEmpty()))
            .put("post_type", "message")
            .put("message_type", messageType)
            .put("sub_type", "normal")
            .put("message_id", mMessageId.incrementAndGet())
            .put("user_id", numericOrString(senderId))
            .put("message", text)
            .put("raw_message", text)
            .put("font", 0)
            .put(
                "sender",
                JSONObject()
                    .put("user_id", numericOrString(senderId))
                    .put("nickname", nickname)
                    .put("card", nickname)
                    .put("role", "member")
            )
        if (peerKey == "group_id") {
            event.put("group_id", numericOrString(peerValue))
        }
        return event
    }

    private fun buildLifecycleEvent(): JSONObject =
        JSONObject()
            .put("time", System.currentTimeMillis() / 1000)
            .put("self_id", numericOrString(mConfig?.selfId.orEmpty()))
            .put("post_type", "meta_event")
            .put("meta_event_type", "lifecycle")
            .put("sub_type", "connect")

    private fun buildHeartbeatEvent(): JSONObject =
        JSONObject()
            .put("time", System.currentTimeMillis() / 1000)
            .put("self_id", numericOrString(mConfig?.selfId.orEmpty()))
            .put("post_type", "meta_event")
            .put("meta_event_type", "heartbeat")
            .put("interval", HEARTBEAT_INTERVAL_MS)

    private fun numericOrString(value: String): Any =
        value.toLongOrNull() ?: value
}
