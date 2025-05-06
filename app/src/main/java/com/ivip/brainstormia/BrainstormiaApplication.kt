package com.ivip.brainstormia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.ivip.brainstormia.billing.BillingViewModel

/**
 * Classe de aplicação que gerencia ViewModels e recursos globais.
 * Implementa o padrão Singleton para ViewModels que precisam de uma única
 * instância em toda a aplicação.
 */
class BrainstormiaApplication : Application() {

    // ViewModels globais com getters públicos
    var chatViewModel: ChatViewModel? = null
        internal set // Permite escrita apenas dentro do mesmo módulo

    var exportViewModel: ExportViewModel? = null
        internal set // Permite escrita apenas dentro do mesmo módulo

    // BillingViewModel como singleton verdadeiro
    val billingViewModel by lazy {
        Log.d("BrainstormiaApp", "Inicializando BillingViewModel singleton")
        BillingViewModel.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Inicializar FirebaseApp
        FirebaseApp.initializeApp(this)

        // Configurar Analytics e Crashlytics
        FirebaseAnalytics.getInstance(this)
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        // Criar canal de notificação para Android 8.0+
        createNotificationChannel()

        // Inicializar ViewModels globais
        initializeViewModels()

        // Obter token FCM para notificações
        setupNotifications()

        // Garantir que o BillingViewModel seja inicializado apenas uma vez
        // A chamada à propriedade lazy é suficiente
        val billing = billingViewModel
        Log.d("BrainstormiaApp", "BillingViewModel inicializado: $billing")
    }

    /**
     * Cria canal de notificação para Android 8.0 (Oreo) e superior.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notificações Brainstormia"
            val descriptionText = "Notificações do aplicativo Brainstormia"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }

            // Registrar o canal com o sistema
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d("BrainstormiaApp", "Canal de notificação criado: $CHANNEL_ID")
        }
    }

    /**
     * Inicializa os ViewModels globais.
     */
    private fun initializeViewModels() {
        try {
            // Inicializar o ChatViewModel se ainda não existe
            if (chatViewModel == null) {
                chatViewModel = ChatViewModel(this)
                Log.d("BrainstormiaApp", "ChatViewModel inicializado")
            }

            // Inicializar o ExportViewModel se ainda não existe
            if (exportViewModel == null) {
                exportViewModel = ExportViewModel(this)
                Log.d("BrainstormiaApp", "ExportViewModel inicializado")
            }
        } catch (e: Exception) {
            Log.e("BrainstormiaApp", "Erro ao inicializar ViewModels", e)
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    /**
     * Configura as notificações e registra o token FCM.
     */
    private fun setupNotifications() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Log.d("FCM_TOKEN", "Token FCM obtido: $token")

                    // Salvar token localmente
                    val prefs = getSharedPreferences("brainstormia_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("fcm_token", token).apply()

                    // Aqui você pode enviar o token para seu servidor se necessário
                } else {
                    Log.e("FCM_TOKEN", "Falha ao obter token FCM: ${task.exception}")
                }
            }
        } catch (e: Exception) {
            Log.e("BrainstormiaApp", "Erro ao configurar notificações", e)
        }
    }

    /**
     * Isso pode ser chamado de qualquer lugar da aplicação.
     */
    fun handleSubscriptionCancellationNotification() {
        Log.d("BrainstormiaApp", "Processando notificação de cancelamento de assinatura")
        billingViewModel.checkForCancellation()
    }

    companion object {
        // ID do canal de notificação para Android 8.0+
        const val CHANNEL_ID = "brainstormia_notification_channel"

        // IDs de tipos de notificação
        const val NOTIFICATION_TYPE_CHAT = "chat"
        const val NOTIFICATION_TYPE_SUBSCRIPTION = "subscription"
    }
}