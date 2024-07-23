package com.gotenna.app

import android.app.Application
import com.gotenna.radio.sdk.GotennaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {
    companion object {
        const val SDK_TOKEN = BuildConfig.SDK_TOKEN
        const val APP_ID = BuildConfig.APP_ID
    }

    private val backgroundScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        initGotennaClient()
    }

    fun preProcess(bytes: ByteArray): ByteArray {
        return bytes
    }

    fun postProcess(bytes: ByteArray): ByteArray {
        return bytes
    }

    private fun initGotennaClient() = backgroundScope.launch {
        GotennaClient.initialize(context = applicationContext, sdkToken = SDK_TOKEN, appId = APP_ID, preProcessAction = null, postProcessAction = null)
    }
}