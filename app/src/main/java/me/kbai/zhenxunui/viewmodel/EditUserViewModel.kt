package me.kbai.zhenxunui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.extends.apiCollect
import me.kbai.zhenxunui.model.ExecuteSql
import me.kbai.zhenxunui.model.UserDetail
import me.kbai.zhenxunui.repository.ApiRepository
import me.kbai.zhenxunui.repository.Resource

class EditUserViewModel : ViewModel() {
    private val _userDetail: MutableLiveData<Resource<UserDetail>> = MutableLiveData()
    val userDetail: LiveData<Resource<UserDetail>> = _userDetail

    fun requestPluginDetail(userId: String) = viewModelScope.launch {
        ApiRepository.getUserDetail(Constants.currentBot!!.selfId, userId)
            .apiCollect {
                _userDetail.value = it
            }
    }

    /**
     * 封禁状态通过exec_sql直写ban_console表, 服务端缓存5分钟自动同步或重启后立即生效
     */
    fun updateUser(userId: String, isBan: Boolean): Flow<Resource<Unit>> = flow {
        //QQ号纯数字, 校验后拼接防止sql注入
        val safeId = userId.filter { it.isDigit() }
        val delete = ApiRepository.executeSql(
            ExecuteSql("DELETE FROM ban_console WHERE user_id='$safeId' AND group_id IS NULL")
        ).first()
        if (delete.status != Resource.Status.SUCCESS) {
            emit(Resource.error(null, delete.message, delete.code, delete.httpStatusCode))
            return@flow
        }
        if (!isBan) {
            emit(Resource.success(Unit, delete.message, delete.code))
            return@flow
        }
        val banTime = System.currentTimeMillis() / 1000
        val insert = ApiRepository.executeSql(
            ExecuteSql(
                "INSERT INTO ban_console (user_id, group_id, ban_level, ban_time, " +
                    "ban_reason, duration, operator) VALUES " +
                    "('$safeId', NULL, 9, $banTime, 'webui', -1, 'webui')"
            )
        ).first()
        if (insert.status == Resource.Status.SUCCESS) {
            emit(Resource.success(Unit, insert.message, insert.code))
        } else {
            emit(Resource.error(null, insert.message, insert.code, insert.httpStatusCode))
        }
    }
}