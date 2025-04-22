package com.ivip.brainstormia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainstormia.ConversationType
import com.ivip.brainstormia.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppDrawerContent(
    conversationDisplayItems: List<ConversationDisplayItem>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteConversationRequest: (Long) -> Unit,
    onRenameConversationRequest: (Long) -> Unit,
    onExportConversationRequest: (Long) -> Unit,
    isDarkTheme: Boolean
) {
    val backgroundColor = if (isDarkTheme) BackgroundColorDark else BackgroundColor
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val drawerItemColor = if (isDarkTheme) SurfaceColorDark else SurfaceColor
    val selectedItemColor = if (isDarkTheme) PrimaryColor.copy(alpha = 0.2f) else PrimaryColor.copy(alpha = 0.1f)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(backgroundColor)
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        // Header
        Text(
            text = "Conversas",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // Nova conversa button
        Button(
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Nova conversa",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Nova conversa",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider(
            color = if (isDarkTheme) Color.DarkGray.copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Lista de conversas - Adicionamos uma key para forçar recomposição
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = rememberLazyListState() // Adicionar um estado explícito
        ) {
            // Em vez de usar key(), usamos items com o parâmetro key
            items(
                items = conversationDisplayItems.sortedByDescending { it.lastTimestamp },
                key = { it.id } // Garantir que cada item tenha uma chave única
            ) { item ->
                val isSelected = item.id == currentConversationId
                val itemBackgroundColor = if (isSelected) selectedItemColor else Color.Transparent

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(itemBackgroundColor)
                                .clickable { onConversationClick(item.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ícone com cor baseada no tipo de conversa
                            val (iconVector, iconTint) = getConversationIcon(item.conversationType, isDarkTheme)
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.displayTitle,
                                    color = textColor,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 15.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                                        .format(Date(item.lastTimestamp)),
                                    color = textColor.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }

                            // Ações em um espaço mais compacto
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp) // Diminuir espaçamento
                            ) {
                                // Ícones menores e mais compactos
                                IconButton(
                                    onClick = { onRenameConversationRequest(item.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Renomear conversa",
                                        tint = iconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onExportConversationRequest(item.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Upload,
                                        contentDescription = "Exportar conversa",
                                        tint = iconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteConversationRequest(item.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir conversa",
                                        tint = iconTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

// Função para determinar o ícone baseado no tipo de conversa
@Composable
private fun getConversationIcon(type: ConversationType, isDarkTheme: Boolean): Pair<ImageVector, Color> {
    return when (type) {
        ConversationType.GENERAL -> Pair(
            Icons.Default.Chat,
            if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1976D2)
        )
        ConversationType.PERSONAL -> Pair(
            Icons.Default.Person,
            if (isDarkTheme) Color(0xFFFFCC80) else Color(0xFFEF6C00)
        )
        ConversationType.EMOTIONAL -> Pair(
            Icons.Default.Favorite,
            if (isDarkTheme) Color(0xFFEF9A9A) else Color(0xFFD32F2F)
        )
        ConversationType.THERAPEUTIC -> Pair(
            Icons.Default.Healing,
            if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF388E3C)
        )
        ConversationType.HIGHLIGHTED -> Pair(
            Icons.Default.StarRate,
            if (isDarkTheme) Color(0xFFFFE082) else Color(0xFFFFC107)
        )
    }
}

// Extensão para aplicar alpha à cor
private fun Modifier.alpha(alpha: Float): Modifier {
    return this.graphicsLayer(alpha = alpha)
}