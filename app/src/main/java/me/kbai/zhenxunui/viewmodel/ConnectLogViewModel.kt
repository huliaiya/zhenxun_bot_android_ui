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

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private var mIndex = 0
    private var mLoading = false

    fun requestNextPage(onError: (String) -> Unit = {}) {
        if (mLoading || !_hasMore.value) return
        mLoading = true
        viewModelScope.launch {
            val res = ApiRepository.getConnectLog(GetConnectLog(mIndex + 1, PAGE_SIZE))
                .apiCollect()
            mLoading = false
            if (res.status != Resource.Status.SUCCESS) {
                onError(res.message)
                return@launch
            }
            val list = res.data?.data.orEmpty()
            if (list.isEmpty()) {
                _hasMore.value = false
                return@launch
            }
            mIndex++
            _logs.value = _logs.value + list
            if (list.size < PAGE_SIZE) _hasMore.value = false
        }
    }

    companion object {
        const val PAGE_SIZE = 15
    }
}
