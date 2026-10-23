package me.kbai.zhenxunui.viewmodel

import android.text.SpannableStringBuilder
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import me.kbai.zhenxunui.extends.apiCollect
import me.kbai.zhenxunui.extends.logE
import me.kbai.zhenxunui.model.BotMessageCount
import me.kbai.zhenxunui.model.SystemStatus
import me.kbai.zhenxunui.repository.ApiRepository
import me.kbai.zhenxunui.repository.Resource
import me.kbai.zhenxunui.tool.AlwaysDifferent
import me.kbai.zhenxunui.tool.Ansi2SpannedHelper

class ConsoleViewModel : ViewModel() {

    private val _systemStatus: MutableLiveData<SystemStatus> = MutableLiveData()
    val systemStatus: LiveData<SystemStatus> = _systemStatus

    private val _messageCount = MutableStateFlow(BotMessageCount())
    val messageCount: StateFlow<BotMessageCount> = _messageCount

    private val _connectTimeFallback = MutableStateFlow(0L)
    val connectTimeFallback: StateFlow<Long> = _connectTimeFallback
    private var mFallbackBotId: String? = null

    private val _activeGroup: MutableStateFlow<String> = MutableStateFlow("[]")
    val activeGroup: StateFlow<String> = _activeGroup

    private val _popularPlugin: MutableStateFlow<String> = MutableStateFlow("[]")
    val popularPlugin: StateFlow<String> = _popularPlugin

    private val mStatusWebSocketHolder =
        ApiRepository.newSystemStatusWebSocket(viewModelScope) { message, exception ->
            if (message == null) logE(exception!!)
            _systemStatus.value = message ?: return@newSystemStatusWebSocket
        }

    private val _botLogs: MutableStateFlow<AlwaysDifferent<SpannableStringBuilder>> =
        MutableStateFlow(AlwaysDifferent(SpannableStringBuilder()))
    val botLogs: StateFlow<AlwaysDifferent<SpannableStringBuilder>> = _botLogs
    private val mAnsi2SpannedHelper = Ansi2SpannedHelper()
    private var mLogsLines = 0

    private val mLogsWebSocketHolder = ApiRepository.newBotLogsWebSocket(viewModelScope) {
        val builder = _botLogs.value.obj

        if (builder.isNotEmpty()) builder.append("\n")
        builder.append(mAnsi2SpannedHelper.parse(it))
        if (mLogsLines >= 100) {
            builder.delete(0, builder.indexOf('\n') + 1)
        } else {
            mLogsLines++
        }
        _botLogs.value = _botLogs.value
    }

    fun requestMessageCount(botId: String) = viewModelScope.launch {
        var res = ApiRepository.getMessageCount(botId).apiCollect()
        //旧版服务端路由名不同, 404时回退旧端点再试一次
        if (res.status == Resource.Status.FAIL && res.httpStatusCode == 404) {
            res = ApiRepository.getMessageCountLegacy(botId).apiCollect()
        }
        if (res.data != null) {
            _messageCount.update { res.data!! }
        }
    }

    fun requestConnectTimeFallback(botId: String) {
        mFallbackBotId = botId
        _connectTimeFallback.value = 0L
        viewModelScope.launch {
            val res = ApiRepository.getConnectLog().apiCollect()
            val latest = res.data?.data?.firstOrNull { it.botId == botId } ?: return@launch
            if (latest.type != 1) return@launch
            val time = parseConnectTime(latest.connectTime) ?: return@launch
            if (mFallbackBotId != botId) return@launch
            _connectTimeFallback.value = time
        }
    }

    private fun parseConnectTime(value: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            .parse(value)?.time?.div(1000)
    }.getOrNull()

    fun requestActiveGroup() = viewModelScope.launch {
        ApiRepository.getActiveGroup()
            .apiCollect { res ->
                if (res.data.isNullOrEmpty()) return@apiCollect
                _activeGroup.update { res.data }
            }
    }

    fun requestPopularPlugin() = viewModelScope.launch {
        ApiRepository.getPopularPlugin()
            .apiCollect { res ->
                if (res.data.isNullOrEmpty()) return@apiCollect
                _popularPlugin.update { res.data }
            }
    }

    fun openWebSocket() {
        mStatusWebSocketHolder.connect()
        mLogsWebSocketHolder.connect()
    }

    fun closeWebSocket() {
        mStatusWebSocketHolder.close()
        mLogsWebSocketHolder.close()
    }

    override fun onCleared() {
        mStatusWebSocketHolder.cancel()
        mLogsWebSocketHolder.cancel()
    }
}