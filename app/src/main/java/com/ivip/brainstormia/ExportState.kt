package com.ivip.brainstormia

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

sealed class ExportState {
    object Initial : ExportState()
    object Loading : ExportState()
    data class Success(val fileId: String? = null, val fileName: String = "") : ExportState()
    data class Error(val message: String) : ExportState()
}

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Initial)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private var driveService: Drive? = null
    private val tag = "ExportViewModel"

    fun setupDriveService() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(getApplication())
                if (googleAccount == null) {
                    Log.w(tag, "Nenhuma conta Google encontrada")
                    return@launch
                }

                val credential = GoogleAccountCredential.usingOAuth2(
                    getApplication(),
                    Collections.singleton(DriveScopes.DRIVE_FILE)
                )
                credential.selectedAccount = googleAccount.account

                driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                )
                    .setApplicationName("Brainstormia")
                    .build()

                Log.d(tag, "Serviço do Drive configurado com sucesso")
            } catch (e: Exception) {
                Log.e(tag, "Erro ao configurar o serviço do Drive", e)
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Initial
    }

    fun exportConversation(conversationId: Long, title: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) {
            _exportState.value = ExportState.Error("Não há mensagens para exportar")
            return
        }

        _exportState.value = ExportState.Loading
        Log.d(tag, "Iniciando exportação para conversa: $conversationId, título: $title")

        viewModelScope.launch {
            try {
                // Formatar data e hora atual para o nome do arquivo
                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val dateTime = dateFormat.format(Date())

                // Criar um nome de arquivo baseado no título da conversa
                val sanitizedTitle = sanitizeFileName(title)
                val fileName = "Brainstormia_${sanitizedTitle}_$dateTime.txt"
                Log.d(tag, "Nome do arquivo preparado: $fileName")

                // Converter mensagens para texto formatado
                val fileContent = formatConversationAsText(messages)

                // Verificar se há conteúdo para exportar
                if (fileContent.isBlank()) {
                    _exportState.value = ExportState.Error("Não há conteúdo para exportar")
                    return@launch
                }

                // Verificar se o Drive está disponível
                val drive = driveService
                if (drive != null) {
                    // Upload do arquivo para o Google Drive
                    exportToDrive(drive, fileName, fileContent)
                } else {
                    // Fallback para armazenamento local se o Drive não estiver disponível
                    exportToLocalStorage(fileName, fileContent)
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao exportar conversa", e)
                _exportState.value = ExportState.Error("Falha na exportação: ${e.localizedMessage ?: "Erro desconhecido"}")
            }
        }
    }

    private suspend fun exportToDrive(drive: Drive, fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Criando arquivo no Google Drive: $fileName")

                // Criar metadados do arquivo
                val fileMetadata = DriveFile().apply {
                    name = fileName
                    mimeType = "text/plain"
                }

                // Criar conteúdo do arquivo
                val contentStream = ByteArrayContent.fromString("text/plain", content)

                // Criar o arquivo no Drive
                val file = drive.files().create(fileMetadata, contentStream)
                    .setFields("id,webViewLink")
                    .execute()

                Log.d(tag, "Arquivo criado no Drive com ID: ${file.id}")

                // Configurar permissões para que o arquivo seja acessível
                try {
                    val permission = com.google.api.services.drive.model.Permission()
                        .setType("user")
                        .setRole("writer")
                        .setEmailAddress(GoogleSignIn.getLastSignedInAccount(getApplication())?.email)

                    drive.permissions().create(file.id, permission)
                        .setFields("id")
                        .execute()

                    Log.d(tag, "Permissões definidas para o arquivo: ${file.id}")
                } catch (e: Exception) {
                    Log.e(tag, "Erro ao configurar permissões do arquivo", e)
                    // Continuar mesmo se as permissões falharem
                }

                // Atualizar estado com sucesso
                withContext(Dispatchers.Main) {
                    _exportState.value = ExportState.Success(
                        fileId = file.id,
                        fileName = fileName
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao criar arquivo no Drive", e)
                throw e
            }
        }
    }

    private suspend fun exportToLocalStorage(fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Exportando para armazenamento local: $fileName")
                val context = getApplication<Application>()

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                }

                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Files.getContentUri("external"), values
                )

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output: OutputStream ->
                        output.write(content.toByteArray())
                    }

                    Log.d(tag, "Arquivo salvo no armazenamento local: $uri")

                    withContext(Dispatchers.Main) {
                        _exportState.value = ExportState.Success(fileName = fileName)
                    }
                } else {
                    throw Exception("Não foi possível criar o arquivo local")
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao exportar para armazenamento local", e)
                throw e
            }
        }
    }

    private fun formatConversationAsText(messages: List<ChatMessage>): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        return buildString {
            appendLine("=== CONVERSA EXPORTADA DO BRAINSTORMIA ===")
            appendLine("Data: $currentDate")
            appendLine("Total de mensagens: ${messages.size}")
            appendLine("=====================================")
            appendLine()

            messages.forEachIndexed { index, message ->
                val sender = when (message.sender) {
                    Sender.USER -> "Você"
                    Sender.BOT -> "Brainstormia"
                    else -> "Desconhecido"
                }

                appendLine("[$sender]:")
                appendLine(message.text)

                // Adicionar separador entre mensagens (exceto para a última)
                if (index < messages.size - 1) {
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
            }

            // Log do tamanho do conteúdo para depuração
            val content = toString()
            Log.d(tag, "Conteúdo formatado: ${content.length} caracteres")
        }
    }

    // Função auxiliar para sanitizar nomes de arquivos
    private fun sanitizeFileName(name: String): String {
        // Remover caracteres inválidos para nomes de arquivos
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("\\s+".toRegex(), "_")
            .take(30)
            .trim()
            .takeIf { it.isNotEmpty() } ?: "Conversa"
    }
}