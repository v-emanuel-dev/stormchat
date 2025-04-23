package com.ivip.brainstormia.components

import androidx.compose.foundation.BorderStroke
import com.ivip.brainstormia.data.models.AIModel
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivip.brainstormia.theme.PrimaryColor
import com.ivip.brainstormia.theme.TextColorDark
import com.ivip.brainstormia.theme.TextColorLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionDropdown(
    models: List<AIModel>,
    selectedModel: AIModel,
    onModelSelected: (AIModel) -> Unit,
    isDarkTheme: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(
                containerColor = Color.Transparent,
                contentColor = textColor
            ),
            border = BorderStroke(1.dp, Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Modelo de IA",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.Medium
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.menuAnchor()
                    ) {
                        Text(
                            text = selectedModel.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Fechar seleção de modelo" else "Abrir seleção de modelo",
                            tint = PrimaryColor
                        )
                    }

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = model.displayName,
                                        color = if (model.id == selectedModel.id) PrimaryColor else textColor,
                                        fontWeight = if (model.id == selectedModel.id) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onModelSelected(model)
                                    expanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = textColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}