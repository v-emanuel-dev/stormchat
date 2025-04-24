package com.ivip.brainstormia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log

/**
 * Application-class global do Brainstormia.
 *  • Mantém instâncias globais dos ViewModels que precisam sobreviver ao processo.
 *  • Garante que o canal de notificação padrão seja criado antes de qualquer FCM chegar.
 */
class BrainstormiaApplication : Application() {

    // ViewModels acessados globalmente em todo o app
    var exportViewModel: ExportViewModel? = null
    var chatViewModel: ChatViewModel?   = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "BrainstormiaApplication onCreate called")

        createNotificationChannel()
    }

    /** Cria (uma vez) o canal usado pelo Firebase Cloud Messaging e outras notificações. */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notificações do Brainstormia",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para mensagens push e lembretes."
                enableLights(true)
                lightColor = android.graphics.Color.YELLOW
                enableVibration(true)
                setShowBadge(true)
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
            Log.d(TAG, "NotificationChannel \"$CHANNEL_ID\" criado/atualizado")
        }
    }

    companion object {
        /** Mesmo ID usado em MyFirebaseService.kt ※ mantenha em sincronia. */
        const val CHANNEL_ID = "brainstormia_default"
        private const val TAG = "BrainstormiaApp"
    }
}