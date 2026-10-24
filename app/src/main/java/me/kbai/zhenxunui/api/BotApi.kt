package me.kbai.zhenxunui.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import me.kbai.zhenxunui.Constants
import me.kbai.zhenxunui.model.LoginInfo
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * @author Sean on 2023/5/31
 */
object BotApi {
    @Volatile
    lateinit var service: ApiService
        private set

    @Volatile
    var authorization: String = ""

    private val mOkHttpClient = createOkHttpClient()

    //独立客户端, 供token静默续期使用, 避免触发authenticator递归
    private val mBareClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mSchemeRegex = Regex(".+://")

    init {
        Constants.apiBaseUrl.observeForever { baseUrl ->
            if (baseUrl.isNullOrBlank()) return@observeForever
            service = createApiService(
                mOkHttpClient,
                Constants.gson,
                baseUrl + Constants.API_PREFIX
            )
        }
    }

    fun openWebSocket(path: String, listener: WebSocketListener): WebSocket? {
        return (Constants.apiBaseUrl.value ?: return null)
            .replaceFirst(mSchemeRegex, "ws://")
            .plus(Constants.WS_PREFIX + path)
            .let { Request.Builder().url(it).build() }
            .let { mOkHttpClient.newWebSocket(it, listener) }
    }

    private fun createOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain: Interceptor.Chain ->
            val request = chain.request().newBuilder()
                .apply {
                    if (authorization.isNotBlank()) header("Authorization", authorization)
                }
                .build()
            chain.proceed(request)
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            //token有效期仅30分钟, 收到401时用已保存的账号密码静默重登并重放请求
            .authenticator { _, response ->
                if (response.request.url.encodedPath.endsWith("/login")) {
                    return@authenticator null
                }
                var prior = response.priorResponse
                var retries = 0
                while (prior != null) {
                    retries++
                    prior = prior.priorResponse
                }
                //同一请求最多静默重登重放一次, 防止密码失效时循环
                if (retries >= 1) return@authenticator null
                val token = refreshAuthorizationBlocking() ?: return@authenticator null
                response.request.newBuilder()
                    .header("Authorization", token)
                    .build()
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun refreshAuthorizationBlocking(): String? {
        val baseUrl = Constants.apiBaseUrl.value ?: return null
        val username = Constants.getUsername()
        val password = Constants.getPassword()
        if (username.isBlank() || password.isNullOrEmpty()) return null
        return try {
            val body = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
            mBareClient.newCall(
                Request.Builder()
                    .url(baseUrl + Constants.API_PREFIX + "login")
                    .post(body)
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = resp.body?.string() ?: return@use null
                val type = object : TypeToken<ApiResponse<LoginInfo>>() {}.type
                val api = Constants.gson.fromJson<ApiResponse<LoginInfo>>(json, type)
                if (api?.success == true && api.data != null) {
                    "${api.data.tokenType} ${api.data.accessToken}"
                        .also { authorization = it }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createApiService(client: OkHttpClient, gson: Gson, baseUrl: String): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
            .create(ApiService::class.java)
}