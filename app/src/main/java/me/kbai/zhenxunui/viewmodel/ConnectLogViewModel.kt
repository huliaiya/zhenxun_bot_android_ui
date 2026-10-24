package me.kbai.zhenxunui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.extends.apiCollect
import me.kbai.zhenxunui.model.BotConnectLog
import me.kbai.zhenxunui.model.GetConnectLog
import me.kbai.zhenxunui.repository.ApiRepository
import me.kbai.zhenxunui.repository.Resource

class ConnectLogViewModel : ViewModel() {

    private val _logs = MutableStateFlow<List<BotConnectLog>>(emptyList())
    val logs: StateFlow<List<BotConnectLog>> = _logs

    fun requestConnectLogs(onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            val res = ApiRepository.getConnectLog(GetConnectLog(index = 1, size = 100))
                .apiCollect()
            if (res.status != Resource.Status.SUCCESS) {
                onError(res.message)
                return@launch
            }
            _logs.value = res.data?.data.orEmpty()
        }
    }
}
