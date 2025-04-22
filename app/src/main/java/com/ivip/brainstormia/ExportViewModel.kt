package com.ivip.brainstormia

import android.content.ContentValues
import android.content.Context
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
    data class Success(val fileId: String? = null, val fileUrl: String? = null) : ExportState()
    data class Error(val message: String) : ExportState()
}

class ExportViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Initial)
    val exportState: StateFlow<ExportState> = _exportState

    private var driveService: Drive? = null

    // Setup Google Drive service
    fun setupDriveService() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val googleAccount = GoogleSignIn.getLastSignedInAccount(getApplication())
                if (googleAccount == null) {
                    Log.w("ExportViewModel", "No Google account found")
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

                Log.d("ExportViewModel", "Drive service set up successfully")
            } catch (e: Exception) {
                Log.e("ExportViewModel", "Error setting up Drive service", e)
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

        viewModelScope.launch {
            try {
                // Format conversation as text
                val conversationText = formatConversationAsText(messages)

                // Create filename with the required prefix
                val fileName = "Brainstormia-${sanitizeFileName(title)}.txt"

                // Check if Drive service is available
                val drive = driveService
                if (drive != null) {
                    // Export to Google Drive
                    exportToDrive(drive, fileName, conversationText)
                } else {
                    // Fallback to local storage if Drive is not available
                    exportToLocalStorage(fileName, conversationText)
                }
            } catch (e: Exception) {
                Log.e("ExportViewModel", "Error exporting conversation", e)
                _exportState.value = ExportState.Error("Erro ao exportar: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun exportToDrive(drive: Drive, fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("ExportViewModel", "Creating file in Google Drive: $fileName")

                // Create file metadata
                val fileMetadata = DriveFile().apply {
                    name = fileName
                    mimeType = "text/plain"
                }

                // Create file content
                val contentStream = ByteArrayContent.fromString("text/plain", content)

                // Create the file in Drive
                val file = drive.files().create(fileMetadata, contentStream)
                    .setFields("id,webViewLink")
                    .execute()

                Log.d("ExportViewModel", "File created in Drive with ID: ${file.id}")

                // Update state with success
                withContext(Dispatchers.Main) {
                    _exportState.value = ExportState.Success(
                        fileId = file.id,
                        fileUrl = file.webViewLink
                    )
                }
            } catch (e: Exception) {
                Log.e("ExportViewModel", "Error creating file in Drive", e)
                throw e
            }
        }
    }

    private suspend fun exportToLocalStorage(fileName: String, content: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("ExportViewModel", "Exporting to local storage: $fileName")
                val context = getApplication<android.app.Application>()

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

                    Log.d("ExportViewModel", "File saved to local storage: $uri")

                    withContext(Dispatchers.Main) {
                        _exportState.value = ExportState.Success()
                    }
                } else {
                    throw Exception("Não foi possível criar o arquivo local")
                }
            } catch (e: Exception) {
                Log.e("ExportViewModel", "Error exporting to local storage", e)
                throw e
            }
        }
    }

    private fun formatConversationAsText(messages: List<ChatMessage>): String {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val currentDate = dateFormat.format(Date())

        return buildString {
            appendLine("Conversa Brainstormia")
            appendLine("Exportada em: $currentDate")
            appendLine("-".repeat(50))
            appendLine()

            messages.forEach { message ->
                val prefix = when (message.sender) {
                    Sender.USER -> "Você: "
                    Sender.BOT -> "Brainstormia: "
                }
                appendLine(prefix + message.text)
                appendLine()
            }
        }
    }

    // Helper function to sanitize filenames
    private fun sanitizeFileName(name: String): String {
        // Remove invalid characters for filenames
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .takeIf { it.isNotEmpty() } ?: "Conversa"
    }
}