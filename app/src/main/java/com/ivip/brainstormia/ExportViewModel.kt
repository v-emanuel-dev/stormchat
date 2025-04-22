package com.ivip.brainstormia

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.ivip.brainstormia.services.DriveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ExportViewModel"

    // Instância do serviço do Drive
    private val driveService = DriveService(application.applicationContext)

    // Estados para controlar o fluxo de exportação
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    // Preparar o serviço do Drive com a conta do usuário
    fun setupDriveService() {
        val account = GoogleSignIn.getLastSignedInAccount(getApplication())
        if (account != null) {
            driveService.setupDriveService(account.email ?: "")
            Log.d(TAG, "Drive Service configurado com a conta: ${account.email}")
        } else {
            Log.e(TAG, "Nenhuma conta Google encontrada")
            _exportState.value = ExportState.Error("Você precisa estar conectado com uma conta Google")
        }
    }

    // Exportar uma conversa para o Google Drive
    fun exportConversation(conversationId: Long, title: String, messages: List<ChatMessage>) {
        if (!driveService.isDriveServiceInitialized()) {
            setupDriveService()
            if (!driveService.isDriveServiceInitialized()) {
                _exportState.value = ExportState.Error("Não foi possível inicializar o serviço do Drive")
                return
            }
        }

        _exportState.value = ExportState.Loading

        val formattedContent = driveService.formatConversationForExport(messages)

        viewModelScope.launch {
            driveService.exportConversation(
                title = title,
                content = formattedContent,
                onSuccess = { fileId, webViewLink ->
                    _exportState.value = ExportState.Success(fileId, webViewLink)
                    Log.d(TAG, "Conversa exportada com sucesso. Link: $webViewLink")
                },
                onFailure = { exception ->
                    _exportState.value = ExportState.Error(exception.message ?: "Erro desconhecido")
                    Log.e(TAG, "Erro ao exportar conversa: ${exception.message}")
                }
            )
        }
    }

    // Reset do estado de exportação
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
}

// Estados possíveis durante o processo de exportação
sealed class ExportState {
    object Idle : ExportState()
    object Loading : ExportState()
    data class Success(val fileId: String, val webViewLink: String) : ExportState()
    data class Error(val message: String) : ExportState()
}