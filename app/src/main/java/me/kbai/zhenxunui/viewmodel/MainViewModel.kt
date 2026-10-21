package me.kbai.zhenxunui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.R
import me.kbai.zhenxunui.extends.apiCollect
import me.kbai.zhenxunui.model.BotBaseInfo
import me.kbai.zhenxunui.repository.ApiRepository
import me.kbai.zhenxunui.repository.Resource
import me.kbai.zhenxunui.tool.GlobalToast

class MainViewModel : ViewModel() {
    private val _botList: MutableLiveData<List<BotBaseInfo>> = MutableLiveData()
    val botList: LiveData<List<BotBaseInfo>> = _botList

    private val _currentBot: MutableLiveData<BotBaseInfo> = MutableLiveData()
    val currentBot: LiveData<BotBaseInfo> = _currentBot

    fun requestBotList() = viewModelScope.launch {
        val maxAttempts = 4
        repeat(maxAttempts) { attempt ->
            val res = ApiRepository.getBotList().apiCollect()

            if (res.status == Resource.Status.SUCCESS && !res.data.isNullOrEmpty()) {
                val list = res.data!!
                _botList.value = list
                val selected = list.find { it.isSelect } ?: list.first()
                _currentBot.value = selected
                Constants.currentBot = selected
                return@launch
            }

            if (res.status == Resource.Status.FAIL && res.httpStatusCode in 400..401) {
                return@launch
            }

            if (attempt == maxAttempts - 1) {
                if (res.status == Resource.Status.FAIL && res.message.isNotBlank()) {
                    GlobalToast.showToast(res.message)
                } else {
                    GlobalToast.showToast(R.string.no_connected_bot)
                }
                return@launch
            }
            delay(2000L * (attempt + 1))
        }
    }

    fun selectBot(id: String) {
        val bot = _botList.value?.find { it.selfId == id } ?: return
        if (_currentBot.value == bot) return

        _currentBot.value = bot
        Constants.currentBot = bot
    }
}