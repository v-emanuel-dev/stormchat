package com.ivip.brainstormia

import android.app.Application
import android.util.Log

class BrainstormiaApplication : Application() {
    // ViewModels that need to be accessed globally
    var exportViewModel: ExportViewModel? = null
    var chatViewModel: ChatViewModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("BrainstormiaApp", "BrainstormiaApplication onCreate called")
    }
}