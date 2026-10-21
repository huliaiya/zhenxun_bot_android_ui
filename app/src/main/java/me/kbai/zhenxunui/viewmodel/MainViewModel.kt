package me.kbai.zhenxunui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        ApiRepository.getBotList()
            .apiCollect { res ->
                if (res.status == Resource.Status.LOADING) return@apiCollect

                val list = res.data
                if (list.isNullOrEmpty()) {
                    if (res.status == Resource.Status.FAIL) {
                        GlobalToast.showToast(res.message)
                    } else {
                        GlobalToast.showToast(R.string.no_connected_bot)
                    }
                    return@apiCollect
                }

                _botList.value = list
                val selected = list.find { it.isSelect } ?: list.first()
                _currentBot.value = selected
                Constants.currentBot = selected
            }
    }

    fun selectBot(id: String) {
        val bot = _botList.value?.find { it.selfId == id } ?: return
        if (_currentBot.value == bot) return

        _currentBot.value = bot
        Constants.currentBot = bot
    }
}