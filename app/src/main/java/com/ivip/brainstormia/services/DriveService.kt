package com.ivip.brainstormia.services

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.ivip.brainstormia.ChatMessage
import com.ivip.brainstormia.Sender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Collections

class DriveService(private val context: Context) {
    private val TAG = "DriveService"

    // Scopes necessários para a API do Drive
    private val SCOPES = Collections.singleton(DriveScopes.DRIVE_FILE)

    // Nome da pasta para organizar os arquivos
    private val FOLDER_NAME = "Brainstormia"

    // ID da pasta no Google Drive (preenchido quando a pasta for encontrada ou criada)
    private var folderIdCache: String? = null

    // Instância do serviço Google Drive
    private var driveService: Drive? = null

    // Inicializa o serviço usando a conta Google autenticada
    fun setupDriveService(userAccount: String) {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, SCOPES
            ).setSelectedAccountName(userAccount)

            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("Brainstormia")
                .build()

            Log.d(TAG, "Drive Service inicializado com sucesso.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar Drive Service: ${e.message}")
            driveService = null
        }
    }

    // Verifica se o serviço está pronto para uso
    fun isDriveServiceInitialized(): Boolean {
        return driveService != null
    }

    // Verifica se a pasta "Brainstormia" existe, e a cria se não existir
    private suspend fun getFolderId(): String? {
        if (folderIdCache != null) {
            return folderIdCache
        }

        return withContext(Dispatchers.IO) {
            try {
                val query = "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                val result = driveService!!.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .execute()

                // Verificar se a pasta já existe
                if (result.files.isNotEmpty()) {
                    val folderId = result.files[0].id
                    Log.d(TAG, "Pasta $FOLDER_NAME encontrada. ID: $folderId")
                    folderIdCache = folderId
                    return@withContext folderId
                }

                // Se não existe, criar nova pasta
                val folderMetadata = File()
                folderMetadata.name = FOLDER_NAME
                folderMetadata.mimeType = "application/vnd.google-apps.folder"

                val folder = driveService!!.files().create(folderMetadata)
                    .setFields("id")
                    .execute()

                val newFolderId = folder.id
                Log.d(TAG, "Nova pasta $FOLDER_NAME criada. ID: $newFolderId")
                folderIdCache = newFolderId
                return@withContext newFolderId
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter/criar pasta: ${e.message}")
                null
            }
        }
    }

    // Exporta uma conversa como arquivo de texto para a pasta "Brainstormia" no Google Drive
    suspend fun exportConversation(
        title: String,
        content: String,
        onSuccess: (fileId: String, webViewLink: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (driveService == null) {
            onFailure(Exception("Drive Service não inicializado"))
            return
        }

        return withContext(Dispatchers.IO) {
            try {
                // Primeiro, verifica/cria a pasta Brainstormia
                val folderId = getFolderId()
                if (folderId == null) {
                    throw Exception("Não foi possível criar ou acessar a pasta Brainstormia")
                }

                // Preparando os metadados do arquivo
                val fileMetadata = File()
                fileMetadata.name = "$title.txt"
                fileMetadata.mimeType = "text/plain"
                fileMetadata.parents = listOf(folderId) // Define a pasta pai

                // Convertendo o conteúdo para bytes
                val contentBytes = content.toByteArray(Charsets.UTF_8)
                val mediaContent = ByteArrayContent.fromString("text/plain", content)

                // Enviando para o Google Drive
                val file = driveService!!.files().create(fileMetadata, mediaContent)
                    .setFields("id,webViewLink")
                    .execute()

                Log.d(TAG, "Arquivo exportado com ID: ${file.id}")

                // Configurando permissão para qualquer pessoa com o link poder ler
                val permission = com.google.api.services.drive.model.Permission()
                    .setType("anyone")
                    .setRole("reader")

                driveService!!.permissions().create(file.id, permission).execute()

                withContext(Dispatchers.Main) {
                    onSuccess(file.id, file.webViewLink)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao exportar conversa: ${e.message}")
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    // Formata conversa para exportação
    fun formatConversationForExport(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.appendLine("# Conversa do Brainstormia")
        sb.appendLine("Data: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date())}")
        sb.appendLine("-".repeat(40))
        sb.appendLine()

        messages.forEach { message ->
            val sender = if (message.sender == Sender.USER) "Você" else "Brainstormia"
            sb.appendLine("[$sender]:")
            sb.appendLine(message.text)
            sb.appendLine()
        }

        return sb.toString()
    }
}