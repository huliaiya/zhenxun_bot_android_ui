package me.kbai.zhenxunui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.extends.apiCollect
import me.kbai.zhenxunui.model.BotBlockModule
import me.kbai.zhenxunui.model.BotManageUpdate
import me.kbai.zhenxunui.repository.ApiRepository
import me.kbai.zhenxunui.repository.Resource

class ZxConsoleViewModel : ViewModel() {

    private val _blockModule = MutableStateFlow<Resource<BotBlockModule>?>(null)
    val blockModule: StateFlow<Resource<BotBlockModule>?> = _blockModule

    fun requestBlockModule(botId: String) = viewModelScope.launch {
        _blockModule.value = Resource.loading(null)
        val res = ApiRepository.getBotBlockModule(botId).apiCollect()
        _blockModule.value = res
    }

    fun changeBotStatus(botId: String, status: Boolean) = ApiRepository.changeBotStatus(botId, status)

    fun updateBotManage(update: BotManageUpdate) = ApiRepository.updateBotManage(update)
}
