package com.ivip.brainstormia.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect as Effect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivip.brainstormia.ExportState
import com.ivip.brainstormia.theme.SurfaceColor
import com.ivip.brainstormia.theme.SurfaceColorDark
import com.ivip.brainstormia.theme.TextColorDark
import com.ivip.brainstormia.theme.TextColorLight

@Composable
fun ExportDialog(
    conversationTitle: String,
    exportState: ExportState,
    onExportConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean = true
) {
    val exportGreenColor = Color(0xFF4CAF50)
    val context = LocalContext.current

    // Abre o arquivo exportado diretamente no app Google Drive
    Effect(exportState) {
        if (exportState is ExportState.Success) {
            try {
                // URI do arquivo no Drive
                val fileId = exportState.fileId.orEmpty()
                val driveUri = Uri.parse("https://drive.google.com/file/d/$fileId/view")
                val intent = Intent(Intent.ACTION_VIEW, driveUri)
                    .setPackage("com.google.android.apps.docs")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("ExportDialog", "Erro ao abrir o Drive: ${e.message}")
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (exportState !is ExportState.Loading) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Exportar Conversa",
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) TextColorLight else TextColorDark
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when (exportState) {
                    is ExportState.Initial -> {
                        Text(
                            text = "Deseja exportar esta conversa para o Google Drive?",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) TextColorLight else TextColorDark
                        )
                        Text(
                            text = "Título: $conversationTitle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = if (isDarkTheme) TextColorLight.copy(alpha = 0.8f) else TextColorDark.copy(alpha = 0.8f)
                        )
                    }
                    is ExportState.Loading -> {
                        CircularProgressIndicator(
                            color = exportGreenColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Exportando...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) TextColorLight else TextColorDark
                        )
                    }
                    is ExportState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Sucesso",
                            tint = exportGreenColor,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Conversa exportada com sucesso!",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) TextColorLight else TextColorDark
                        )

                        Text(
                            text = "Nome do arquivo: ${exportState.fileName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Normal,
                            color = if (isDarkTheme) TextColorLight.copy(alpha = 0.8f) else TextColorDark.copy(alpha = 0.8f)
                        )

                        OutlinedButton(
                            onClick = {
                                try {
                                    val fileId = exportState.fileId.orEmpty()
                                    val driveUri = Uri.parse("https://drive.google.com/file/d/$fileId/view")
                                    val intent = Intent(Intent.ACTION_VIEW, driveUri)
                                        .setPackage("com.google.android.apps.docs")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e("ExportDialog", "Erro ao abrir Drive: ${e.message}")
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = exportGreenColor
                            ),
                            border = BorderStroke(1.dp, exportGreenColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = exportGreenColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Abrir no Google Drive",
                                color = exportGreenColor
                            )
                        }
                    }
                    is ExportState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Erro",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Erro ao exportar: ${exportState.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) TextColorLight else TextColorDark
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (exportState) {
                is ExportState.Initial -> {
                    Button(
                        onClick = onExportConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = exportGreenColor
                        )
                    ) {
                        Text(
                            text = "Exportar",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is ExportState.Success, is ExportState.Error -> {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = exportGreenColor
                        )
                    ) {
                        Text(
                            text = "OK",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (exportState is ExportState.Initial) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancelar",
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) TextColorLight.copy(alpha = 0.8f) else Color.DarkGray
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = if (isDarkTheme) SurfaceColorDark else SurfaceColor,
        tonalElevation = if (isDarkTheme) 8.dp else 4.dp
    )
}