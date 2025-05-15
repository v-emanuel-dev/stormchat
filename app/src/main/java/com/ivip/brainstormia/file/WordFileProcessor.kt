package com.ivip.brainstormia.file

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Simplified implementation for Word files processing.
 * This version doesn't use Apache POI due to API compatibility issues.
 */
class WordFileProcessor : FileProcessor {
    override fun canProcess(mimeType: String): Boolean {
        return mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || // docx
                mimeType == "application/msword" // doc
    }

    override suspend fun processFile(file: File, mimeType: String, context: Context): String =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // Simplified version that doesn't use Apache POI
                val fileSize = file.length()

                val description = StringBuilder()
                description.append("Documento Word: ${file.name}\n")
                description.append("Tipo: $mimeType\n")
                description.append("Tamanho: ${formatFileSize(fileSize)}\n\n")
                description.append("Nota: A extração do conteúdo de Word não está disponível nesta versão do aplicativo. ")
                description.append("O aplicativo precisa de Android 8.0 (API 26) ou superior para processar documentos Word.")

                description.toString()
            } catch (e: Exception) {
                Log.e("WordProcessor", "Erro ao processar arquivo Word", e)
                "Erro ao extrair texto do documento Word: ${e.message}"
            }
        }

    override suspend fun processUri(uri: Uri, mimeType: String, context: Context): String =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // Simplified version that doesn't use Apache POI
                var fileSize = 0L
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex("_size")
                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }

                val description = StringBuilder()
                description.append("Documento Word da URI: ${uri.lastPathSegment ?: "desconhecido"}\n")
                description.append("Tipo: $mimeType\n")
                description.append("Tamanho: ${formatFileSize(fileSize)}\n\n")
                description.append("Nota: A extração do conteúdo de Word não está disponível nesta versão do aplicativo. ")
                description.append("O aplicativo precisa de Android 8.0 (API 26) ou superior para processar documentos Word.")

                description.toString()
            } catch (e: Exception) {
                Log.e("WordProcessor", "Erro ao processar arquivo Word da URI", e)
                "Erro ao extrair texto do documento Word: ${e.message}"
            }
        }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}