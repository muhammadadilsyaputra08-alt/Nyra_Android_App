package com.tdpl.chat

import android.app.Application
import com.tdpl.chat.data.ModelDownloadManager

class TDPLApp : Application() {
    lateinit var modelManager: ModelDownloadManager
        private set

    override fun onCreate() {
        super.onCreate()
        modelManager = ModelDownloadManager(applicationContext)
    }
}
