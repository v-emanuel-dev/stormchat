package com.ivip.brainstormia.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivip.brainstormia.theme.PrimaryColor
import com.ivip.brainstormia.theme.TextColorDark
import com.ivip.brainstormia.theme.TextColorLight

@Composable
fun ThemeSwitch(
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbPosition by animateFloatAsState(
        targetValue = if (isDarkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 300)
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .width(90.dp)  // Aumentado para dar mais espaço
            .clip(CircleShape)
            .background(
                if (isDarkTheme) Color(0xFF1A3A4A) else Color(0xFFE0E0E0)
            )
            .clickable { onThemeChanged(!isDarkTheme) },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track com ícones melhor posicionados
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Light icon - posicionado mais à esquerda
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LightMode,
                    contentDescription = "Light Theme",
                    tint = if (!isDarkTheme) PrimaryColor else Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Dark icon - posicionado mais à direita
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DarkMode,
                    contentDescription = "Dark Theme",
                    tint = if (isDarkTheme) Color.White else Color.Gray.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Thumb ajustado
        Box(
            modifier = Modifier
                .size(36.dp)
                .offset(x = (54 * thumbPosition).dp)
                .padding(3.dp)
                .clip(CircleShape)
                .background(if (isDarkTheme) PrimaryColor else Color.White)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = if (isDarkTheme) TextColorLight else TextColorDark,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}