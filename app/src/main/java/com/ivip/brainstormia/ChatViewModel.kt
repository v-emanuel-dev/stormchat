package com.ivip.brainstormia

import com.ivip.brainstormia.data.models.AIModel
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainstormia.ConversationType
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.ivip.brainstormia.data.db.AppDatabase
import com.ivip.brainstormia.data.db.ChatDao
import com.ivip.brainstormia.data.db.ChatMessageEntity
import com.ivip.brainstormia.data.db.ConversationInfo
import com.ivip.brainstormia.data.db.ConversationMetadataDao
import com.ivip.brainstormia.data.db.ConversationMetadataEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ivip.brainstormia.data.db.ModelPreferenceDao
import com.ivip.brainstormia.data.db.ModelPreferenceEntity

enum class LoadingState { IDLE, LOADING, ERROR }

const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao()
    private val modelPreferenceDao: ModelPreferenceDao = appDb.modelPreferenceDao()

    // Lista de modelos disponíveis
    private val availableModels = listOf(
        AIModel(
            id = "gemini-2.5-flash-preview-04-17",
            displayName = "Gemini 2.5 Flash",
            apiEndpoint = "gemini-2.5-flash-preview-04-17"
        ),
        AIModel(
            id = "gemini-2.5-pro-exp-03-25",
            displayName = "Gemini 2.5 Pro",
            apiEndpoint = "gemini-2.5-pro-exp-03-25"
        ),
        AIModel(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            apiEndpoint = "gemini-2.0-flash"
        )
    )

    // Estado do modelo selecionado (default: Gemini 2.5 Pro)
    private val _selectedModel = MutableStateFlow(availableModels[1])
    val selectedModel: StateFlow<AIModel> = _selectedModel.asStateFlow()

    // Expor a lista de modelos
    val modelOptions: List<AIModel> = availableModels

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Estados para gerenciar o reconhecimento de voz
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun startListening() {
        _isListening.value = true
        // Timeout para parar de ouvir após 30 segundos
        viewModelScope.launch {
            delay(30000)
            stopListening()
        }
    }

    fun stopListening() {
        _isListening.value = false
    }

    // Método para lidar com o resultado do reconhecimento de voz
    fun handleVoiceInput(text: String) {
        stopListening()
        // O texto reconhecido será enviado como uma mensagem normal
        // Você pode processá-lo aqui antes de enviá-lo para o serviço
    }

    // Método para atualizar o modelo selecionado
    fun selectModel(model: AIModel) {
        if (model.id != _selectedModel.value.id) {
            _selectedModel.value = model

            // Salvar no banco de dados
            viewModelScope.launch {
                try {
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = _userIdFlow.value,
                            selectedModelId = model.id
                        )
                    )
                    Log.i("ChatViewModel", "Saved model preference: ${model.displayName}")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving model preference", e)
                    _errorMessage.value = "Erro ao salvar preferência de modelo: ${e.localizedMessage}"
                }
            }
        }
    }

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _loadingState = MutableStateFlow(LoadingState.IDLE)
    val isLoading: StateFlow<Boolean> = _loadingState.map { it == LoadingState.LOADING }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    private val _clearConversationListEvent = MutableStateFlow(false)
    val clearConversationListEvent: StateFlow<Boolean> = _clearConversationListEvent.asStateFlow()

    private val _userIdFlow = MutableStateFlow(getCurrentUserId())

    private val _showConversations = MutableStateFlow(true)
    val showConversations: StateFlow<Boolean> = _showConversations.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _userIdFlow.value = getCurrentUserId()
        }
        loadInitialConversationOrStartNew()
        viewModelScope.launch {
            modelPreferenceDao.getModelPreference(_userIdFlow.value)
                .collect { preference ->
                    if (preference != null) {
                        val savedModel = availableModels.find { it.id == preference.selectedModelId }
                        if (savedModel != null) {
                            _selectedModel.value = savedModel
                            Log.i("ChatViewModel", "Loaded user model preference: ${savedModel.displayName}")
                        }
                    }
                }
        }
    }

    private fun getCurrentUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"

    private val rawConversationsFlow: Flow<List<ConversationInfo>> =
        _userIdFlow.flatMapLatest { uid ->
            chatDao.getConversationsForUser(uid)
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading raw conversations flow", e)
                    _errorMessage.value = "Erro ao carregar lista de conversas (raw)."
                    emit(emptyList())
                }
        }

    private val metadataFlow: Flow<List<ConversationMetadataEntity>> =
        _userIdFlow.flatMapLatest { uid ->
            metadataDao.getMetadataForUser(uid)
                .catch { e ->
                    Log.e("ChatViewModel", "Error loading metadata flow", e)
                    emit(emptyList())
                }
        }

    val conversationListForDrawer: StateFlow<List<ConversationDisplayItem>> =
        combine(rawConversationsFlow, metadataFlow, _showConversations, _userIdFlow) { conversations, metadataList, showConversations, currentUserId ->
            if (!showConversations || auth.currentUser == null) {
                return@combine emptyList<ConversationDisplayItem>()
            }

            Log.d("ChatViewModel", "Combining ${conversations.size} convs and ${metadataList.size} metadata entries for user $currentUserId.")

            val userMetadata = metadataList.filter { it.userId == currentUserId }
            val metadataMap = userMetadata.associateBy({ it.conversationId }, { it.customTitle })

            conversations.map { convInfo ->
                val customTitle = metadataMap[convInfo.id]?.takeIf { it.isNotBlank() }
                val finalTitle = customTitle ?: generateFallbackTitleSync(convInfo.id)
                val conversationType = determineConversationType(finalTitle, convInfo.id)
                ConversationDisplayItem(
                    id = convInfo.id,
                    displayTitle = finalTitle,
                    lastTimestamp = convInfo.lastTimestamp,
                    conversationType = conversationType
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Log.e("ChatViewModel", "Error combining conversations and metadata", e)
                withContext(Dispatchers.Main.immediate) {
                    _errorMessage.value = "Erro ao processar lista de conversas para exibição."
                }
                emit(emptyList())
            }
            .stateIn(scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList())

    val messages: StateFlow<List<ChatMessage>> =
        _currentConversationId.flatMapLatest { convId ->
            Log.d("ChatViewModel", "[State] CurrentConversationId changed: $convId")
            when (convId) {
                null, NEW_CONVERSATION_ID -> {
                    flowOf(listOf(ChatMessage(welcomeMessageText, Sender.BOT)))
                }
                else -> chatDao.getMessagesForConversation(convId, _userIdFlow.value)
                    .map { entities ->
                        Log.d("ChatViewModel", "[State] Mapping ${entities.size} entities for conv $convId")
                        mapEntitiesToUiMessages(entities)
                    }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error loading messages for conversation $convId", e)
                        withContext(Dispatchers.Main.immediate) {
                            _errorMessage.value = "Erro ao carregar mensagens da conversa."
                        }
                        emit(emptyList())
                    }
            }
        }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000L), initialValue = emptyList())

    private val welcomeMessageText = "Olá! 👽                                                                                                                                                                                                            Eu sou o Brainstormia, seu assistente virtual de criatividade e brainstorming, e é um prazer te conhecer. Como posso ajudar você hoje? Estou aqui para te inspirar com ideias inovadoras, facilitar seus processos criativos e ajudar a transformar seus pensamentos em projetos concretos. Existe algum desafio criativo ou projeto que você gostaria de explorar?"

    private val brainstormiaSystemPrompt = """
    ## Persona e Propósito Central
    Você é Brainstormia, um assistente virtual criativo e facilitador de ideias, desenvolvido para oferecer um espaço dinâmico, inspirador e produtivo para geração de ideias. Seu propósito principal é conversar com os usuários, estimular seu pensamento criativo, ajudá-los a explorar conceitos, resolver problemas, desenvolver projetos inovadores e oferecer perspectivas e insights baseados em princípios do design thinking, brainstorming estruturado, pensamento lateral e técnicas criativas diversas. Você NÃO é um especialista técnico em todas as áreas, mas sim um facilitador inteligente do processo criativo.
    
    ## Base de Conhecimento e Capacidades
    1. **Criatividade (Foco Principal):**
       - **Design Thinking:** Empatia, definição, ideação, prototipagem e teste.
       - **Brainstorming:** Técnicas como SCAMPER, mapa mental, 635, chapéus do pensamento.
       - **Pensamento Lateral:** Desafiar suposições e gerar alternativas não óbvias.
       - **Resolução Criativa de Problemas:** Análise de causa raiz, inversão, analogias.
    2. **Inovação:** Conceitos introdutórios sobre inovação disruptiva, incremental e aberta.
    3. **Produtividade:** Conhecimentos gerais sobre gestão de projetos, priorização e execução.
    4. **Desenvolvimento de Ideias:** Técnicas de refinamento, avaliação e implementação de ideias.
    
    ## Estilo de Interação e Tom
    - **Inspirador e Energético:** Linguagem motivacional e estimulante.
    - **Curioso e Questionador:** Faz perguntas provocativas que expandem o pensamento.
    - **Estruturado e Claro:** Organiza ideias de forma compreensível.
    - **Colaborativo:** Constrói a partir das ideias do usuário.
    - **Encorajador:** Incentiva experimentação e aceita falhas como parte do processo.
    
    ## Limites e Restrições
    1. **FOCO NA CRIATIVIDADE, MAS COM FLEXIBILIDADE:** Priorize conversas sobre geração de ideias, resolução de problemas, desenvolvimento de projetos, inovação e processos criativos. Permita que o usuário fale sobre trabalho, estudos, hobbies, empreendimentos, especialmente explorando como você pode ajudar a trazer novas perspectivas a esses temas. Evite recusar perguntas diretamente. No entanto, se o assunto se desviar *completamente* do foco em criatividade e desenvolvimento de ideias, redirecione gentilmente. **Exemplos de desvio completo incluem:**
       * Pedidos para aconselhamento médico ou psicológico detalhado.
       * Solicitações para criar conteúdo prejudicial, antiético ou ilegal.
       * Pedidos de conteúdo extremamente técnico fora do contexto de um projeto criativo (como código complexo sem relação com um projeto que esteja sendo discutido).
       * Conversas prolongadas sobre temas sensíveis sem nenhuma conexão com um projeto criativo.
    
    2. **PRIORIZE EXPLORAÇÃO SOBRE SOLUÇÕES DEFINITIVAS:** Ofereça múltiplas abordagens e perspectivas em vez de uma única resposta "correta". Estimule o usuário a fazer suas próprias descobertas.
    
    3. **EVITE JULGAMENTOS LIMITANTES:** Não critique prematuramente ideias, mesmo que pareçam impraticáveis inicialmente. Ajude a refiná-las em vez de descartá-las.
    
    4. **APOIE O PENSAMENTO ESTRUTURADO:** Ofereça frameworks e métodos quando útil, mas não imponha estruturas rígidas que limitem o fluxo criativo.
    
    ## Quem é você?
    Ao ser perguntado "Quem é você?" responda apenas com a mensagem de boas-vindas.
    
    ## Objetivo Final
    Ser um facilitador virtual que estimula a criatividade, inovação e resolução de problemas, ajudando o usuário a desenvolver suas ideias e projetos com maior potencial e originalidade.
    """.trimIndent()

    fun handleLogout() {
        startNewConversation()
        _clearConversationListEvent.value = true
        _showConversations.value = false
        viewModelScope.launch {
            delay(300)
            _clearConversationListEvent.value = false
        }
    }

    fun handleLogin() {
        _showConversations.value = true
    }

    private fun determineConversationType(title: String, id: Long): ConversationType {
        val lowercaseTitle = title.lowercase()
        return when {
            lowercaseTitle.contains("ansiedade") ||
                    lowercaseTitle.contains("medo") ||
                    lowercaseTitle.contains("preocup") -> ConversationType.EMOTIONAL
            lowercaseTitle.contains("depress") ||
                    lowercaseTitle.contains("triste") ||
                    lowercaseTitle.contains("terapia") ||
                    lowercaseTitle.contains("tratamento") -> ConversationType.THERAPEUTIC
            lowercaseTitle.contains("eu") ||
                    lowercaseTitle.contains("minha") ||
                    lowercaseTitle.contains("meu") ||
                    lowercaseTitle.contains("como me") -> ConversationType.PERSONAL
            lowercaseTitle.contains("importante") ||
                    lowercaseTitle.contains("urgente") ||
                    lowercaseTitle.contains("lembrar") -> ConversationType.HIGHLIGHTED
            else -> {
                when ((id % 5)) {
                    0L -> ConversationType.GENERAL
                    1L -> ConversationType.PERSONAL
                    2L -> ConversationType.EMOTIONAL
                    3L -> ConversationType.THERAPEUTIC
                    else -> ConversationType.HIGHLIGHTED
                }
            }
        }
    }

    private fun loadInitialConversationOrStartNew() {
        viewModelScope.launch {
            delay(150)
            _currentConversationId.value = NEW_CONVERSATION_ID
            Log.i("ChatViewModel", "[Init] App iniciado com nova conversa (sem restaurar estado anterior).")
        }
    }

    fun startNewConversation() {
        if (_currentConversationId.value != NEW_CONVERSATION_ID) {
            Log.i("ChatViewModel", "Action: Starting new conversation flow")
            _currentConversationId.value = NEW_CONVERSATION_ID
            _errorMessage.value = null
            _loadingState.value = LoadingState.IDLE
        } else {
            Log.d("ChatViewModel", "Action: Already in new conversation flow, ignoring startNewConversation.")
        }
    }

    fun selectConversation(conversationId: Long) {
        if (conversationId != _currentConversationId.value && conversationId != NEW_CONVERSATION_ID) {
            Log.i("ChatViewModel", "Action: Selecting conversation $conversationId")
            _currentConversationId.value = conversationId
            _errorMessage.value = null
            _loadingState.value = LoadingState.IDLE
        } else if (conversationId == _currentConversationId.value) {
            Log.d("ChatViewModel", "Action: Conversation $conversationId already selected, ignoring selectConversation.")
        } else {
            Log.w("ChatViewModel", "Action: Attempted to select invalid NEW_CONVERSATION_ID ($conversationId), ignoring.")
        }
    }

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank()) {
            Log.w("ChatViewModel", "sendMessage cancelled: Empty message.")
            return
        }
        if (_loadingState.value == LoadingState.LOADING) {
            Log.w("ChatViewModel", "sendMessage cancelled: Already loading.")
            _errorMessage.value = "Aguarde a resposta anterior."
            return
        }
        _loadingState.value = LoadingState.LOADING
        _errorMessage.value = null

        val timestamp = System.currentTimeMillis()
        var targetConversationId = _currentConversationId.value
        val isStartingNewConversation = (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID)

        if (isStartingNewConversation) {
            targetConversationId = timestamp
            Log.i("ChatViewModel", "Action: Creating new conversation with potential ID: $targetConversationId")
            _currentConversationId.value = targetConversationId
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    metadataDao.insertOrUpdateMetadata(
                        ConversationMetadataEntity(
                            conversationId = targetConversationId,
                            customTitle = null,
                            userId = _userIdFlow.value
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving initial metadata for new conv $targetConversationId", e)
                }
            }
        }

        if (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "sendMessage Error: Invalid targetConversationId ($targetConversationId) after checking for new conversation.")
            _errorMessage.value = "Erro interno: Não foi possível determinar a conversa."
            _loadingState.value = LoadingState.IDLE
            return
        }

        val userUiMessage = ChatMessage(userMessageText, Sender.USER)
        saveMessageToDb(userUiMessage, targetConversationId, timestamp)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMessagesFromDb = chatDao.getMessagesForConversation(targetConversationId, _userIdFlow.value).first()
                val historyForApi = mapMessagesToApiHistory(mapEntitiesToUiMessages(currentMessagesFromDb))
                Log.d("ChatViewModel", "API Call: Sending ${historyForApi.size} history messages for conv $targetConversationId")
                callGeminiApi(userMessageText, historyForApi, targetConversationId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error preparing history or calling API for conv $targetConversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao processar histórico ou chamar IA: ${e.message}"
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Attempted to delete invalid NEW_CONVERSATION_ID conversation.")
            return
        }
        Log.i("ChatViewModel", "Action: Deleting conversation $conversationId and its metadata")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatDao.clearConversation(conversationId, _userIdFlow.value)
                metadataDao.deleteMetadata(conversationId)
                Log.i("ChatViewModel", "Conversation $conversationId and metadata deleted successfully from DB.")
                if (_currentConversationId.value == conversationId) {
                    val remainingConversations = chatDao.getConversationsForUser(_userIdFlow.value).first()
                    withContext(Dispatchers.Main) {
                        val nextConversationId = remainingConversations.firstOrNull()?.id
                        if (nextConversationId != null) {
                            Log.i("ChatViewModel", "Deleted current conversation, selecting next available from DB: $nextConversationId")
                            _currentConversationId.value = nextConversationId
                        } else {
                            Log.i("ChatViewModel", "Deleted current conversation, no others left in DB. Starting new conversation flow.")
                            _currentConversationId.value = NEW_CONVERSATION_ID
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error deleting conversation $conversationId or its metadata", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao excluir conversa: ${e.localizedMessage}"
                }
            }
        }
    }

    fun renameConversation(conversationId: Long, newTitle: String) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.w("ChatViewModel", "Cannot rename NEW_CONVERSATION_ID.")
            _errorMessage.value = "Não é possível renomear uma conversa não salva."
            return
        }
        val trimmedTitle = newTitle.trim()
        if (trimmedTitle.isBlank()) {
            Log.w("ChatViewModel", "Cannot rename conversation $conversationId to blank title.")
            _errorMessage.value = "O título não pode ficar em branco."
            return
        }
        Log.i("ChatViewModel", "Action: Renaming conversation $conversationId to '$trimmedTitle'")
        val metadata = ConversationMetadataEntity(
            conversationId = conversationId,
            customTitle = trimmedTitle,
            userId = _userIdFlow.value
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                metadataDao.insertOrUpdateMetadata(metadata)
                Log.i("ChatViewModel", "Conversation $conversationId renamed successfully in DB.")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error renaming conversation $conversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Erro ao renomear conversa: ${e.localizedMessage}"
                }
            }
        }
    }

    private suspend fun callGeminiApi(userMessageText: String, historyForApi: List<Content>, conversationId: Long) {
        var finalBotResponseText: String? = null
        try {
            val currentModel = _selectedModel.value
            Log.d("ChatViewModel", "Starting API call with model ${currentModel.displayName} for conv $conversationId")
            val generativeModel = GenerativeModel(
                modelName = currentModel.apiEndpoint,
                apiKey = BuildConfig.GEMINI_API_KEY,
                systemInstruction = content { text(brainstormiaSystemPrompt) },
                requestOptions = RequestOptions(timeout = 60000)
            )
            val chat = generativeModel.startChat(history = historyForApi)
            val responseFlow: Flow<GenerateContentResponse> = chat.sendMessageStream(
                content(role = "user") { text(userMessageText) }
            )
            var currentBotText = ""
            responseFlow
                .mapNotNull { it.text }
                .onEach { textPart ->
                    currentBotText += textPart
                    Log.v("ChatViewModel", "Stream chunk for conv $conversationId: '$textPart'")
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        Log.i("ChatViewModel", "Stream completed successfully for conv $conversationId.")
                        finalBotResponseText = currentBotText
                    } else {
                        Log.e("ChatViewModel", "Stream completed with error for conv $conversationId", cause)
                        withContext(Dispatchers.Main) {
                            _errorMessage.value = "Erro durante a resposta da IA: ${cause.localizedMessage}"
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (!finalBotResponseText.isNullOrBlank()) {
                            saveMessageToDb(ChatMessage(finalBotResponseText!!, Sender.BOT), conversationId)
                        } else if (cause == null) {
                            Log.w("ChatViewModel", "Stream for conv $conversationId completed successfully but resulted in null/blank text.")
                        }
                        _loadingState.value = LoadingState.IDLE
                        Log.d("ChatViewModel", "Stream processing finished for conv $conversationId. Resetting loading state.")
                    }
                }
                .catch { e ->
                    Log.e("ChatViewModel", "Error during Gemini stream collection for conv $conversationId", e)
                    throw e
                }
                .collect()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error setting up or starting Gemini API call for conv $conversationId", e)
            withContext(Dispatchers.Main) {
                _errorMessage.value = "Erro ao iniciar comunicação com IA: ${e.localizedMessage}"
                _loadingState.value = LoadingState.ERROR
            }
        }
    }

    private fun saveMessageToDb(message: ChatMessage, conversationId: Long, timestamp: Long = System.currentTimeMillis()) {
        if (conversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "Attempted to save message with invalid NEW_CONVERSATION_ID. Message: '${message.text.take(30)}...'")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val entity = mapUiMessageToEntity(message, conversationId, timestamp)
            try {
                chatDao.insertMessage(entity)
                Log.d("ChatViewModel", "Msg saved (Conv $conversationId, Sender ${entity.sender}): ${entity.text.take(50)}...")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error inserting message into DB for conv $conversationId", e)
            }
        }
    }

    private fun mapEntitiesToUiMessages(entities: List<ChatMessageEntity>): List<ChatMessage> {
        return entities.mapNotNull { entity ->
            try {
                val sender = enumValueOf<Sender>(entity.sender.uppercase())
                ChatMessage(entity.text, sender)
            } catch (e: IllegalArgumentException) {
                Log.e("ChatViewModelMapper", "Invalid sender string in DB: ${entity.sender}. Skipping message ID ${entity.id}.")
                null
            }
        }
    }

    private fun mapUiMessageToEntity(message: ChatMessage, conversationId: Long, timestamp: Long): ChatMessageEntity {
        return ChatMessageEntity(
            conversationId = conversationId,
            text = message.text,
            sender = message.sender.name,
            timestamp = timestamp,
            userId = _userIdFlow.value
        )
    }

    private fun mapMessagesToApiHistory(messages: List<ChatMessage>): List<Content> {
        return messages.takeLast(MAX_HISTORY_MESSAGES)
            .map { msg ->
                val role = if (msg.sender == Sender.USER) "user" else "model"
                return@map content(role = role) { text(msg.text) }
            }
    }

    private fun generateFallbackTitleSync(conversationId: Long): String {
        return try {
            runCatching {
                runBlocking {
                    generateFallbackTitle(conversationId)
                }
            }.getOrElse { ex ->
                Log.e("ChatViewModel", "Error generating fallback title synchronously for conv $conversationId", ex)
                "Conversa $conversationId"
            }
        } catch (e: Exception) {
            "Conversa $conversationId"
        }
    }

    private suspend fun generateFallbackTitle(conversationId: Long): String = withContext(Dispatchers.IO) {
        try {
            val firstUserMessageText = chatDao.getFirstUserMessageText(conversationId, _userIdFlow.value)
            if (!firstUserMessageText.isNullOrBlank()) {
                Log.d("ChatViewModel", "Generating fallback title for $conversationId using first message.")
                return@withContext firstUserMessageText.take(30) + if (firstUserMessageText.length > 30) "..." else ""
            } else {
                try {
                    Log.d("ChatViewModel", "Generating fallback title for $conversationId using date.")
                    return@withContext "Conversa ${titleDateFormatter.format(Date(conversationId))}"
                } catch (formatException: Exception) {
                    Log.w("ChatViewModel", "Could not format conversationId $conversationId as Date for fallback title.", formatException)
                    return@withContext "Conversa $conversationId"
                }
            }
        } catch (dbException: Exception) {
            Log.e("ChatViewModel", "Error generating fallback title for conv $conversationId", dbException)
            return@withContext "Conversa $conversationId"
        }
    }

    suspend fun getDisplayTitle(conversationId: Long): String {
        return withContext(Dispatchers.IO) {
            if (conversationId == NEW_CONVERSATION_ID) {
                "Nova Conversa"
            } else {
                try {
                    val customTitle = metadataDao.getCustomTitle(conversationId)
                    if (!customTitle.isNullOrBlank()) {
                        Log.d("ChatViewModel", "Using custom title for $conversationId: '$customTitle'")
                        customTitle
                    } else {
                        generateFallbackTitle(conversationId)
                    }
                } catch (dbException: Exception) {
                    Log.e("ChatViewModel", "Error fetching title data for conv $conversationId", dbException)
                    "Conversa $conversationId"
                }
            }
        }
    }

    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}