package com.ivip.brainstormia.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivip.brainstormia.data.models.AIModel

@Composable
fun ModelSelectionDropdown(
    models: List<AIModel>,
    selectedModel: AIModel,
    onModelSelected: (AIModel) -> Unit,
    isDarkTheme: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableStateOf(0.dp) }
    val localDensity = LocalDensity.current

    // Cores atualizadas para o tema escuro
    val backgroundColor = if (isDarkTheme) Color(0xFF212121) else Color(0xFFF0F4F7)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val dropdownBackgroundColor = if (isDarkTheme) Color(0xFF333333) else Color.White

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .onGloballyPositioned { coordinates ->
                    containerWidth = with(localDensity) { coordinates.size.width.toDp() }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Modelo: ${selectedModel.displayName}",
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Selecionar modelo",
                    tint = textColor,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(containerWidth)
                .background(dropdownBackgroundColor)
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model.displayName,
                            color = textColor,
                            fontWeight = if (model.id == selectedModel.id) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}