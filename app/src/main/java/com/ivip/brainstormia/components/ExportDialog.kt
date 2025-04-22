package com.ivip.brainstormia.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ivip.brainstormia.ExportState
import com.ivip.brainstormia.theme.TextColorDark
import com.ivip.brainstormia.theme.TextColorLight

@Composable
fun ExportDialog(
    conversationTitle: String,
    exportState: ExportState,
    onExportConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDarkTheme: Boolean
) {
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val buttonTextColor = TextColorLight // Sempre branco para melhor visibilidade
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Exportar Conversa",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Você está prestes a exportar a conversa \"$conversationTitle\" para o Google Drive.",
                    color = textColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (exportState) {
                    is ExportState.Idle -> {
                        // Mostrar botões de confirmação e cancelamento
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = buttonTextColor
                                )
                            ) {
                                Text(
                                    text = "Cancelar",
                                    color = buttonTextColor
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(onClick = onExportConfirm) {
                                Text(
                                    text = "Exportar",
                                    color = buttonTextColor
                                )
                            }
                        }
                    }

                    is ExportState.Loading -> {
                        // Mostrar indicador de carregamento
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Exportando conversa...",
                            color = textColor
                        )
                    }

                    is ExportState.Success -> {
                        // Mostrar mensagem de sucesso e link para o arquivo
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Conversa exportada com sucesso!",
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                // Usar Intent explícito para abrir no app do Drive
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setPackage("com.google.android.apps.docs") // Pacote do Google Drive
                                        data = Uri.parse(exportState.webViewLink)
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback para navegador se o app Drive não estiver disponível
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(exportState.webViewLink))
                                    context.startActivity(browserIntent)
                                }
                            }
                        ) {
                            Text(
                                text = "Abrir no Google Drive",
                                color = buttonTextColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = buttonTextColor
                            )
                        ) {
                            Text(
                                text = "Fechar",
                                color = buttonTextColor
                            )
                        }
                    }

                    is ExportState.Error -> {
                        // Mostrar mensagem de erro
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Erro na exportação",
                            color = textColor,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = exportState.message,
                            color = textColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                contentColor = buttonTextColor
                            )
                        ) {
                            Text(
                                text = "Fechar",
                                color = buttonTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}