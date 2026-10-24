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

sealed class ConnectLogState {
    data object Loading : ConnectLogState()
    data object Success : ConnectLogState()
    data object Empty : ConnectLogState()
    data object Error : ConnectLogState()
}

class ConnectLogViewModel : ViewModel() {

    private val _logs = MutableStateFlow<List<BotConnectLog>>(emptyList())
    val logs: StateFlow<List<BotConnectLog>> = _logs

    private val _state = MutableStateFlow<ConnectLogState>(ConnectLogState.Loading)
    val state: StateFlow<ConnectLogState> = _state

    fun requestConnectLogs(onError: (String) -> Unit = {}) {
        _state.value = ConnectLogState.Loading
        viewModelScope.launch {
            val res = ApiRepository.getConnectLog(GetConnectLog(index = 1, size = 100))
                .apiCollect()
            if (res.status != Resource.Status.SUCCESS) {
                _state.value = ConnectLogState.Error
                onError(res.message)
                return@launch
            }
            val list = res.data?.data.orEmpty()
            _logs.value = list
            _state.value = if (list.isEmpty()) ConnectLogState.Empty else ConnectLogState.Success
        }
    }
}
