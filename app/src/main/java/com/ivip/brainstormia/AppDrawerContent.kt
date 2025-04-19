package com.ivip.brainstormia

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import com.ivip.brainstormia.theme.*


@Composable
fun AppDrawerContent(
    conversationDisplayItems: List<ConversationDisplayItem>,
    currentConversationId: Long?,
    onConversationClick: (Long) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteConversationRequest: (Long) -> Unit,
    onRenameConversationRequest: (Long) -> Unit,
    isDarkTheme: Boolean = true
) {
    val surfaceColor = if (isDarkTheme) SurfaceColorDark else SurfaceColor
    val secondaryTextColor = if (isDarkTheme) TextColorLight.copy(alpha = 0.7f) else Color.DarkGray
    val dividerColor = if (isDarkTheme) Color.White.copy(alpha = 0.2f) else Color.LightGray.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp),
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PrimaryColor.copy(alpha = if (isDarkTheme) 0.2f else 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bolt_foreground),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Divider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = dividerColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            NewChatButton(
                onClick = onNewChatClick,
                isSelected = currentConversationId == null || currentConversationId == NEW_CONVERSATION_ID,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (conversationDisplayItems.isNotEmpty()) {
                Text(
                    text = "Suas conversas",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(conversationDisplayItems) { item ->
                        ConversationItem(
                            item = item,
                            isSelected = currentConversationId == item.id,
                            onClick = { onConversationClick(item.id) },
                            onRenameClick = { onRenameConversationRequest(item.id) },
                            onDeleteClick = { onDeleteConversationRequest(item.id) },
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Nenhuma conversa salva",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Suas conversas aparecerão aqui",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryTextColor.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Versão 1.0",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun NewChatButton(
    onClick: () -> Unit,
    isSelected: Boolean,
    isDarkTheme: Boolean = true
) {
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val selectedBgColor = if (isDarkTheme) PrimaryColor.copy(alpha = 0.25f) else PrimaryColor.copy(alpha = 0.15f)
    val unselectedBgColor = if (isDarkTheme) Color.Transparent else Color.Transparent
    val textSelectedColor = if (isDarkTheme) PrimaryColor.copy(alpha = 0.9f) else PrimaryColor
    val textUnselectedColor = if (isDarkTheme) Color.White else Color.Black // Alterado de TextColorLight para White

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedBgColor else unselectedBgColor,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nova conversa",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Nova conversa",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) textSelectedColor else textUnselectedColor
            )
        }
    }
}

@Composable
fun ConversationItem(
    item: ConversationDisplayItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDarkTheme: Boolean = true
) {
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val selectedBgColor = if (isDarkTheme) PrimaryColor.copy(alpha = 0.25f) else PrimaryColor.copy(alpha = 0.15f)
    val unselectedBgColor = if (isDarkTheme) Color.Transparent else Color.Transparent
    val textSelectedColor = if (isDarkTheme) PrimaryColor.copy(alpha = 0.9f) else PrimaryColor
    val textUnselectedColor = if (isDarkTheme) TextColorLight else Color.Black
    val iconTintColor = if (isDarkTheme) Color.LightGray else Color.DarkGray

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedBgColor else unselectedBgColor,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SecondaryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.displayTitle.firstOrNull()?.uppercase() ?: "C",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) textSelectedColor else textUnselectedColor,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row {
                IconButton(
                    onClick = { onRenameClick() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Renomear",
                        tint = iconTintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onDeleteClick() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir",
                        tint = iconTintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}