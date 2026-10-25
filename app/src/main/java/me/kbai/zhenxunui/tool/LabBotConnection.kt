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
                val parsed = extractMessage(params.opt("message"))
                val peerId = when {
                    action == "send_group_msg" || params.optString("message_type") == "group" ->
                        params.optString("group_id")
                    else -> params.optString("user_id")
                }
                val conversation = LabStore.findGroupByPeerId(peerId)
                    ?: LabStore.findFriendByUserId(peerId)
                val botMessageId = mMessageId.incrementAndGet()
                rememberMessage(botMessageId.toLong(), parsed.text)
                conversation?.let {
                    LabStore.emitBotMessage(
                        it,
                        parsed.text,
                        parsed.reply,
                        parsed.images,
                        parsed.videos
                    )
                }
                reply(JSONObject().put("message_id", botMessageId))
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

    private class ParsedMessage(
        val text: String,
        val reply: String? = null,
        val images: List<String> = emptyList(),
        val videos: List<String> = emptyList()
    )

    private val mMessageTexts = object : LinkedHashMap<Long, String>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>) = size > 200
    }

    private fun rememberMessage(id: Long, text: String) {
        synchronized(mMessageTexts) { mMessageTexts[id] = text }
    }

    private fun replyTextOf(id: Long): String? =
        synchronized(mMessageTexts) { mMessageTexts[id] }

    private fun extractMessage(message: Any?): ParsedMessage = when (message) {
        is String -> parseCqString(message)
        is JSONArray -> parseSegmentArray(message)
        else -> ParsedMessage("")
    }

    private fun parseSegmentArray(segments: JSONArray): ParsedMessage {
        val text = StringBuilder()
        val images = mutableListOf<String>()
        val videos = mutableListOf<String>()
        var replyId = -1L
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONObject(i) ?: continue
            val data = segment.optJSONObject("data") ?: JSONObject()
            when (segment.optString("type")) {
                "text" -> text.append(data.optString("text"))
                "reply" -> replyId = data.optLong("id", -1L)
                "image" -> mediaUrl(data)?.let { images += it }
                "video" -> mediaUrl(data)?.let { videos += it }
            }
        }
        return ParsedMessage(
            text = text.toString().trim(),
            reply = replyTextOf(replyId),
            images = images,
            videos = videos
        )
    }

    private fun parseCqString(raw: String): ParsedMessage {
        val text = StringBuilder()
        val images = mutableListOf<String>()
        val videos = mutableListOf<String>()
        var replyId = -1L
        val regex = Regex("\\[CQ:([a-zA-Z]+)((?:,[a-zA-Z]+=[^,\\]]*)*)\\]")
        var lastEnd = 0
        for (match in regex.findAll(raw)) {
            text.append(raw.substring(lastEnd, match.range.first))
            lastEnd = match.range.last + 1
            val attrs = parseCqAttrs(match.groupValues[2])
            when (match.groupValues[1].lowercase()) {
                "reply" -> replyId = attrs["id"]?.toLongOrNull() ?: -1L
                "image" -> attrs["url"]?.let { images += it }
                "video" -> attrs["url"]?.let { videos += it }
            }
        }
        text.append(raw.substring(lastEnd))
        return ParsedMessage(
            text = text.toString().trim(),
            reply = replyTextOf(replyId),
            images = images,
            videos = videos
        )
    }

    private fun parseCqAttrs(raw: String): Map<String, String> =
        raw.split(',').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null
            else part.substring(0, idx) to unescapeCq(part.substring(idx + 1))
        }.toMap()

    private fun unescapeCq(value: String): String = value
        .replace("&#44;", ",")
        .replace("&#93;", "]")
        .replace("&#91;", "[")
        .replace("&#38;", "&")

    private fun mediaUrl(data: JSONObject): String? {
        val url = data.optString("url")
        if (url.startsWith("http")) return url
        val file = data.optString("file")
        if (file.startsWith("http")) return file
        return null
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
        val messageId = mMessageId.incrementAndGet()
        rememberMessage(messageId.toLong(), text)
        val event = JSONObject()
            .put("time", System.currentTimeMillis() / 1000)
            .put("self_id", numericOrString(config?.selfId.orEmpty()))
            .put("post_type", "message")
            .put("message_type", messageType)
            .put("sub_type", "normal")
            .put("message_id", messageId)
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
