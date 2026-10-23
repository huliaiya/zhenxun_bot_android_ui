package me.kbai.zhenxunui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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

    private var botListJob: Job? = null

    fun requestBotList(botId: String? = null) {
        botListJob?.cancel()
        botListJob = viewModelScope.launch {
            var attempt = 0
            while (true) {
                //服务端只会初始化传入bot_id对应bot的实时数据, 不传则默认选中列表第一个
                val res = ApiRepository.getBotList(botId).apiCollect()

                if (res.status == Resource.Status.FAIL && res.httpStatusCode in 400..401) {
                    return@launch
                }

                if (res.status == Resource.Status.SUCCESS && !res.data.isNullOrEmpty()) {
                    val list = res.data!!
                    _botList.value = list
                    val selected = list.find { it.isSelect } ?: list.first()
                    _currentBot.value = selected
                    Constants.currentBot = selected
                    Constants.cacheBotList(list)
                    return@launch
                }

                attempt++
                if (attempt == 1) {
                    //接口卡住或bot断线时先用上次成功的数据顶住, 保证页面可用
                    val cached = Constants.loadCachedBotList()
                    if (cached.isNotEmpty() && _botList.value.isNullOrEmpty()) {
                        _botList.value = cached
                        val selected = cached.find { it.isSelect } ?: cached.first()
                        _currentBot.value = selected
                        Constants.currentBot = selected
                    }
                    GlobalToast.showToast(R.string.bot_list_retry_hint)
                } else if (attempt % 5 == 0) {
                    GlobalToast.showToast(R.string.bot_list_retry_hint)
                }
                delay(minOf(2000L * attempt, 10000L))
            }
        }
    }

    fun selectBot(id: String) {
        val bot = _botList.value?.find { it.selfId == id } ?: return
        if (_currentBot.value?.selfId == id) return

        _currentBot.value = bot
        Constants.currentBot = bot
        //切换后重新拉取该bot的基础信息, 否则连接时间/好友/群组数量都是默认值
        requestBotList(id)
    }
}