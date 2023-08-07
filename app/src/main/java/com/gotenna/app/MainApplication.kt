package com.gotenna.app

import android.app.Application
import com.gotenna.radio.sdk.GotennaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {
    companion object {
        const val SDK_TOKEN = BuildConfig.SDK_TOKEN
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
        GotennaClient.initialize(applicationContext, SDK_TOKEN, preProcessAction = null, postProcessAction = null)
    }
}