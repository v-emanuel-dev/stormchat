package com.ivip.brainstormia

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.util.Log
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ivip.brainstormia.components.ExportDialog
import com.ivip.brainstormia.components.ImageGenerationDialog
import com.ivip.brainstormia.components.ModelSelectionDropdown
import com.ivip.brainstormia.theme.BackgroundColor
import com.ivip.brainstormia.theme.BotBubbleColor
import com.ivip.brainstormia.theme.PrimaryColor
import com.ivip.brainstormia.theme.SurfaceColor
import com.ivip.brainstormia.theme.SurfaceColorDark
import com.ivip.brainstormia.theme.TextColorDark
import com.ivip.brainstormia.theme.TextColorLight
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.commonmark.node.ThematicBreak
import androidx.compose.material.icons.filled.SaveAlt // Novo ícone
import androidx.compose.material3.Button // Se não estiver lá
import androidx.compose.material3.SnackbarDuration
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.core.content.ContextCompat

@Composable
fun MessageBubble(
    message: ChatMessage,
    isDarkTheme: Boolean = true,
    onSaveImageClicked: (String?) -> Unit = {} // Callback para salvar a imagem
) {
    val isUserMessage = message.sender == Sender.USER

    // Verificar se a mensagem contém uma imagem
    val containsImage = message.text.contains("![Imagem Gerada]")

    // Se for uma mensagem com imagem, extrair o caminho
    val imagePath = if (containsImage) {
        val regex = "!\\[Imagem Gerada\\]\\((.+?)\\)".toRegex()
        val matchResult = regex.find(message.text)
        matchResult?.groupValues?.get(1)
    } else null

    val userShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp
    )

    val botShape = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    // Colors
    val userBubbleColor = BotBubbleColor
    val userTextColor = Color.White
    val botTextColor =
        if (isDarkTheme) TextColorLight else Color.Black

    val visibleState = remember { MutableTransitionState(initialState = isUserMessage) }

    LaunchedEffect(message) {
        if (!isUserMessage) visibleState.targetState = true
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUserMessage) {
            // User bubble (unchanged)
            Card(
                modifier = Modifier
                    .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.88f),
                shape = userShape,
                colors = CardDefaults.cardColors(containerColor = userBubbleColor),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDarkTheme) 4.dp else 2.dp
                )
            ) {
                Box(
                    modifier = Modifier
                        .background(userBubbleColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = userTextColor,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        } else {
            // Bot message
            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(animationSpec = tween(300)) +
                        slideInHorizontally(
                            initialOffsetX = { -40 },
                            animationSpec = tween(400, easing = FastOutSlowInEasing)
                        )
            ) {
                // Para mensagens com imagem, trate de forma diferente
                if (imagePath != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(start = 16.dp, end = 16.dp)
                    ) {
                        // Exibição da imagem simplificada - sem cards ou boxes aninhados
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            var isImageLoading by remember { mutableStateOf(true) }

                            // Imagem sem containers adicionais
                            Image(
                                painter = rememberAsyncImagePainter(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(imagePath)
                                        .listener(
                                            onStart = {
                                                isImageLoading = true
                                            },
                                            onSuccess = { _, _ ->
                                                isImageLoading = false
                                            },
                                            onError = { _, _ ->
                                                isImageLoading = false
                                            }
                                        )
                                        .build()
                                ),
                                contentDescription = stringResource(R.string.generated_image_description),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                contentScale = ContentScale.Fit // Mantém a proporção original
                            )

                            // Mostrar indicador de carregamento
                            if (isImageLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(30.dp)
                                )
                            } else {
                                // Botão de salvar
                                IconButton(
                                    onClick = {
                                        onSaveImageClicked(imagePath)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SaveAlt,
                                        contentDescription = stringResource(R.string.save_image),
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Container para o texto do prompt
                        Card(
                            shape = botShape,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkTheme) Color(0xFF292929) else Color(
                                    0xFFE8E8E8
                                )
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isDarkTheme) 2.dp else 1.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                // Texto do prompt
                                val promptRegex = "\"(.+?)\"".toRegex()
                                val promptMatch = promptRegex.find(message.text)
                                val prompt = promptMatch?.groupValues?.get(1) ?: ""

                                Text(
                                    text = "Imagem gerada com base no prompt:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = botTextColor
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "\"$prompt\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = botTextColor
                                )
                            }
                        }
                    }
                } else {
                    // Mensagens normais de texto (sem imagem)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Use AndroidView com TextView + Markwon
                        AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    // Configuração básica do TextView
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                    setTextColor(botTextColor.toArgb())
                                    textSize = 16f
                                    setLineSpacing(4f, 1f)

                                    // Aplicar técnica de correção de seleção de texto
                                    setTextIsSelectable(false)
                                    post {
                                        setTextIsSelectable(true)
                                    }

                                    // Plugin personalizado para lidar com regras horizontais
                                    val customHrPlugin = object : AbstractMarkwonPlugin() {
                                        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                                            builder.setFactory(ThematicBreak::class.java) { _, _ ->
                                                arrayOf(
                                                    // Criar um span personalizado em vez do HR padrão
                                                    object : LeadingMarginSpan.Standard(0),
                                                        LineBackgroundSpan {
                                                        override fun drawBackground(
                                                            canvas: Canvas,
                                                            paint: Paint,
                                                            left: Int,
                                                            right: Int,
                                                            top: Int,
                                                            baseline: Int,
                                                            bottom: Int,
                                                            text: CharSequence,
                                                            start: Int,
                                                            end: Int,
                                                            lineNumber: Int
                                                        ) {
                                                            val originalColor = paint.color
                                                            val originalWidth = paint.strokeWidth

                                                            // Usar valores diretos em vez de métodos do theme
                                                            paint.color = botTextColor.toArgb()
                                                            paint.strokeWidth = 6f // ~2dp

                                                            // Padding fixo em vez de recursos de dimensão
                                                            val padding = 48 // ~16dp
                                                            val lineLeft = left + padding
                                                            val lineRight = right - padding
                                                            val lineY = (top + bottom) / 2f

                                                            canvas.drawLine(
                                                                lineLeft.toFloat(),
                                                                lineY,
                                                                lineRight.toFloat(),
                                                                lineY,
                                                                paint
                                                            )

                                                            paint.color = originalColor
                                                            paint.strokeWidth = originalWidth
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Configurar Markwon para renderizar Markdown com nosso plugin personalizado
                                    val markwon = Markwon.builder(context)
                                        .usePlugin(HtmlPlugin.create())
                                        .usePlugin(LinkifyPlugin.create())
                                        .usePlugin(customHrPlugin) // Adicionar nosso plugin personalizado
                                        .build()

                                    // Renderizar o Markdown
                                    markwon.setMarkdown(this, message.text)
                                }
                            },
                            update = { textView ->
                                // Atualizar quando a mensagem mudar
                                val context = textView.context

                                // Plugin personalizado para lidar com regras horizontais (durante atualizações)
                                val customHrPlugin = object : AbstractMarkwonPlugin() {
                                    override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                                        builder.setFactory(ThematicBreak::class.java) { _, _ ->
                                            arrayOf(
                                                object : LeadingMarginSpan.Standard(0),
                                                    LineBackgroundSpan {
                                                    override fun drawBackground(
                                                        canvas: Canvas, paint: Paint,
                                                        left: Int, right: Int,
                                                        top: Int, baseline: Int, bottom: Int,
                                                        text: CharSequence, start: Int, end: Int,
                                                        lineNumber: Int
                                                    ) {
                                                        val originalColor = paint.color
                                                        val originalWidth = paint.strokeWidth

                                                        paint.color = botTextColor.toArgb()
                                                        paint.strokeWidth = 6f

                                                        val padding = 48
                                                        val lineLeft = left + padding
                                                        val lineRight = right - padding
                                                        val lineY = (top + bottom) / 2f

                                                        canvas.drawLine(
                                                            lineLeft.toFloat(),
                                                            lineY,
                                                            lineRight.toFloat(),
                                                            lineY,
                                                            paint
                                                        )

                                                        paint.color = originalColor
                                                        paint.strokeWidth = originalWidth
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                // Criar instância do Markwon com nosso plugin personalizado
                                val markwon = Markwon.builder(context)
                                    .usePlugin(HtmlPlugin.create())
                                    .usePlugin(LinkifyPlugin.create())
                                    .usePlugin(customHrPlugin)
                                    .build()

                                // Resetar o estado de seleção para corrigir o bug
                                textView.setTextIsSelectable(false)

                                // Sempre atualizar a cor do texto quando isDarkTheme muda
                                textView.setTextColor(botTextColor.toArgb())

                                // Renderizar Markdown e reativar seleção
                                markwon.setMarkdown(textView, message.text)
                                textView.post {
                                    textView.setTextIsSelectable(true)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}


/**
 * A SelectionContainer that tries to maintain selection when tapping outside
 */
@Composable
fun PersistentSelectionContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val selectionIsImportant = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // No ripple effect
            ) {
                // Do nothing on click, but capture the click event
                // This prevents clicks from bubbling up and canceling selection
            }
    ) {
        SelectionContainer {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},  // Adicione este parâmetro
    chatViewModel: ChatViewModel,  // Non-nullable parameter
    authViewModel: AuthViewModel = viewModel(),
    exportViewModel: ExportViewModel,  // Non-nullable parameter
    isDarkTheme: Boolean = true,
    onThemeChanged: (Boolean) -> Unit = {}  // Add this parameter with default value
) {
    // Definir cores do tema dentro do Composable
    val backgroundColor =
        if (isDarkTheme) Color(0xFF121212) else BackgroundColor // Usando #121212 como cor de fundo principal
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark

    // Definir a cor amarela para o ícone de raio
    val raioBrandColor = Color(0xFFFFD700) // Cor amarela padrão

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

    // Estado de geração de imagem
    val isImageGenerating by chatViewModel.isImageGenerating.collectAsState()
    val currentImagePrompt by chatViewModel.currentImagePrompt.collectAsState()

    var userMessage by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val modelSelectorVisible = remember {
        derivedStateOf {
            // Mostra o seletor quando estamos no topo ou quando é uma nova conversa vazia
            messages.isEmpty() || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 50)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var conversationIdToRename by remember { mutableStateOf<Long?>(null) }
    var currentTitleForDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmationDialog by remember { mutableStateOf<Long?>(null) }
    var conversationIdToExport by remember { mutableStateOf<Long?>(null) } // Novo estado
    var exportDialogTitle by remember { mutableStateOf("") } // Novo estado
    val isPremiumUser by chatViewModel.isPremiumUser.collectAsState()

    var showImageGenerationDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Variável para armazenar o caminho da imagem a ser salva após permissão
    var imagePathToSaveAfterPermission by remember { mutableStateOf<String?>(null) }

    // Definir a permissão a ser solicitada com base na versão Android
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Para Android Q (API 29) e superior
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    } else {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    // Variável para controlar a exibição do diálogo de explicação da permissão
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }

    // Launcher para solicitar permissão
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            imagePathToSaveAfterPermission?.let { path ->
                chatViewModel.saveImageToGallery(path)
                imagePathToSaveAfterPermission = null // Limpa após usar
            }
        } else {
            // Permissão negada, mostrar snackbar
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.image_save_permission_denied),
                    duration = SnackbarDuration.Short
                )
            }
            // Opcionalmente mostrar diálogo explicando a necessidade da permissão
            // showPermissionRationaleDialog = true
        }
    }

    // Função para verificar permissão e salvar imagem
    fun checkAndSaveImage(imagePath: String) {
        // Para Android Q (API 29) e superior, salvar em MediaStore na pasta Pictures/AppName
        // geralmente não requer permissão explícita de WRITE_EXTERNAL_STORAGE.
        // Para Android < Q, a permissão é necessária.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(context, permissionToRequest) -> {
                    chatViewModel.saveImageToGallery(imagePath)
                }

                else -> {
                    imagePathToSaveAfterPermission = imagePath
                    requestPermissionLauncher.launch(permissionToRequest)
                }
            }
        } else {
            // Em Android Q+ não precisamos de permissão para salvar na pasta específica do App no MediaStore.
            chatViewModel.saveImageToGallery(imagePath)
        }
    }

    // Diálogo de explicação de permissão (opcional)
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionRationaleDialog = false },
            title = { Text(stringResource(R.string.permission_required)) },
            text = { Text(stringResource(R.string.request_save_permission)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            chatViewModel.checkIfUserIsPremium()
        }
    }

    LaunchedEffect(Unit) {
        // Verificação imediata ao iniciar a tela
        if (currentUser != null) {
            chatViewModel.checkIfUserIsPremium()
        }

        // Verificação periódica
        while (true) {
            delay(60000) // 1 minuto
            if (currentUser != null) {
                chatViewModel.checkIfUserIsPremium()
            }
        }
    }

    // Escutar eventos de mensagem adicionada
    LaunchedEffect(Unit) {
        chatViewModel.messageAddedEvent.collect {
            // Rolar para a última mensagem quando uma nova for adicionada
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Escutar eventos de imagem salva
    LaunchedEffect(Unit) {
        chatViewModel.imageSavedEvent.collect { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Long
            )
        }
    }

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
                exportViewModel.setupDriveService()
                Log.d(
                    "ChatScreen",
                    "Preparando para exportar conversa: $exportDialogTitle (ID: $id)"
                )
            } catch (e: Exception) {
                Log.e("ChatScreen", "Erro ao buscar título para exportação", e)
                conversationIdToExport = null
            }
        }
    }

    LaunchedEffect(Unit) {
        // Verificar imediatamente ao iniciar
        if (currentUser != null) {
            chatViewModel.checkIfUserIsPremium()
        }

        // Depois verificar a cada minuto
        while (true) {
            delay(60000) // 1 minuto
            if (currentUser != null) {
                chatViewModel.checkIfUserIsPremium()
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
                onExportConversationRequest = { conversationId ->
                    exportViewModel.resetExportState()
                    conversationIdToExport = conversationId
                },
                onNavigateToProfile = onNavigateToProfile,
                isDarkTheme = isDarkTheme,
                onThemeChanged = onThemeChanged,
                chatViewModel = chatViewModel
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
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    contentWindowInsets = WindowInsets.ime,
                    topBar = {
                        CenterAlignedTopAppBar(
                            windowInsets = WindowInsets(0),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_bolt_foreground),
                                        contentDescription = stringResource(R.string.app_icon_description), // Substitui "Ícone StormChat"
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                }
                            },
                            navigationIcon = {
                                Row {
                                    IconButton(onClick = {
                                        coroutineScope.launch { drawerState.open() }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = stringResource(R.string.menu_description), // Substitui "Menu"
                                            tint = Color.White
                                        )
                                    }

                                    val isPremiumUser by chatViewModel.isPremiumUser.collectAsState()

                                    if (isPremiumUser == true && currentConversationId != null && currentConversationId != NEW_CONVERSATION_ID) {
                                        IconButton(
                                            onClick = {
                                                exportViewModel.resetExportState()
                                                conversationIdToExport = currentConversationId
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = stringResource(R.string.export_conversation_description), // Substitui "Exportar conversa"
                                                tint = Color.White
                                            )
                                        }
                                    }


                                }
                            },
                            actions = {
                                val rotation = rememberInfiniteTransition()
                                val angle by rotation.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 6000),
                                        repeatMode = RepeatMode.Restart
                                    )
                                )
                                // Para usuários premium, mostrar a estrela dourada animada
                                if (isPremiumUser) {
                                    IconButton(
                                        onClick = { onNavigateToProfile() },
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .graphicsLayer { rotationZ = angle }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = stringResource(R.string.premium_user_description), // Substitui "Usuário Premium"
                                            tint = Color(0xFFFFD700) // Usar cor dourada consistente
                                        )
                                    }
                                } else {
                                    // Para usuários normais, mostrar a estrela branca
                                    IconButton(
                                        onClick = { onNavigateToProfile() },
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Perfil",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (currentUser != null) {
                                            onLogout()
                                        } else {
                                            onLogin()
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (currentUser != null) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Filled.Login,
                                        contentDescription = if (currentUser != null) stringResource(
                                            R.string.logout
                                        ) else stringResource(R.string.login), // Substitui "Sair"/"Entrar"
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else PrimaryColor,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        MessageInput(
                            message = userMessage,
                            onMessageChange = { newText -> userMessage = newText },
                            onSendClick = {
                                if (userMessage.isNotBlank()) {
                                    chatViewModel.sendMessage(userMessage)
                                    userMessage = ""
                                }
                            },
                            isSendEnabled = !isLoading,
                            isDarkTheme = isDarkTheme,
                            viewModel = chatViewModel,
                            onImageGenerationClick = { showImageGenerationDialog = true }
                        )
                    },
                    containerColor = if (isDarkTheme) Color(0xFF121212) else backgroundColor,
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
                            AnimatedVisibility(
                                visible = modelSelectorVisible.value,
                                enter = fadeIn(animationSpec = tween(300)) + expandVertically(
                                    animationSpec = tween(300)
                                ),
                                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(
                                    animationSpec = tween(300)
                                )
                            ) {
                                key(isPremiumUser) {
                                    ModelSelectionDropdown(
                                        models = chatViewModel.modelOptions,
                                        selectedModel = selectedModel,
                                        onModelSelected = { chatViewModel.selectModel(it) },
                                        isPremiumUser = isPremiumUser,
                                        isDarkTheme = isDarkTheme
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            ImageGenerationDialog(
                                isVisible = showImageGenerationDialog,
                                onDismiss = { showImageGenerationDialog = false },
                                onGenerateImage = { prompt, quality, size, transparent ->
                                    chatViewModel.generateImage(prompt, quality, size, transparent)
                                    showImageGenerationDialog =
                                        false  // Close dialog after generating
                                },
                                generationState = chatViewModel.imageGenerationState.collectAsState().value,
                                isDarkTheme = isDarkTheme
                            )

                            // Wrap the entire LazyColumn with a SelectionContainer
                            SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        // Important: prevent click events from bubbling up
                                        .clickable(
                                            enabled = false,
                                            onClick = {}
                                        ),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        items = messages,
                                        key = { index, _ -> index }
                                    ) { _, message ->
                                        MessageBubble(
                                            message = message,
                                            isDarkTheme = isDarkTheme,
                                            onSaveImageClicked = { imagePath ->
                                                if (imagePath != null) {
                                                    checkAndSaveImage(imagePath)
                                                } else {
                                                    Log.w(
                                                        "ChatScreen",
                                                        "Save image clicked, but path was null for message: ${message.text}"
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    // Mostrar indicador de carregamento durante geração de imagem
                                    if (isImageGenerating) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Card(
                                                    modifier = Modifier
                                                        .widthIn(max = LocalConfiguration.current.screenWidthDp.dp * 0.88f),
                                                    shape = RoundedCornerShape(
                                                        20.dp,
                                                        20.dp,
                                                        6.dp,
                                                        20.dp
                                                    ),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isDarkTheme) Color(
                                                            0xFF292929
                                                        ) else Color(0xFFE4E4E4)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(16.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        LightningLoadingAnimation(
                                                            isDarkTheme = isDarkTheme
                                                        )

                                                        Spacer(modifier = Modifier.height(8.dp))

                                                        Text(
                                                            text = "Gerando imagem...",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isDarkTheme) TextColorLight else TextColorDark
                                                        )

                                                        if (!currentImagePrompt.isNullOrBlank()) {
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = "\"${currentImagePrompt}\"",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontStyle = FontStyle.Italic,
                                                                color = if (isDarkTheme) TextColorLight else TextColorDark
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Loading para resposta de texto do modelo
                                    if (isLoading) {
                                        item {
                                            LightningLoadingAnimation(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                isDarkTheme = isDarkTheme
                                            )
                                        }
                                    }
                                }
                            }

                            errorMessage?.let { errorMsg ->
                                Text(
                                    text = stringResource(
                                        R.string.error_prefix,
                                        errorMsg
                                    ), // Substitui "Erro: $errorMsg"
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

        val showScrollToTopButton = remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 2 ||
                        (listState.firstVisibleItemIndex > 0 && listState.firstVisibleItemScrollOffset > 100)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd
        ) {
            AnimatedVisibility(
                visible = showScrollToTopButton.value && messages.size > 3,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp, bottom = 96.dp)
                        .zIndex(8f)
                ) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                // Versão simplificada sem animationSpec
                                listState.animateScrollToItem(index = 0)
                            }
                        },
                        modifier = Modifier.size(46.dp),
                        containerColor = if (isDarkTheme)
                            Color(0xFF3D3D3D) else
                            PrimaryColor,
                        contentColor = Color.White
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.scroll_to_top),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // O restante do código permanece inalterado
        // Diálogos de exclusão, renomear e exportação
        showDeleteConfirmationDialog?.let { conversationIdToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = null },
                title = {
                    Text(
                        text = stringResource(R.string.delete_confirmation_title), // Substitui "Excluir conversa"
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) TextColorLight else TextColorDark
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.delete_confirmation_message), // Substitui "Tem certeza que deseja excluir esta conversa? Esta ação não pode ser desfeita."
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
                            text = stringResource(R.string.delete), // Substitui "Excluir"
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmationDialog = null }) {
                        Text(
                            text = stringResource(R.string.cancel), // Substitui "Cancelar"
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
    viewModel: ChatViewModel,
    onImageGenerationClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isListening by viewModel.isListening.collectAsState()

    // Estado para verificar se tem texto sendo digitado
    val isTyping = message.isNotBlank()

    // Animação de piscar para a borda
    val infiniteTransition = rememberInfiniteTransition()
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // Cores do tema
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else BackgroundColor
    val surfaceColor = if (isDarkTheme) Color(0xFF333333) else Color(0xFFC8C8C9)
    val disabledContainerColor =
        if (isDarkTheme) Color(0xFF282828) else PrimaryColor.copy(alpha = 0.15f)
    val disabledTextColor =
        if (isDarkTheme) Color.LightGray.copy(alpha = 0.5f) else Color.DarkGray.copy(alpha = 0.7f)
    val disabledCursorColor =
        if (isDarkTheme) PrimaryColor.copy(alpha = 0.7f) else PrimaryColor.copy(alpha = 0.6f)

    // Cor da bolha do usuário (azul usado no botão de enviar)
    val userBubbleColor =
        if (isDarkTheme) Color(0xFF0D47A1) else Color(0xFF1976D2) // Azul da bolha do usuário

    // FIXED: Cor do botão de enviar quando tem texto (PrimaryColor em vez de branco no tema claro)
    val sendButtonColor = when {
        !isSendEnabled -> if (isDarkTheme) Color(0xFF333333) else PrimaryColor.copy(alpha = 0.4f)
        message.isNotBlank() -> if (isDarkTheme) Color(0xFF333333) else PrimaryColor // AQUI: Mudado para PrimaryColor
        else -> if (isDarkTheme) Color(0xFF333333).copy(alpha = 0.6f) else PrimaryColor.copy(alpha = 0.5f)
    }

    // Cor do placeholder
    val placeholderColor = if (isDarkTheme)
        Color.LightGray.copy(alpha = 0.6f)
    else
        Color.Black.copy(alpha = 0.6f)

    // Cor branca para borda e cursor
    val focusColor = if (isDarkTheme) Color.White else PrimaryColor
    val borderColor = if (isFocused) focusColor.copy(alpha = blinkAlpha) else Color.Transparent
    val cursorColor = if (isFocused) focusColor else disabledCursorColor

    val borderWidth = if (isDarkTheme) 2.dp else 2.5.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding() // Adicionar esta linha
            .background(backgroundColor)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSendEnabled) surfaceColor else disabledContainerColor,
                    shape = RoundedCornerShape(28.dp)
                )
                .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(28.dp))
                .padding(
                    start = 8.dp,
                    end = 4.dp,
                    top = 2.dp,
                    bottom = 6.dp
                ), // Reduzido padding geral
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message,
                onValueChange = onMessageChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.message_hint), // Substitui "Mensagem..."
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = if (isSendEnabled) placeholderColor else disabledTextColor
                        )
                    )
                },
                textStyle = TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (isDarkTheme) TextColorLight else TextColorDark
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp) // Reduzido a altura mínima
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = if (isSendEnabled) surfaceColor else disabledContainerColor,
                    unfocusedContainerColor = if (isSendEnabled) surfaceColor else disabledContainerColor,
                    disabledContainerColor = disabledContainerColor,
                    cursorColor = cursorColor,
                    focusedTextColor = if (isDarkTheme) TextColorLight else TextColorDark,
                    unfocusedTextColor = if (isDarkTheme) TextColorLight else TextColorDark
                ),
                enabled = isSendEnabled,
                maxLines = 7 // Aumentado para 7 linhas
            )

            // Espaço reduzido entre os componentes
            Spacer(modifier = Modifier.width(4.dp))

            // Áreas de botões agrupados
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 1.dp)
            ) {
                // Botão de microfone (visível apenas quando não está digitando)
                if (!isTyping) {
                    SimpleVoiceInputButton(
                        onTextResult = { text ->
                            onMessageChange(text); viewModel.handleVoiceInput(
                            text
                        )
                        },
                        isListening = isListening,
                        onStartListening = { viewModel.startListening() },
                        onStopListening = { viewModel.stopListening() },
                        isSendEnabled = isSendEnabled,
                        isDarkTheme = isDarkTheme,
                        size = 36.dp,
                        iconSize = 18.dp
                    )

                    // Espaço mínimo entre microfone e botão de enviar
                    Spacer(modifier = Modifier.width(2.dp))

//                    if (viewModel.isPremiumUser.collectAsState().value) {
//                        // Botão para geração de imagem
//                        Box(
//                            modifier = Modifier
//                                .size(36.dp)
//                                .clip(CircleShape)
//                                .background(
//                                    if (isDarkTheme) Color.Gray.copy(alpha = 0.3f) else PrimaryColor.copy(
//                                        alpha = 0.25f
//                                    )
//                                )
//                                .clickable(enabled = isSendEnabled) { onImageGenerationClick() },
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                imageVector = Icons.Default.Image,
//                                contentDescription = stringResource(R.string.generate_image_description),
//                                modifier = Modifier.size(18.dp),
//                                tint = if (isDarkTheme) Color.White else Color.Black
//                            )
//                        }
                }
            }

            // Botão de enviar (maior que o microfone)
            Box(
                modifier = Modifier
                    .size(48.dp) // Tamanho aumentado
                    .clip(CircleShape)
                    .background(sendButtonColor) // FIXED: Usando a cor corrigida
                    .clickable(enabled = message.isNotBlank() && isSendEnabled) { onSendClick() },
                contentAlignment = Alignment.Center
            ) {
                // FIXED: Cor do ícone de enviar ajustada para garantir contraste
                val iconColor =
                    if (!isDarkTheme && message.isNotBlank()) Color.White else Color.White

                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.send_description), // Adiciona descrição para acessibilidade
                    modifier = Modifier.size(26.dp), // Ícone aumentado
                    tint = iconColor // Cor ajustada para garantir visibilidade
                )
            }
        }
    }
}

