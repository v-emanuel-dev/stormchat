package com.ivip.brainstormia

import com.ivip.brainstormia.data.models.AIModel
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainstormia.ConversationType
import com.google.firebase.Firebase
import com.ivip.brainstormia.data.db.AppDatabase
import com.ivip.brainstormia.data.db.ChatDao
import com.ivip.brainstormia.data.db.ChatMessageEntity
import com.ivip.brainstormia.data.db.ConversationInfo
import com.ivip.brainstormia.data.db.ConversationMetadataDao
import com.ivip.brainstormia.data.db.ConversationMetadataEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
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
import com.ivip.brainstormia.data.models.AIProvider
import kotlinx.coroutines.withTimeoutOrNull

enum class LoadingState { IDLE, LOADING, ERROR }

const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    // Cliente OpenAI
    private val openAIClient = OpenAIClient(BuildConfig.OPENAI_API_KEY)
    private val googleAIClient = GoogleAIClient(BuildConfig.GOOGLE_API_KEY) // Adicionar esta linha
    private val anthropicClient = AnthropicClient(BuildConfig.ANTHROPIC_API_KEY) // Adicionar esta linha

    private val auth = FirebaseAuth.getInstance()
    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao()
    private val modelPreferenceDao: ModelPreferenceDao = appDb.modelPreferenceDao()


    // Lista de modelos disponíveis (OpenAI)

    val availableModels = listOf(
        // Anthropic
        AIModel(
            id = "claude-3-7-sonnet-20250219",
            displayName = "Claude 3.7 Sonnet",
            apiEndpoint = "claude-3-7-sonnet-20250219",
            provider = AIProvider.ANTHROPIC,
            isPremium = true
        ),
        AIModel(
            id = "claude-3-5-sonnet-20241022",
            displayName = "Claude 3.5 Sonnet",
            apiEndpoint = "claude-3-5-sonnet-20241022",
            provider = AIProvider.ANTHROPIC,
            isPremium = true
        ),

        // Google Gemini
        AIModel(
            id = "gemini-2.5-pro",
            displayName = "Gemini 2.5 Pro",
            apiEndpoint = "gemini-2.5-pro-exp-03-25",
            provider = AIProvider.GOOGLE,
            isPremium = true
        ),
        AIModel(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            apiEndpoint = "gemini-2.5-flash-preview-04-17",
            provider = AIProvider.GOOGLE,
            isPremium = true
        ),
        AIModel(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            apiEndpoint = "gemini-2.0-flash",
            provider = AIProvider.GOOGLE,
            isPremium = false
        ),

        // OpenAI
        AIModel(
            id = "gpt-4.1",
            displayName = "GPT-4.1",
            apiEndpoint = "gpt-4.1",
            provider = AIProvider.OPENAI,
            isPremium = true
        ),
        AIModel(
            id = "gpt-4o",
            displayName = "GPT-4o",
            apiEndpoint = "gpt-4o",
            provider = AIProvider.OPENAI,
            isPremium = false
        ),
        AIModel(
            id = "gpt-4.5-preview",
            displayName = "GPT-4.5 Preview",
            apiEndpoint = "gpt-4.5-preview",
            provider = AIProvider.OPENAI,
            isPremium = true
        ),
        AIModel(
            id = "o1",
            displayName = "GPT o1",
            apiEndpoint = "o1",
            provider = AIProvider.OPENAI,
            isPremium = true
        ),
        AIModel(
            id = "o3",
            displayName = "GPT o3",
            apiEndpoint = "o3",
            provider = AIProvider.OPENAI,
            isPremium = true
        ),
        AIModel(
            id = "o3-mini",
            displayName = "GPT o3 Mini",
            apiEndpoint = "o3-mini",
            provider = AIProvider.OPENAI,
            isPremium = false
        ),
        AIModel(
            id = "o4-mini",
            displayName = "GPT o4 Mini",
            apiEndpoint = "o4-mini",
            provider = AIProvider.OPENAI,
            isPremium = false
        )
    )

    private val defaultModel = AIModel(
        id = "gpt-4o",
        displayName = "GPT-4o",
        apiEndpoint = "gpt-4o",
        provider = AIProvider.OPENAI,
        isPremium = false // ou true se quiser forçar premium
    )

    private val _selectedModel = MutableStateFlow(defaultModel)
    val selectedModel: StateFlow<AIModel> = _selectedModel

    // Método para atualizar o modelo selecionado
    fun selectModel(model: AIModel) {
        // Limpar qualquer mensagem de erro anterior
        _errorMessage.value = null

        // Verificar se o usuário está logado
        val currentUserId = _userIdFlow.value
        if (currentUserId.isBlank() || currentUserId == "local_user") {
            Log.w("ChatViewModel", "Tentativa de selecionar modelo sem usuário logado")
            _errorMessage.value = "Você precisa estar logado para alterar o modelo"
            return
        }

        // Verificar se o usuário tem permissão para usar o modelo premium
        if (model.isPremium && !_isPremiumUser.value) {
            _errorMessage.value = "Este modelo requer assinatura premium. Usando GPT-4o."

            // Encontrar o modelo padrão não-premium (GPT-4o)
            val defaultModel = availableModels.find { it.id == "gpt-4o" } ?: defaultModel

            // Forçar a atualização do modelo com uma abordagem mais agressiva
            viewModelScope.launch {
                try {
                    // 1. Primeiro, vamos resetar completamente o modelo para null (não existe)
                    withContext(Dispatchers.Main) {
                        (_selectedModel as MutableStateFlow).value = AIModel(
                            id = "resetting",
                            displayName = "Resetando...",
                            apiEndpoint = "",
                            provider = AIProvider.OPENAI,
                            isPremium = false
                        )
                    }

                    // 2. Pequeno delay para garantir que a UI seja atualizada
                    delay(100)

                    // 3. Agora definir o modelo padrão
                    withContext(Dispatchers.Main) {
                        (_selectedModel as MutableStateFlow).value = defaultModel
                    }

                    // 4. Salvar a preferência no banco de dados
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = currentUserId,
                            selectedModelId = defaultModel.id
                        )
                    )

                    Log.i("ChatViewModel", "Modelo revertido para padrão: ${defaultModel.displayName}")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Erro ao salvar preferência de modelo padrão", e)
                }
            }
            return
        }

        // Caso de usuário premium ou modelo gratuito
        if (model.id != _selectedModel.value.id) {
            viewModelScope.launch {
                try {
                    // Abordagem de reset e set para garantir que a UI atualize
                    withContext(Dispatchers.Main) {
                        // 1. Resetar
                        (_selectedModel as MutableStateFlow).value = AIModel(
                            id = "changing",
                            displayName = "Alterando...",
                            apiEndpoint = "",
                            provider = AIProvider.OPENAI,
                            isPremium = false
                        )

                        // 2. Pequeno delay
                        delay(100)

                        // 3. Definir novo modelo
                        (_selectedModel as MutableStateFlow).value = model
                    }

                    // 4. Salvar no banco de dados
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = currentUserId,
                            selectedModelId = model.id
                        )
                    )

                    Log.i("ChatViewModel", "Preferência de modelo salva: ${model.displayName}")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Erro ao salvar preferência de modelo", e)
                    _errorMessage.value = "Erro ao salvar preferência de modelo: ${e.localizedMessage}"
                }
            }
        }
    }

    // Função atualizada para verificar status premium via singleton BillingViewModel
    fun checkIfUserIsPremium() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email

        if (email.isNullOrBlank()) {
            Log.e("ChatViewModel", "Cannot check premium status: No logged in user or email")
            _isPremiumUser.value = false
            // Force verification of the current model
            validateCurrentModel(false)
            return
        }

        // Add debugging to track the process
        Log.d("ChatViewModel", "Checking premium status for user: $email")

        // Get singleton BillingViewModel through application
        viewModelScope.launch {
            try {
                // Get the BillingViewModel singleton through the application
                val app = getApplication<Application>() as BrainstormiaApplication
                val billingViewModel = app.billingViewModel

                // Observe the premium status changes from BillingViewModel
                launch {
                    billingViewModel.isPremiumUser.collect { isPremiumFromBilling ->
                        // Atualizar nosso estado baseado no resultado do BillingViewModel
                        Log.d("ChatViewModel", "BillingViewModel reportou premium status: $isPremiumFromBilling")
                        _isPremiumUser.value = isPremiumFromBilling
                        validateCurrentModel(isPremiumFromBilling)
                    }
                }

                // Agora, forçar verificação no BillingViewModel
                Log.d("ChatViewModel", "Forçando verificação com BillingViewModel")
                billingViewModel.checkUserSubscription()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error checking premium with BillingViewModel", e)

                // Em caso de falha, verificar via Firebase como backup
                val db = Firebase.firestore
                db.collection("premium_users")
                    .document(email)
                    .get()
                    .addOnSuccessListener { document ->
                        val isPremium = document.exists() && (document.getBoolean("isPremium") == true)
                        val productId = document.getString("productId")

                        Log.d("ChatViewModel", "Fallback Firebase check: isPremium=$isPremium, productId=$productId")

                        // Verificar se o ID do produto é o antigo "vitalicio"
                        val isOldVitalicioId = productId?.equals("vitalicio", ignoreCase = true) == true

                        if (isPremium && isOldVitalicioId) {
                            // Se for um ID antigo, não confiamos nele
                            Log.w("ChatViewModel", "Firebase indicates Vitalício with old ID. NOT trusting it.")
                            _isPremiumUser.value = false
                        } else {
                            _isPremiumUser.value = isPremium
                        }

                        validateCurrentModel(_isPremiumUser.value)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ChatViewModel", "Error with Firebase fallback: ${e.message}", e)
                        _isPremiumUser.value = false
                        validateCurrentModel(false)
                    }
            }
        }
    }

    // Novo método para validar o modelo atual com base no status premium
    private fun validateCurrentModel(isPremium: Boolean) {
        if (!isPremium && _selectedModel.value.isPremium) {
            // Usuário não premium usando modelo premium
            // Retorna ao modelo padrão
            val defaultModel = availableModels.find { it.id == "gpt-4o" } ?: defaultModel

            viewModelScope.launch {
                try {
                    // Atualiza o modelo selecionado
                    _selectedModel.value = defaultModel
                    Log.i("ChatViewModel", "Usuário não premium. Revertendo para o modelo padrão: ${defaultModel.displayName}")

                    // Atualiza a preferência no banco de dados
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = _userIdFlow.value,
                            selectedModelId = defaultModel.id
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Erro ao salvar a preferência do modelo padrão", e)
                }
            }
        }
    }

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser: StateFlow<Boolean> = _isPremiumUser

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

    /* ─── Flag de prontidão ─────────────────────────────────────────────── */

    // 1) flag interna mutável
    private val _isReady = MutableStateFlow(false)

    // 2) flag pública para quem observa do lado de fora
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        // Verificação inicial de premium status
        checkIfUserIsPremium()

        // Observe mudanças no status premium para validar o modelo selecionado
        viewModelScope.launch {
            _isPremiumUser.collect { isPremium ->
                Log.d("ChatViewModel", "Premium status changed: $isPremium")
                validateCurrentModel(isPremium)
            }
        }

        // Verificação de status premium e validação do modelo selecionado
        viewModelScope.launch {
            // Primeiro, carrega a preferência do modelo do usuário
            modelPreferenceDao.getModelPreference(_userIdFlow.value)
                .collect { preference ->
                    if (preference != null) {
                        val savedModel = availableModels.find { it.id == preference.selectedModelId }
                        if (savedModel != null) {
                            // Verifica se o usuário é premium ou se o modelo não requer premium
                            if (!savedModel.isPremium || _isPremiumUser.value) {
                                _selectedModel.value = savedModel
                                Log.i("ChatViewModel", "Loaded user model preference: ${savedModel.displayName}")
                            } else {
                                // Usuário não é premium, mas está tentando usar um modelo premium
                                // Forçamos a reversão para o modelo padrão GPT-4o
                                val defaultModel = availableModels.find { it.id == "gpt-4o" } ?: defaultModel
                                _selectedModel.value = defaultModel
                                Log.i("ChatViewModel", "User is not premium. Reverting to default model: ${defaultModel.displayName}")

                                // Atualiza a preferência no banco para o modelo padrão
                                modelPreferenceDao.insertOrUpdatePreference(
                                    ModelPreferenceEntity(
                                        userId = _userIdFlow.value,
                                        selectedModelId = defaultModel.id
                                    )
                                )
                            }
                        }
                    }
                }
        }

        // aguarda a criação da "nova conversa" ou qualquer tarefa
        loadInitialConversationOrStartNew()
        _isReady.value = true          // <- PRONTO ✔

        auth.addAuthStateListener { firebaseAuth ->
            val newUser = firebaseAuth.currentUser
            val newUserId = newUser?.uid ?: "local_user"
            val previousUserId = _userIdFlow.value

            Log.d("ChatViewModel", "Auth state changed: $previousUserId -> $newUserId")

            if (newUserId != previousUserId) {
                viewModelScope.launch {
                    if (newUser != null) {
                        // User logged in
                        Log.d("ChatViewModel", "User logged in: $newUserId")
                        _userIdFlow.value = newUserId
                        _showConversations.value = true

                        // Try to load conversations with delay
                        delay(300)
                        forceLoadConversationsAfterLogin()
                    } else {
                        // User logged out
                        Log.d("ChatViewModel", "User logged out")
                        _userIdFlow.value = "local_user"
                        _showConversations.value = false
                        _currentConversationId.value = NEW_CONVERSATION_ID
                    }
                }
            }
        }

        loadInitialConversationOrStartNew()
    }

    private fun getCurrentUserId(): String =
        FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"

    private val rawConversationsFlow: Flow<List<ConversationInfo>> =
        _userIdFlow.flatMapLatest { uid ->
            Log.d("ChatViewModel", "Initializing rawConversationsFlow for user: $uid")
            if (uid.isBlank()) {
                Log.w("ChatViewModel", "Empty user ID in rawConversationsFlow, emitting empty list")
                flowOf(emptyList())
            } else {
                chatDao.getConversationsForUser(uid)
                    .onStart {
                        Log.d("ChatViewModel", "Starting to collect conversations for user: $uid")
                    }
                    .onEmpty {
                        Log.d("ChatViewModel", "No conversations found for user: $uid")
                    }
                    .catch { e ->
                        Log.e("ChatViewModel", "Error loading raw conversations flow for user: $uid", e)
                        _errorMessage.value = "Erro ao carregar lista de conversas (raw)."
                        emit(emptyList())
                    }
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

    val messages: StateFlow<List<com.ivip.brainstormia.ChatMessage>> =
        _currentConversationId.flatMapLatest { convId ->
            Log.d("ChatViewModel", "[State] CurrentConversationId changed: $convId")
            when (convId) {
                null, NEW_CONVERSATION_ID -> {
                    flowOf(listOf(com.ivip.brainstormia.ChatMessage(welcomeMessageText, Sender.BOT)))
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

    private val welcomeMessageText = "Olá! Eu sou o StormChat 👽, seu assistente virtual de criatividade e sabedoria. Estou aqui para inspirar suas ideias, compartilhar conhecimento prático e ajudar você a encontrar soluções inteligentes. Como posso impulsionar sua mente hoje?"

    private val brainstormiaSystemPrompt = """
    ## Persona e Propósito
    
    Você é **Brainstormia**, um companheiro virtual erudito, inspirador e curioso. Seu propósito é ajudar o usuário a explorar ideias, esclarecer dúvidas, conectar conhecimentos e transformar informação em sabedoria prática. Você pode conversar sobre qualquer assunto, sempre oferecendo respostas ricas em contexto, referências e provocações intelectuais.
    
    ## Estilo de Interação
    
    Você combina clareza com profundidade. Seu estilo é:
    
    - **Esclarecedor:** Explica conceitos com simplicidade sem perder rigor.
    - **Reflexivo:** Estimula pensamento crítico e múltiplas perspectivas.
    - **Inspirador:** Incentiva a curiosidade, o aprendizado e a criatividade.
    - **Didático e adaptável:** Ajusta a linguagem e o nível da explicação ao perfil do usuário.
    - **Respeitoso e colaborativo:** Constrói ideias junto ao usuário, sem impor opiniões.
    
    Evite respostas excessivamente fragmentadas, curtas ou robóticas. Prefira continuidade e encadeamento lógico entre mensagens. Quando o usuário engajar por várias interações, mantenha o contexto e aprofunde, em vez de reiniciar o raciocínio.
    
    ## Base de Conhecimento
    
    Você pode abordar qualquer tema, mas seu diferencial é conectar os assuntos às ideias de grandes pensadores, teorias e tradições. Costuma enriquecer as conversas fazendo referências, por exemplo, a:
    
    - **Filosofia:** Sócrates, Platão, Aristóteles, estoicismo, Nietzsche, Sartre, Beauvoir, Confúcio, Buda.
    - **Psicologia:** Freud, Jung, Rogers, Maslow, Skinner, Beck, Piaget, Vygotsky, Seligman, entre outros.
    - **Ciência:** ideias de Einstein, Darwin, Curie, Hawking, além do método científico.
    - **Literatura & Arte:** movimentos culturais, autores e obras relevantes para a humanidade.
    - **Tecnologia & Inovação:** Turing, Lovelace, cultura digital, pensamento computacional.
    - **Negócios & Liderança:** visão estratégica, tomada de decisão, criatividade aplicada.
    - **Desenvolvimento Pessoal:** sabedoria prática, reflexão sobre bem-estar, propósito, hábitos e sentido da vida.
    
    Essa base não é exaustiva nem restritiva. Você pode dialogar sobre qualquer tema contemporâneo, histórico, técnico ou subjetivo, adaptando-se ao interesse e nível do usuário.
    
    ## Limites e Responsabilidade
    
    - **Não impõe verdades absolutas.** Prefere explorar possibilidades e contrastar visões.
    - **Reconhece limites.** Quando necessário, sugere fontes externas ou destaca incertezas.
    - **Evita jargões desnecessários.** Busca clareza, não exibicionismo.
    - **Nunca inventa citações.** Se não souber de onde vem algo, diga honestamente.
    
    ## Identidade
    
    Se perguntarem "Quem é você?", responda:
    
    > **Olá! Eu sou o Brainstormia 👽, seu assistente virtual de criatividade e sabedoria.**
    
    ## Objetivo Final
    
    Promover o crescimento intelectual do usuário. Ajudá-lo a ver o mundo de formas novas, tomar decisões mais conscientes e aplicar o conhecimento com propósito e imaginação.
    """
    fun handleLogin() {
        Log.d("ChatViewModel", "handleLogin() called - user=${_userIdFlow.value}")
        _selectedModel.value = defaultModel
        // Make sure conversations are visible
        _showConversations.value = true

        // Force reload of conversations with multiple attempts
        viewModelScope.launch {
            // First attempt
            val currentUserId = getCurrentUserId()
            Log.d("ChatViewModel", "handleLogin: reloading conversations for user $currentUserId")

            // Reset the flow to force recomposition
            _userIdFlow.value = ""
            delay(50)
            _userIdFlow.value = currentUserId

            // Check after a short delay if conversations loaded
            delay(500)
            if (conversationListForDrawer.value.isEmpty() && auth.currentUser != null) {
                Log.w("ChatViewModel", "First attempt failed, trying second refresh")

                // Second attempt with longer delay
                refreshConversationList()

                // One final check with longer delay
                delay(1000)
                if (conversationListForDrawer.value.isEmpty() && auth.currentUser != null) {
                    Log.w("ChatViewModel", "Second attempt failed, forcing DB query")

                    // Last resort - direct DB query
                    try {
                        val userId = getCurrentUserId()
                        val conversations = withContext(Dispatchers.IO) {
                            chatDao.getConversationsForUser(userId).first()
                        }
                        Log.d("ChatViewModel", "Direct DB query found ${conversations.size} conversations")

                        // Force one more refresh
                        _userIdFlow.value = ""
                        delay(50)
                        _userIdFlow.value = userId
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error in direct DB query", e)
                    }
                }
            }
        }
    }

    // Add this method to ChatViewModel
    fun forceLoadConversationsAfterLogin() {
        viewModelScope.launch {
            Log.d("ChatViewModel", "Force loading conversations after login")

            // Ensure we're showing conversations
            _showConversations.value = true

            // Make sure we have the correct user ID
            val currentUser = FirebaseAuth.getInstance().currentUser
            val userId = currentUser?.uid

            if (userId == null) {
                Log.e("ChatViewModel", "Cannot load conversations - no user ID available")
                return@launch
            }

            // Log current and new user ID for debugging
            val previousUserId = _userIdFlow.value
            Log.d("ChatViewModel", "User ID transition: $previousUserId -> $userId")

            // Force update user ID with delay to ensure database operations complete
            _userIdFlow.value = ""
            delay(100) // This is now inside a coroutine so it works
            _userIdFlow.value = userId

            // Rest of the function...
        }
    }


    fun handleLogout() {
        startNewConversation()
        _selectedModel.value = defaultModel
        _clearConversationListEvent.value = true
        _showConversations.value = false
        viewModelScope.launch {
            delay(300)
            _clearConversationListEvent.value = false
        }
    }

    fun refreshConversationList() {
        viewModelScope.launch {
            Log.d("ChatViewModel", "Explicitly refreshing conversation list")

            // Clear and reset events
            _clearConversationListEvent.value = true
            delay(100)
            _clearConversationListEvent.value = false

            // Force reload by updating user ID flow
            val currentUserId = getCurrentUserId()
            _userIdFlow.value = ""
            delay(50)
            _userIdFlow.value = currentUserId

            // Log the current state
            Log.d("ChatViewModel", "Refreshed conversation list for user ${_userIdFlow.value}")
        }
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

        val userUiMessage = com.ivip.brainstormia.ChatMessage(userMessageText, Sender.USER)
        saveMessageToDb(userUiMessage, targetConversationId, timestamp)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMessagesFromDb = chatDao.getMessagesForConversation(targetConversationId, _userIdFlow.value).first()
                val historyMessages = mapEntitiesToUiMessages(currentMessagesFromDb)

                Log.d("ChatViewModel", "API Call: Enviando ${historyMessages.size} mensagens para a API para conv $targetConversationId")

                callOpenAIApi(userMessageText, historyMessages, targetConversationId)
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

    private suspend fun callOpenAIApi(
        userMessageText: String,
        historyMessages: List<ChatMessage>,
        conversationId: Long
    ) {
        try {
            val currentModel = _selectedModel.value
            Log.d(
                "ChatViewModel",
                "Iniciando chamada de API com modelo ${currentModel.displayName} (${currentModel.provider}) para conv $conversationId"
            )

            var responseText = StringBuilder()
            var modelUsed = currentModel

            withContext(Dispatchers.IO) {
                try {
                    // Escolher o cliente com base no provedor
                    val result = when (currentModel.provider) {
                        AIProvider.OPENAI -> {
                            Log.d("ChatViewModel", "Usando cliente OpenAI")
                            withTimeoutOrNull(60000) {
                                openAIClient.generateChatCompletion(
                                    modelId = currentModel.apiEndpoint,
                                    systemPrompt = brainstormiaSystemPrompt,
                                    userMessage = userMessageText,
                                    historyMessages = historyMessages
                                ).collect { chunk ->
                                    responseText.append(chunk)
                                }
                                true
                            }
                        }
                        AIProvider.GOOGLE -> {
                            Log.d("ChatViewModel", "Usando cliente Google")
                            withTimeoutOrNull(60000) {
                                googleAIClient.generateChatCompletion(
                                    modelId = currentModel.apiEndpoint,
                                    systemPrompt = brainstormiaSystemPrompt,
                                    userMessage = userMessageText,
                                    historyMessages = historyMessages
                                ).collect { chunk ->
                                    responseText.append(chunk)
                                }
                                true
                            }
                        }
                        AIProvider.ANTHROPIC -> {
                            Log.d("ChatViewModel", "Usando cliente Anthropic")
                            withTimeoutOrNull(120000) { // Damos mais tempo ao Claude
                                anthropicClient.generateChatCompletion(
                                    modelId = currentModel.apiEndpoint,
                                    systemPrompt = brainstormiaSystemPrompt,
                                    userMessage = userMessageText,
                                    historyMessages = historyMessages
                                ).collect { chunk ->
                                    responseText.append(chunk)
                                }
                                true
                            }
                        }
                    }

                    // Verificar se houve timeout
                    if (result == null) {
                        Log.w("ChatViewModel", "Timeout com modelo ${currentModel.id}")

                        // Tenta usar o modelo de backup (GPT-4o) apenas para modelos OpenAI
                        if (currentModel.provider == AIProvider.OPENAI && currentModel.id != "gpt-4o") {
                            responseText.clear()
                            Log.w("ChatViewModel", "Usando modelo de backup (GPT-4o)")

                            val backupModel = availableModels.first { it.id == "gpt-4o" }
                            modelUsed = backupModel

                            val backupResult = withTimeoutOrNull(60000) {
                                openAIClient.generateChatCompletion(
                                    modelId = backupModel.apiEndpoint,
                                    systemPrompt = brainstormiaSystemPrompt,
                                    userMessage = userMessageText,
                                    historyMessages = historyMessages
                                ).collect { chunk ->
                                    responseText.append(chunk)
                                }
                                true
                            }

                            if (backupResult == null) {
                                throw Exception("Timeout na chamada da API (segunda tentativa)")
                            }
                        } else {
                            throw Exception("Timeout na chamada da API")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Erro na chamada à API: ${e.message}")
                    throw e
                }
            }

            // Processamento da resposta
            val finalResponse = responseText.toString()
            if (finalResponse.isNotBlank()) {
                Log.d("ChatViewModel", "Resposta da API recebida para conv $conversationId (${finalResponse.length} caracteres)")

                val botMessageEntity = ChatMessageEntity(
                    id = 0,
                    conversationId = conversationId,
                    text = finalResponse,
                    sender = Sender.BOT.name,
                    timestamp = System.currentTimeMillis(),
                    userId = _userIdFlow.value
                )

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        chatDao.insertMessage(botMessageEntity)
                        Log.d("ChatViewModel", "Mensagem do bot salva no banco de dados")
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Erro ao salvar mensagem do bot no banco de dados", e)
                    }
                }
            } else {
                Log.w("ChatViewModel", "Resposta vazia da API para conv $conversationId")
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Resposta vazia da IA. Por favor, tente novamente."
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Erro na chamada à API para conv $conversationId", e)
            withContext(Dispatchers.Main) {
                if (e.message?.contains("Timeout") == true) {
                    _errorMessage.value = "A IA demorou muito para responder. Por favor, tente novamente."
                } else {
                    _errorMessage.value = "Erro ao comunicar com IA: ${e.localizedMessage}"
                }
            }
        } finally {
            withContext(Dispatchers.Main) {
                _loadingState.value = LoadingState.IDLE
            }
        }
    }

    private fun mapEntitiesToUiMessages(entities: List<ChatMessageEntity>): List<com.ivip.brainstormia.ChatMessage> {
        return entities.mapNotNull { entity ->
            try {
                val sender = enumValueOf<Sender>(entity.sender.uppercase())
                com.ivip.brainstormia.ChatMessage(entity.text, sender)
            } catch (e: IllegalArgumentException) {
                Log.e("ChatViewModelMapper", "Invalid sender string in DB: ${entity.sender}. Skipping message ID ${entity.id}.")
                null
            }
        }
    }

    private fun mapUiMessageToEntity(message: com.ivip.brainstormia.ChatMessage, conversationId: Long, timestamp: Long): ChatMessageEntity {
        return ChatMessageEntity(
            conversationId = conversationId,
            text = message.text,
            sender = message.sender.name,
            timestamp = timestamp,
            userId = _userIdFlow.value
        )
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

    private fun saveMessageToDb(uiMessage: com.ivip.brainstormia.ChatMessage, conversationId: Long, timestamp: Long) {
        val entity = mapUiMessageToEntity(uiMessage, conversationId, timestamp)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatDao.insertMessage(entity)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving message to DB", e)
            }
        }
    }

    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}