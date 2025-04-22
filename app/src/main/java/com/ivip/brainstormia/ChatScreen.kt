package com.ivip.brainstormia

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.components.ExportDialog
import com.ivip.brainstormia.components.ModelSelectionDropdown
import com.ivip.brainstormia.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ivip.brainstormia.theme.BotBubbleColor
import dev.jeziellago.compose.markdowntext.MarkdownText
import androidx.compose.runtime.collectAsState
import com.ivip.brainstormia.components.SimpleVoiceInputButton
import androidx.compose.foundation.border
import androidx.compose.ui.focus.onFocusChanged

@Composable
fun MessageBubble(
    message: ChatMessage,
    isDarkTheme: Boolean = true
) {
    val isUserMessage = message.sender == Sender.USER

    val userShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp
    )

    // Cores adaptadas para o tema
    val userBubbleColor = BotBubbleColor
    val userTextColor = Color.White
    val botTextColor = if (isDarkTheme) TextColorLight else TextColorDark
    val linkColor = if (isDarkTheme) Color(0xFFCCE9FF) else Color(0xFFB8E2FF)

    val visibleState = remember { MutableTransitionState(initialState = isUserMessage) }

    LaunchedEffect(message) {
        if (!isUserMessage) {
            visibleState.targetState = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUserMessage) {
            // Mensagem do usuário
            Card(
                modifier = Modifier
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.88f),
                shape = userShape,
                colors = CardDefaults.cardColors(
                    containerColor = userBubbleColor
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDarkTheme) 4.dp else 2.dp
                )
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = userTextColor,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        } else {
            // Mensagem do bot sem bolha
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
                        slideInHorizontally(
                            initialOffsetX = { -40 },
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SelectionContainer {
                        MarkdownText(
                            markdown = message.text,
                            modifier = Modifier.fillMaxWidth(),
                            color = botTextColor,
                            linkColor = linkColor,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            textAlign = TextAlign.Start,
                            maxLines = Int.MAX_VALUE,
                            isTextSelectable = true,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 24.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    chatViewModel: ChatViewModel,  // Non-nullable parameter
    authViewModel: AuthViewModel = viewModel(),
    exportViewModel: ExportViewModel,  // Non-nullable parameter
    isDarkTheme: Boolean = true
) {
    // Definir cores do tema dentro do Composable
    val backgroundColor = if (isDarkTheme) BackgroundColorDark else BackgroundColor
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark

    val messages by chatViewModel.messages.collectAsState()
    val conversationDisplayList by chatViewModel.conversationListForDrawer.collectAsState()
    val currentConversationId by chatViewModel.currentConversationId.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    val logoutEvent by authViewModel.logoutEvent.collectAsState()
    val selectedModel by chatViewModel.selectedModel.collectAsState()

    // Estado de exportação
    val exportState by exportViewModel.exportState.collectAsState()

    var userMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val uriHandler = LocalUriHandler.current

    var conversationIdToRename by remember { mutableStateOf<Long?>(null) }
    var currentTitleForDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmationDialog by remember { mutableStateOf<Long?>(null) }
    var conversationIdToExport by remember { mutableStateOf<Long?>(null) } // Novo estado
    var exportDialogTitle by remember { mutableStateOf("") } // Novo estado

    LaunchedEffect(logoutEvent) {
        if (logoutEvent) {
            userMessage = ""
            chatViewModel.handleLogout()
            Log.d("ChatScreen", "Tela e menu lateral limpos após logout")
        }
    }

    LaunchedEffect(exportState) {
        if (exportState is ExportState.Success) {
            // Forçar atualização da lista de conversas após exportação bem-sucedida
            delay(500) // Pequeno atraso para garantir que o banco de dados atualizou
            chatViewModel.refreshConversationList()
        }
    }

    LaunchedEffect(conversationIdToRename) {
        val id = conversationIdToRename
        currentTitleForDialog = if (id != null && id != NEW_CONVERSATION_ID) "" else null
        if (id != null && id != NEW_CONVERSATION_ID) {
            Log.d("ChatScreen", "Fetching title for rename dialog (ID: $id)")
            try {
                currentTitleForDialog = chatViewModel.getDisplayTitle(id)
            } catch (e: Exception) {
                Log.e("ChatScreen", "Error fetching title for rename dialog", e)
            }
        }
    }

    // Novo efeito para buscar o título da conversa a ser exportada
    LaunchedEffect(conversationIdToExport) {
        val id = conversationIdToExport
        if (id != null && id != NEW_CONVERSATION_ID) {
            try {
                exportDialogTitle = chatViewModel.getDisplayTitle(id)
                Log.d("ChatScreen", "Preparando para exportar conversa: $exportDialogTitle (ID: $id)")
            } catch (e: Exception) {
                Log.e("ChatScreen", "Erro ao buscar título para exportação", e)
                conversationIdToExport = null
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                conversationDisplayItems = conversationDisplayList,
                currentConversationId = currentConversationId,
                onConversationClick = { conversationId ->
                    coroutineScope.launch {
                        drawerState.close()
                        try {
                            chatViewModel.selectConversation(conversationId)
                        } catch (e: Exception) {
                            Log.e("ChatScreen", "Erro ao selecionar conversa $conversationId", e)
                        }
                    }
                },
                onNewChatClick = {
                    coroutineScope.launch {
                        // mesma lógica para nova conversa
                        drawerState.close()
                        if (currentConversationId != null && currentConversationId != NEW_CONVERSATION_ID) {
                            chatViewModel.startNewConversation()
                        }
                    }
                },
                onDeleteConversationRequest = { conversationId ->
                    showDeleteConfirmationDialog = conversationId
                },
                onRenameConversationRequest = { conversationId ->
                    Log.d("ChatScreen", "Rename requested for $conversationId. Setting state.")
                    conversationIdToRename = conversationId
                },
                onExportConversationRequest = { conversationId -> // Novo callback
                    exportViewModel.resetExportState()
                    conversationIdToExport = conversationId
                },
                isDarkTheme = isDarkTheme
            )
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                        .background(PrimaryColor)
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            windowInsets = WindowInsets(0),
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_bolt_foreground),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Brainstormia",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Filled.Menu,
                                        stringResource(R.string.open_drawer_description),
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                // Botão de exportação para a conversa atual
                                if (currentConversationId != null && currentConversationId != NEW_CONVERSATION_ID) {
                                    IconButton(
                                        onClick = {
                                            exportViewModel.resetExportState()
                                            conversationIdToExport = currentConversationId
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = "Exportar conversa",
                                            tint = Color.White
                                        )
                                    }
                                }

                                // Botão de login/logout existente
                                IconButton(
                                    onClick = {
                                        if (currentUser != null) {
                                            onLogout()
                                        } else {
                                            onLogin()
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = if (currentUser != null) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                                        contentDescription = if (currentUser != null) "Sair" else "Entrar",
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = PrimaryColor,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        MessageInput(
                            message = userMessage,
                            onMessageChange = { userMessage = it },
                            onSendClick = {
                                if (userMessage.isNotBlank()) {
                                    chatViewModel.sendMessage(userMessage)
                                    userMessage = ""
                                }
                            },
                            isSendEnabled = !isLoading,
                            isDarkTheme = isDarkTheme,
                            viewModel = chatViewModel  // Passar o viewModel para o MessageInput
                        )
                    },
                    containerColor = backgroundColor,
                    contentColor = textColor
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(backgroundColor)
                    ) {
                        // Add AI model selector only if user is logged in
                        if (currentUser != null) {
                            ModelSelectionDropdown(
                                models = chatViewModel.modelOptions,
                                selectedModel = selectedModel,
                                onModelSelected = { chatViewModel.selectModel(it) },
                                isDarkTheme = isDarkTheme
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = messages,
                                    key = { message: ChatMessage -> "${message.sender}-${message.text.hashCode()}" }
                                ) { message ->
                                    MessageBubble(
                                        message = message,
                                        isDarkTheme = isDarkTheme
                                    )
                                }
                                if (isLoading) {
                                    item {
                                        TypingBubbleAnimation(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            isDarkTheme = isDarkTheme
                                        )
                                    }
                                }
                            }

                            errorMessage?.let { errorMsg ->
                                Text(
                                    text = "Erro: $errorMsg",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                        .background(
                                            color = Color(0xFFE53935),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de exclusão de conversa (existente)
        showDeleteConfirmationDialog?.let { conversationIdToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = null },
                title = {
                    Text(
                        text = "Excluir conversa",
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) TextColorLight else TextColorDark
                    )
                },
                text = {
                    Text(
                        text = "Tem certeza que deseja excluir esta conversa? Esta ação não pode ser desfeita.",
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) TextColorLight else TextColorDark
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            chatViewModel.deleteConversation(conversationIdToDelete)
                            showDeleteConfirmationDialog = null
                        }
                    ) {
                        Text(
                            text = "Excluir",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = null }) {
                        Text(
                            text = "Cancelar",
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) TextColorLight.copy(alpha = 0.8f) else Color.DarkGray
                        )
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = if (isDarkTheme) SurfaceColorDark else SurfaceColor,
                tonalElevation = if (isDarkTheme) 8.dp else 4.dp
            )
        }

        // Diálogo de renomear conversa (existente)
        conversationIdToRename?.let { id ->
            if (currentTitleForDialog != null) {
                RenameConversationDialog(
                    conversationId = id,
                    currentTitle = currentTitleForDialog,
                    onConfirm = { confirmedId, newTitle ->
                        chatViewModel.renameConversation(confirmedId, newTitle)
                        conversationIdToRename = null
                    },
                    onDismiss = {
                        conversationIdToRename = null
                    },
                    isDarkTheme = isDarkTheme
                )
            }
        }

        // Diálogo de exportação (novo)
        conversationIdToExport?.let { convId ->
            ExportDialog(
                conversationTitle = exportDialogTitle,
                exportState = exportState,
                onExportConfirm = {
                    exportViewModel.exportConversation(
                        conversationId = convId,
                        title = exportDialogTitle,
                        messages = messages
                    )
                },
                onDismiss = {
                    if (exportState !is ExportState.Loading) {
                        conversationIdToExport = null
                        if (exportState is ExportState.Success) {
                            exportViewModel.resetExportState()
                        }
                    }
                },
                isDarkTheme = isDarkTheme
            )
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
}

@Composable
fun MessageInput(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSendEnabled: Boolean,
    isDarkTheme: Boolean = true,
    viewModel: ChatViewModel
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isFocused  by remember { mutableStateOf(false) }
    val isListening by viewModel.isListening.collectAsState()

    // Animação de piscar para a borda
    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Cores do tema
    val backgroundColor        = if (isDarkTheme) BackgroundColorDark else BackgroundColor
    val surfaceColor           = if (isDarkTheme) SurfaceColorDark   else SurfaceColor
    val disabledContainerColor = if (isDarkTheme) PrimaryColor.copy(alpha = 0.25f)
    else PrimaryColor.copy(alpha = 0.15f)
    val disabledTextColor      = if (isDarkTheme) Color.LightGray.copy(alpha = 0.5f)
    else Color.DarkGray.copy(alpha = 0.7f)
    val disabledCursorColor    = if (isDarkTheme) PrimaryColor.copy(alpha = 0.7f)
    else PrimaryColor.copy(alpha = 0.6f)

    // Cor branca para borda e cursor
    val focusColor  = Color.White
    val borderColor = if (isFocused) focusColor.copy(alpha = blinkAlpha) else Color.Transparent
    val cursorColor = if (isFocused) focusColor else disabledCursorColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSendEnabled) surfaceColor else disabledContainerColor,
                    shape = RoundedCornerShape(28.dp)
                )
                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(28.dp))
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = {
                    Text(
                        text = "Mensagem...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize   = 16.sp,
                            color      = if (isSendEnabled) Color.LightGray.copy(alpha = 0.6f) else disabledTextColor
                        )
                    )
                },
                textStyle = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize   = 16.sp,
                    color      = if (isSendEnabled) TextColorLight else disabledTextColor
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor  = Color.Transparent,
                    errorIndicatorColor     = Color.Transparent,
                    focusedContainerColor   = if (isSendEnabled) surfaceColor else disabledContainerColor,
                    unfocusedContainerColor = if (isSendEnabled) surfaceColor else disabledContainerColor,
                    disabledContainerColor  = disabledContainerColor,
                    cursorColor             = cursorColor,
                    focusedTextColor        = if (isDarkTheme) TextColorLight else TextColorDark,
                    unfocusedTextColor      = if (isDarkTheme) TextColorLight else TextColorDark
                ),
                enabled  = isSendEnabled,
                maxLines = if (isExpanded) 8 else 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            val lineCount = if (message.isBlank()) 1 else message.count { it == '\n' } + 1
            val showExpand = lineCount >= 2 || message.length > 80
            if (showExpand) {
                val icon = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSendEnabled) Color.Gray.copy(alpha = 0.3f)
                            else PrimaryColor.copy(alpha = 0.25f)
                        )
                        .clickable(enabled = isSendEnabled) { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            SimpleVoiceInputButton(
                onTextResult     = { text -> onMessageChange(text); viewModel.handleVoiceInput(text) },
                isListening      = isListening,
                onStartListening = { viewModel.startListening() },
                onStopListening  = { viewModel.stopListening() },
                isSendEnabled    = isSendEnabled,
                isDarkTheme      = isDarkTheme
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !isSendEnabled       -> PrimaryColor.copy(alpha = if (isDarkTheme) 0.5f else 0.4f)
                            message.isNotBlank() -> PrimaryColor
                            else                 -> PrimaryColor.copy(alpha = if (isDarkTheme) 0.6f else 0.5f)
                        }
                    )
                    .clickable(enabled = message.isNotBlank() && isSendEnabled) { onSendClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun TypingIndicatorAnimation(
    isDarkTheme: Boolean = true,
    dotSize: Dp = 8.dp,
    spaceBetweenDots: Dp = 4.dp,
    bounceHeight: Dp = 6.dp
) {
    // Definir a cor dos pontos dentro da função
    val dotColor = if (isDarkTheme) TextColorLight else TextColorDark

    val dots = listOf(
        remember { Animatable(0f) },
        remember { Animatable(0f) },
        remember { Animatable(0f) }
    )

    val bounceHeightPx = with(LocalDensity.current) { bounceHeight.toPx() }

    dots.forEachIndexed { index, animatable ->
        LaunchedEffect(animatable) {
            delay(index * 140L)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1000
                        0f at 0 using LinearOutSlowInEasing
                        1f at 250 using LinearOutSlowInEasing
                        0f at 500 using LinearOutSlowInEasing
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    // Removendo o Card
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEachIndexed { index, animatable ->
            if (index != 0) {
                Spacer(modifier = Modifier.width(spaceBetweenDots))
            }

            val translateY = -animatable.value * bounceHeightPx

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = translateY
                    }
                    .background(color = dotColor, shape = CircleShape)
            )
        }
    }
}

@Composable
fun TypingBubbleAnimation(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Removendo o Card e deixando apenas a animação
        Box(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            TypingIndicatorAnimation(
                isDarkTheme = isDarkTheme
            )
        }
    }
}