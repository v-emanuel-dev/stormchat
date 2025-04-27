package com.ivip.brainstormia.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivip.brainstormia.data.models.AIModel

@Composable
fun ModelSelectionDropdown(
    models: List<AIModel>,
    selectedModel: AIModel,
    onModelSelected: (AIModel) -> Unit,
    isPremiumUser: Boolean,
    isDarkTheme: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Botão de seleção principal sem cantos arredondados
        Surface(
            onClick = { expanded = true },
            color = if (isDarkTheme) Color(0xFF2C2C2C) else Color(0xFFE0E0E0),
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedModel.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Expandir",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (expanded) {
            Dialog(
                onDismissRequest = { expanded = false },
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                // Fundo que ocupa a tela inteira
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (isDarkTheme) Color(0xCC121212) else Color(0xCCF5F5F5)
                ) {
                    // Conteúdo clicável que fecha o diálogo ao clicar fora
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { expanded = false },
                        contentAlignment = Alignment.Center
                    ) {
                        // Lista de modelos
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.7f)
                                // Previne que cliques na coluna se propaguem para o Box
                                .clickable(onClick = {}, enabled = false),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Cabeçalho
                            Text(
                                text = "Modelo",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDarkTheme) Color.White else Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Lista de modelos
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    models.forEach { model ->
                                        val isEnabled = !model.isPremium || isPremiumUser
                                        val textColor = when {
                                            !isEnabled -> if (isDarkTheme) Color.Gray else Color.LightGray
                                            model == selectedModel -> MaterialTheme.colorScheme.primary
                                            else -> if (isDarkTheme) Color.White else Color.Black
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp),
                                            color = if (model == selectedModel) {
                                                Color(0xFFFFC107) // Amarelo para o modelo selecionado
                                            } else {
                                                if (isDarkTheme) Color(0xFF2C2C2C) else Color(0xFFF5F5F5)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable(enabled = isEnabled) {
                                                        onModelSelected(model)
                                                        expanded = false
                                                    }
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = model.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (model.isPremium) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Star,
                                                        contentDescription = "Premium",
                                                        tint = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFFFD700),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Espaçamento mínimo entre itens
                                        if (model != models.last()) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}