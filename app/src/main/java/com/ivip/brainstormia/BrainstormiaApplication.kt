package com.ivip.brainstormia

import android.app.Application
import androidx.lifecycle.ViewModelProvider

class BrainstormiaApplication : Application() {
    // ViewModel para exportação, disponível globalmente
    var exportViewModel: ExportViewModel? = null

    // Adicionar referência global ao ChatViewModel
    var chatViewModel: ChatViewModel? = null

    override fun onCreate() {
        super.onCreate()

        // Inicializar o ExportViewModel
        exportViewModel = ViewModelProvider.AndroidViewModelFactory(this)
            .create(ExportViewModel::class.java)

        // Inicializar o ChatViewModel
        chatViewModel = ViewModelProvider.AndroidViewModelFactory(this)
            .create(ChatViewModel::class.java)
    }
}