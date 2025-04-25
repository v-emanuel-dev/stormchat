package com.ivip.brainstormia.components

import androidx.compose.foundation.BorderStroke
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
import com.ivip.brainstormia.data.models.AIModel
import com.ivip.brainstormia.theme.PrimaryColor
import com.ivip.brainstormia.theme.SurfaceColor
import com.ivip.brainstormia.theme.SurfaceColorDark
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

    // Cores ajustadas para cada tema
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val dropdownBackgroundColor = if (isDarkTheme) SurfaceColorDark else SurfaceColor

    // Cor da borda - azul para tema claro, cinza para tema escuro
    val borderColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.3f) else PrimaryColor.copy(alpha = 0.6f)
    // Espessura da borda - mais grossa no tema claro
    val borderWidth = if (isDarkTheme) 1.dp else 1.5.dp

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
            border = BorderStroke(borderWidth, borderColor)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedModel.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
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
                    onDismissRequest = { expanded = false },
                    containerColor = dropdownBackgroundColor
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