// Componente de botão de voz otimizado com tamanho personalizável
@Composable
fun SimpleVoiceInputButton(
    onTextResult: (String) -> Unit,
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    isSendEnabled: Boolean,
    isDarkTheme: Boolean,
    size: Dp = 36.dp,        // Tamanho do botão (personalizável)
    iconSize: Dp = 18.dp     // Tamanho do ícone (personalizável)
) {
    // Implementação existente, mas com tamanhos personalizáveis
    val backgroundColor = if (isDarkTheme) {
        if (isListening) Color.Red.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f)
    } else {
        if (isListening) Color.Red.copy(alpha = 0.6f) else PrimaryColor.copy(alpha = 0.25f)
    }

    Box(
        modifier = Modifier
            .size(size) // Tamanho personalizável
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = isSendEnabled) {
                if (isListening) {
                    onStopListening()
                } else {
                    onStartListening()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Use Icons.Default.KeyboardVoice em vez de Icons.Default.Mic
        Icon(
            imageVector = Icons.Default.KeyboardVoice, // Alternativa para Icons.Default.Mic
            contentDescription = stringResource(R.string.voice_input_description), // Substitui a descrição nula ou vazia
            modifier = Modifier.size(iconSize), // Tamanho do ícone personalizável
            tint = if (isDarkTheme) Color.White else Color.Black
        )
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
fun LightningLoadingAnimation(
    isDarkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    // FIXED: Enhanced colors for light theme
    // Cores para a animação do raio ajustadas para melhor contraste em tema claro
    val baseColor =
        if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B) // Amarelo dourado (escuro) para tema claro
    val accentColor =
        if (isDarkTheme) Color(0xFFFF9500) else Color(0xFFFFA500) // Laranja mais intenso para tema claro

    // Animação de rotação
    val rotation = rememberInfiniteTransition(label = "rotationTransition")
    val rotateAngle by rotation.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotateAnimation"
    )

    // Animação de escala (pulsar)
    val scale = rememberInfiniteTransition(label = "scaleTransition")
    val scaleSize by scale.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnimation"
    )

    // Animação de cor
    val colorTransition = rememberInfiniteTransition(label = "colorTransition")
    val colorProgress by colorTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colorAnimation"
    )

    // Interpolação de cor manual
    val currentColor = androidx.compose.ui.graphics.lerp(
        baseColor,
        accentColor,
        colorProgress
    )

    // Animação de brilho (glow) - ENHANCED
    val glow = rememberInfiniteTransition(label = "glowTransition")
    val glowIntensity by glow.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAnimation"
    )

    Box(
        modifier = modifier
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // FIXED: Glow effect enhanced for light theme
        // Adicionar um efeito de círculo de "glow" atrás do raio com opacidade aumentada para tema claro
        Box(
            modifier = Modifier
                .size(70.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            currentColor.copy(alpha = if (isDarkTheme) 0.3f * glowIntensity else 0.5f * glowIntensity),
                            currentColor.copy(alpha = if (isDarkTheme) 0.1f * glowIntensity else 0.25f * glowIntensity),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // FIXED: Shadow effect for better visibility in light theme
        if (!isDarkTheme) {
            // Add an extra drop shadow effect for light theme
            Icon(
                painter = painterResource(id = R.drawable.ic_bolt_foreground),
                contentDescription = stringResource(R.string.loading), // Substitui "Carregando..."
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer {
                        rotationZ = rotateAngle
                        scaleX = scaleSize
                        scaleY = scaleSize
                        alpha = 0.5f
                    },
                tint = Color.DarkGray.copy(alpha = 0.5f)
            )
        }

        // Ícone do raio
        Icon(
            painter = painterResource(id = R.drawable.ic_bolt_foreground),
            contentDescription = "Carregando...",
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    rotationZ = rotateAngle
                    scaleX = scaleSize
                    scaleY = scaleSize
                    alpha = glowIntensity
                },
            tint = currentColor
        )
    }
}

// Substitua a função TypingBubbleAnimation existente por esta:
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
        // Substitui a animação de pontos pelo raio animado
        Box(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LightningLoadingAnimation(
                isDarkTheme = isDarkTheme
            )
        }
    }
}