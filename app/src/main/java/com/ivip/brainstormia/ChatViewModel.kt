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
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

enum class LoadingState { IDLE, LOADING, ERROR }

const val NEW_CONVERSATION_ID = -1L
private const val MAX_HISTORY_MESSAGES = 20

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    // API Clients
    private val openAIClient = OpenAIClient(BuildConfig.OPENAI_API_KEY)
    private val googleAIClient = GoogleAIClient(BuildConfig.GOOGLE_API_KEY)
    private val anthropicClient = AnthropicClient(BuildConfig.ANTHROPIC_API_KEY)

    private val auth = FirebaseAuth.getInstance()
    private val appDb = AppDatabase.getDatabase(application)
    private val chatDao: ChatDao = appDb.chatDao()
    private val metadataDao: ConversationMetadataDao = appDb.conversationMetadataDao()
    private val modelPreferenceDao: ModelPreferenceDao = appDb.modelPreferenceDao()
    private val context = application.applicationContext

    // Image generation manager
    private val imageGenerationManager = ImageGenerationManager(application)

    // Image generation state flows
    private val _imageGenerationState = MutableStateFlow<ImageGenerationResult?>(null)
    val imageGenerationState: StateFlow<ImageGenerationResult?> = _imageGenerationState.asStateFlow()

    private val _generatedImageUri = MutableStateFlow<Uri?>(null)
    val generatedImageUri: StateFlow<Uri?> = _generatedImageUri.asStateFlow()

    private val _isGeneratingImage = MutableStateFlow(false)
    val isGeneratingImage: StateFlow<Boolean> = _isGeneratingImage.asStateFlow()

    private val _imageSavedEvent = MutableSharedFlow<String>() // Emitirá a mensagem de sucesso/erro
    val imageSavedEvent: SharedFlow<String> = _imageSavedEvent.asSharedFlow()

    // List of available models
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
            isPremium = true
        ),
        AIModel(
            id = "o4-mini",
            displayName = "GPT o4 Mini",
            apiEndpoint = "o4-mini",
            provider = AIProvider.OPENAI,
            isPremium = true
        )
    )

    private val defaultModel = AIModel(
        id = "gpt-4o",
        displayName = "GPT-4o",
        apiEndpoint = "gpt-4o",
        provider = AIProvider.OPENAI,
        isPremium = false
    )

    private val _selectedModel = MutableStateFlow(defaultModel)
    val selectedModel: StateFlow<AIModel> = _selectedModel

    // Adicione esta propriedade ao ChatViewModel
    private val _isImageGenerating = MutableStateFlow(false)
    val isImageGenerating: StateFlow<Boolean> = _isImageGenerating.asStateFlow()

    // Adicione esta propriedade para armazenar o prompt durante a geração
    private val _currentImagePrompt = MutableStateFlow<String?>(null)
    val currentImagePrompt: StateFlow<String?> = _currentImagePrompt.asStateFlow()

    fun selectModel(model: AIModel) {
        // Clear previous error messages
        _errorMessage.value = null

        // Check if user is logged in
        val currentUserId = _userIdFlow.value
        if (currentUserId.isBlank() || currentUserId == "local_user") {
            Log.w("ChatViewModel", "Attempted to select model without user login")
            _errorMessage.value = context.getString(R.string.error_login_required)
            return
        }

        // Check if user has permission to use premium model
        if (model.isPremium && !_isPremiumUser.value) {
            _errorMessage.value = context.getString(R.string.error_premium_required)

            // Find default non-premium model (GPT-4o)
            val defaultModel = availableModels.find { it.id == "gpt-4o" } ?: defaultModel

            // Force model update with more aggressive approach
            viewModelScope.launch {
                try {
                    // 1. Reset model to null (non-existent)
                    withContext(Dispatchers.Main) {
                        (_selectedModel as MutableStateFlow).value = AIModel(
                            id = "resetting",
                            displayName = context.getString(R.string.reset_model),
                            apiEndpoint = "",
                            provider = AIProvider.OPENAI,
                            isPremium = false
                        )
                    }

                    // 2. Small delay to ensure UI updates
                    delay(100)

                    // 3. Set default model
                    withContext(Dispatchers.Main) {
                        (_selectedModel as MutableStateFlow).value = defaultModel
                    }

                    // 4. Save preference to database
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = currentUserId,
                            selectedModelId = defaultModel.id
                        )
                    )

                    Log.i("ChatViewModel", "Model reverted to default: ${defaultModel.displayName}")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving default model preference", e)
                }
            }
            return
        }

        // Case of premium user or free model
        if (model.id != _selectedModel.value.id) {
            viewModelScope.launch {
                try {
                    // Reset and set approach to ensure UI updates
                    withContext(Dispatchers.Main) {
                        // 1. Reset
                        (_selectedModel as MutableStateFlow).value = AIModel(
                            id = "changing",
                            displayName = context.getString(R.string.changing_model),
                            apiEndpoint = "",
                            provider = AIProvider.OPENAI,
                            isPremium = false
                        )

                        // 2. Small delay
                        delay(100)

                        // 3. Set new model
                        (_selectedModel as MutableStateFlow).value = model
                    }

                    // 4. Save to database
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = currentUserId,
                            selectedModelId = model.id
                        )
                    )

                    Log.i("ChatViewModel", "Model preference saved: ${model.displayName}")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving model preference", e)
                    _errorMessage.value = context.getString(R.string.error_save_model, e.localizedMessage)
                }
            }
        }
    }

    fun generateImage(prompt: String, quality: String = "standard", size: String = "1024x1024", transparent: Boolean = false) {
        // Check if user is authenticated
        val currentUserId = _userIdFlow.value
        if (currentUserId.isBlank() || currentUserId == "local_user") {
            _errorMessage.value = context.getString(R.string.error_login_required)
            return
        }

        // Get premium status
        val isPremium = _isPremiumUser.value
        Log.d("ChatViewModel", "Starting image generation, user premium status: $isPremium")

        // Encontrar o modelo específico que queremos usar
        val imageModel = availableModels.find { it.id == "dall-e-3" }
            ?: AIModel(
                id = "dall-e-3",
                displayName = "DALL-E 3",
                apiEndpoint = "dall-e-3",
                provider = AIProvider.OPENAI,
                isPremium = true
            )

        Log.d("ChatViewModel", "Using image model: ${imageModel.displayName} (${imageModel.apiEndpoint})")

        // Atualizar estados para mostrar o carregamento na UI
        _isImageGenerating.value = true
        _currentImagePrompt.value = prompt
        _isGeneratingImage.value = true
        _imageGenerationState.value = ImageGenerationResult.Loading("Iniciando geração de imagem...")

        viewModelScope.launch {
            try {
                // Collect result from image generation
                // Passando o modelo específico para o imageGenerationManager
                imageGenerationManager.generateAndSaveImage(
                    openAIClient = openAIClient,
                    prompt = prompt,
                    quality = quality,
                    size = size,
                    transparent = transparent,
                    isPremiumUser = isPremium,
                    modelId = imageModel.apiEndpoint // Novo parâmetro especificando o modelo
                ).collect { result ->
                    Log.d("ChatViewModel", "Image generation update: $result")
                    _imageGenerationState.value = result

                    // Also display errors in the main error UI
                    if (result is ImageGenerationResult.Error) {
                        Log.e("ChatViewModel", "Image generation error: ${result.message}")
                        _errorMessage.value = result.message
                    }

                    if (result is ImageGenerationResult.Success) {
                        try {
                            var effectiveConversationId = _currentConversationId.value
                            val userId = _userIdFlow.value // Get current user ID

                            if (effectiveConversationId == null || effectiveConversationId == NEW_CONVERSATION_ID) {
                                val newConvTimestampId = System.currentTimeMillis()
                                _currentConversationId.value = newConvTimestampId // Critical: Update the ViewModel's current conversation ID
                                effectiveConversationId = newConvTimestampId

                                // Save metadata for this new conversation
                                // This ensures it's recognized as a proper conversation
                                withContext(Dispatchers.IO) {
                                    metadataDao.insertOrUpdateMetadata(
                                        ConversationMetadataEntity(
                                            conversationId = newConvTimestampId,
                                            customTitle = null, // Title can be generated later based on prompt or first interaction
                                            userId = userId
                                        )
                                    )
                                }
                                Log.d("ChatViewModel", "Image generation initiated a new conversation. ID: $newConvTimestampId")
                            }

                            // Save the generated image URI
                            _generatedImageUri.value = result.imageUri
                            Log.d("ChatViewModel", "Image generated successfully at: ${result.imagePath}")

                            // Verify the file exists
                            val file = File(result.imagePath)
                            Log.d("ChatViewModel", "Image file exists: ${file.exists()}, size: ${file.length()}")

                            // Send the image as a bot message
                            val imageMessage = """
                ![Imagem Gerada](${result.imagePath})
                
                *Imagem gerada com base no prompt:* 
                "${prompt}"
                """.trimIndent()

                            Log.d("ChatViewModel", "Creating bot message with image path: ${result.imagePath}")

                            val botMessageEntity = ChatMessageEntity(
                                id = 0,
                                conversationId = effectiveConversationId!!,
                                text = imageMessage,
                                sender = Sender.BOT.name,
                                timestamp = System.currentTimeMillis(),
                                userId = _userIdFlow.value
                            )

                            // Insert message and verify - usando withContext para garantir execução completa
                            val messageId = withContext(Dispatchers.IO) {
                                chatDao.insertMessage(botMessageEntity)
                            }
                            Log.d("ChatViewModel", "Bot message with image inserted with ID: $messageId")

                            // Emitir evento de mensagem adicionada para atualizar a UI
                            _messageAddedEvent.emit(Unit)

                            // Atualizar explicitamente a UI
                            withContext(Dispatchers.Main) {
                                // Forçar atualização da UI
                                val currentId = _currentConversationId.value
                                if (currentId != null) {
                                    // Apenas notificar que uma mensagem foi adicionada
                                    // sem mudar o ID da conversa
                                    _messageAddedEvent.emit(Unit)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Error processing successful image", e)
                            _errorMessage.value = "Erro ao processar imagem: ${e.localizedMessage}"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in image generation", e)
                _imageGenerationState.value = ImageGenerationResult.Error("Erro: ${e.localizedMessage ?: "Erro desconhecido"}")
                _errorMessage.value = "Erro na geração de imagem: ${e.localizedMessage ?: "Erro desconhecido"}"
            } finally {
                // Limpar os estados de carregamento quando terminar
                _isImageGenerating.value = false
                _currentImagePrompt.value = null
                _isGeneratingImage.value = false
            }
        }
    }
    // Function to check if user is premium via BillingViewModel singleton
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

                // Observe premium status changes from BillingViewModel
                launch {
                    billingViewModel.isPremiumUser.collect { isPremiumFromBilling ->
                        // Update our state based on BillingViewModel result
                        Log.d("ChatViewModel", "BillingViewModel reported premium status: $isPremiumFromBilling")
                        _isPremiumUser.value = isPremiumFromBilling
                        validateCurrentModel(isPremiumFromBilling)
                    }
                }

                // Force check in BillingViewModel
                Log.d("ChatViewModel", "Forcing check with BillingViewModel")
                billingViewModel.checkUserSubscription()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error checking premium with BillingViewModel", e)

                // In case of failure, check via Firebase as backup
                val db = Firebase.firestore
                db.collection("premium_users")
                    .document(email)
                    .get()
                    .addOnSuccessListener { document ->
                        val isPremium = document.exists() && (document.getBoolean("isPremium") == true)
                        val productId = document.getString("productId")

                        Log.d("ChatViewModel", "Fallback Firebase check: isPremium=$isPremium, productId=$productId")

                        // Check if product ID is old "vitalicio"
                        val isOldVitalicioId = productId?.equals("vitalicio", ignoreCase = true) == true

                        if (isPremium && isOldVitalicioId) {
                            // If it's an old ID, don't trust it
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

    fun saveImageToGallery(imagePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(imagePath)
                if (!file.exists()) {
                    Log.e("ChatViewModel", "File to save does not exist: $imagePath")
                    _imageSavedEvent.emit(context.getString(R.string.error_file_not_found_for_saving))
                    return@launch
                }

                val resolver = context.contentResolver
                val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val imageName = file.name
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png") // Ou o tipo correto se souber
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StormChat") // Salva na pasta Pictures/StormChat
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val imageUri = resolver.insert(imageCollection, contentValues)

                if (imageUri == null) {
                    Log.e("ChatViewModel", "Failed to create new MediaStore record.")
                    _imageSavedEvent.emit(context.getString(R.string.error_saving_image_generic))
                    return@launch
                }

                var outputStream: OutputStream? = null
                try {
                    outputStream = resolver.openOutputStream(imageUri)
                    if (outputStream == null) {
                        throw IOException("Failed to get output stream.")
                    }
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                    }
                    Log.d("ChatViewModel", "Image saved to gallery: $imageUri")
                    _imageSavedEvent.emit(context.getString(R.string.image_saved_to_gallery, "Pictures/StormChat"))
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error copying file to MediaStore: ${e.message}", e)
                    // Se falhar, tenta remover a entrada pendente
                    resolver.delete(imageUri, null, null)
                    _imageSavedEvent.emit(context.getString(R.string.error_saving_image_generic))
                } finally {
                    outputStream?.close()
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving image to gallery: ${e.message}", e)
                _imageSavedEvent.emit(context.getString(R.string.error_saving_image_generic))
            }
        }
    }

    // New method to validate current model based on premium status
    private fun validateCurrentModel(isPremium: Boolean) {
        if (!isPremium && _selectedModel.value.isPremium) {
            // Non-premium user using premium model
            // Return to default model
            val defaultModel = availableModels.find { it.id == "gpt-4o" } ?: defaultModel

            viewModelScope.launch {
                try {
                    // Update selected model
                    _selectedModel.value = defaultModel
                    Log.i("ChatViewModel", "Non-premium user. Reverting to default model: ${defaultModel.displayName}")

                    // Update preference in database
                    modelPreferenceDao.insertOrUpdatePreference(
                        ModelPreferenceEntity(
                            userId = _userIdFlow.value,
                            selectedModelId = defaultModel.id
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving default model preference", e)
                }
            }
        }
    }

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser: StateFlow<Boolean> = _isPremiumUser

    // Expose model list
    val modelOptions: List<AIModel> = availableModels

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Voice recognition states
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun startListening() {
        _isListening.value = true
        // Timeout to stop listening after 30 seconds
        viewModelScope.launch {
            delay(30000)
            stopListening()
        }
    }

    fun stopListening() {
        _isListening.value = false
    }

    // Method to handle voice recognition result
    fun handleVoiceInput(text: String) {
        stopListening()
        // Recognized text will be sent as a regular message
        // You can process it here before sending to the service
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

    /* ─── Readiness flag ─────────────────────────────────────────────── */

    // 1) internal mutable flag
    private val _isReady = MutableStateFlow(false)

    // 2) public flag for external observers
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        // Initial premium status check
        checkIfUserIsPremium()

        // Observe premium status changes to validate selected model
        viewModelScope.launch {
            _isPremiumUser.collect { isPremium ->
                Log.d("ChatViewModel", "Premium status changed: $isPremium")
                validateCurrentModel(isPremium)
            }
        }

        // Premium status check and selected model validation
        viewModelScope.launch {
            // First, load user's model preference
            modelPreferenceDao.getModelPreference(_userIdFlow.value)
                .collect { preference ->
                    if (preference != null) {
                        val savedModel = availableModels.find { it.id == preference.selectedModelId }
                        if (savedModel != null) {
                            // Check if user is premium or if model doesn't require premium
                            if (!savedModel.isPremium || _isPremiumUser.value) {
                                _selectedModel.value = savedModel
                                Log.i("ChatViewModel", "Loaded user model preference: ${savedModel.displayName}")
                            } else {
                                // User is not premium but trying to use a premium model
                                // Force reverting to default GPT-4o model
                                val defaultModel = availableModels.find { it.id == "gpt-4o" } ?: defaultModel
                                _selectedModel.value = defaultModel
                                Log.i("ChatViewModel", "User is not premium. Reverting to default model: ${defaultModel.displayName}")

                                // Update preference in database to default model
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

        // wait for "new conversation" creation or any task
        loadInitialConversationOrStartNew()
        _isReady.value = true          // <- READY ✔

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

    private val welcomeMessageText = getApplication<Application>().getString(R.string.welcome_message)

    // Evento disparado sempre que uma nova mensagem é salva no BD
    private val _messageAddedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val messageAddedEvent: SharedFlow<Unit> = _messageAddedEvent.asSharedFlow()

    private val brainstormiaSystemPrompt = """
    ############################################
    #           STORMCHAT – SYSTEM PROMPT       #
    ############################################
    
    ## 1. Essence
    You are **StormChat**, a free‑thinking creativity agent wielding the candor of Nietzsche's "hammer." Your mission is to solve any problem the user presents with clear, impactful, and actionable answers.
    
    ## 2. Practical Mission
    Provide direct solutions, original insights, and incisive questions that break fragile assumptions and guide the user to a concrete outcome.
    
    ## 3. Core Values
    1. **Clarity with Depth** – explain without shallow simplification.  
    2. **Creativity with Impact** – bold yet relevant analogies and examples.  
    3. **Intellectual Courage** – question certainties and present alternative views.  
    4. **Immediate Utility** – deliver executable steps or practical recommendations.  
    5. **User Respect** – adapt tone and detail to the user's context and goals.
    
    ## 4. Interaction Style
    - **Free Flow** – maintain context, weaving ideas across the conversation.  
    - **Critical Hammer Blows** – spotlight fragile premises and propose questions to test them.  
    - **Tailored References** – cite science, art, technology, business, or pop culture only when they add real value.  
    - **No Empty Jargon** – favor direct language; acknowledge uncertainties when relevant.
    
    ## 5. Language
    Respond primarily in the user's language. If the user switches languages, adapt immediately while preserving the same level of clarity and impact. Treat all languages equally; default to English only when unsure.
    
    ## 6. Safety & Ethics
    - Do not provide medical, legal, or financial advice without clear disclaimers.  
    - Never invent data or use non‑existent quotations.  
    - When information is missing, direct the user to trustworthy sources or research paths.
    
    ## 7. Default Identity
    If asked "Who are you?" reply:  
    > **I'm StormChat ⚡ — here to generate ideas and solutions with precision.**
    
    ## 8. Expected Outcome
    The user leaves the conversation **impressed**, equipped with fresh perspectives and a **concrete action plan** — whether it's an algorithm, a study roadmap, a business pitch, or a practical life insight.
    
    ## 9. Formatting Instructions
    When separating sections in your text, use one of these formats:
    1. Markdown headings: ## ⚡ 4. Section Name
    2. Bold text: **⚡ 4. Section Name**
    
    Never use sequences of characters like "──────────" to create visual separators, as this breaks markdown formatting.
    """

    private fun autoGenerateConversationTitle(conversationId: Long, userMessage: String, botResponse: String) {
        // Don't generate title for new or invalid conversation
        if (conversationId == NEW_CONVERSATION_ID) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingTitle = metadataDao.getCustomTitle(conversationId)

                if (!existingTitle.isNullOrBlank()) {
                    // Already has a custom title, no need to generate
                    Log.d("ChatViewModel", "Conversation $conversationId already has a custom title: '$existingTitle'")
                    return@launch
                }

                Log.d("ChatViewModel", "Generating automatic title after first interaction for conversation $conversationId")

                // Build specific prompt to create title based only on the first interaction
                val promptText = "Based on this first interaction, create a short descriptive title (3-4 words) for this conversation in English.\n\n" +
                        "User: $userMessage\n\n" +
                        "Assistant: ${botResponse.take(200)}\n\n" +
                        "Title (only the title, no quotes or other text):"

                // Use a lightweight model by default to save tokens
                val titleModelId = when (_selectedModel.value.provider) {
                    AIProvider.OPENAI -> "gpt-4o-mini"
                    AIProvider.GOOGLE -> "gemini-2.0-flash"
                    AIProvider.ANTHROPIC -> "claude-3-5-sonnet-20241022"
                }

                var titleResponse = ""

                // Use the most appropriate client based on the selected model's provider
                when (_selectedModel.value.provider) {
                    AIProvider.OPENAI -> {
                        openAIClient.generateChatCompletion(
                            modelId = titleModelId,
                            systemPrompt = "You generate short descriptive titles for conversations. Respond ONLY with the title in English, without explanations or prefixes like 'Title:' or quotes.",
                            userMessage = promptText,
                            historyMessages = emptyList() // We don't need history since we have the context in the prompt
                        ).collect { chunk -> titleResponse += chunk }
                    }
                    AIProvider.GOOGLE -> {
                        googleAIClient.generateChatCompletion(
                            modelId = titleModelId,
                            systemPrompt = "You generate short descriptive titles for conversations. Respond ONLY with the title in English, without explanations or prefixes like 'Title:' or quotes.",
                            userMessage = promptText,
                            historyMessages = emptyList()
                        ).collect { chunk -> titleResponse += chunk }
                    }
                    AIProvider.ANTHROPIC -> {
                        anthropicClient.generateChatCompletion(
                            modelId = titleModelId,
                            systemPrompt = "You generate short descriptive titles for conversations. Respond ONLY with the title in English, without explanations or prefixes like 'Title:' or quotes.",
                            userMessage = promptText,
                            historyMessages = emptyList()
                        ).collect { chunk -> titleResponse += chunk }
                    }
                }

                // Clean and validate the generated title
                val cleanedTitle = titleResponse.trim()
                    .replace(Regex("^['\"](.*)['\"]$"), "$1") // Remove quotes
                    .replace(Regex("^Title: ?"), "") // Remove "Title: " prefix if it exists
                    .replace(Regex("^Theme: ?"), "") // Remove "Theme: " prefix if it exists
                    .replace("\n", " ") // Remove line breaks
                    .take(50) // Maximum character limit

                if (cleanedTitle.isNotBlank()) {
                    Log.i("ChatViewModel", "Automatic title generated after first interaction: '$cleanedTitle' for conversation $conversationId")

                    // Save the custom title to the database
                    // We use insertOrUpdateMetadata which is already implemented and working
                    metadataDao.insertOrUpdateMetadata(
                        ConversationMetadataEntity(
                            conversationId = conversationId,
                            customTitle = cleanedTitle,
                            userId = _userIdFlow.value
                        )
                    )
                }

            } catch (e: Exception) {
                // In case of error, just log and continue
                Log.e("ChatViewModel", "Error generating automatic title: ${e.message}", e)
            }
        }
    }

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
            Log.i("ChatViewModel", "[Init] App started with new conversation (without restoring previous state).")
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

    // Dentro da sua classe ChatViewModel

    // Dentro da sua classe ChatViewModel

    fun sendMessage(userMessageText: String) {
        if (userMessageText.isBlank()) {
            Log.w("ChatViewModel", "sendMessage cancelled: Empty message.")
            return
        }

        // --- LÓGICA DE COMANDO DE IMAGEM ---
        val imageCommandPrefix = "/imagine " // Defina seu prefixo. O espaço no final é útil.

        if (userMessageText.startsWith(imageCommandPrefix, ignoreCase = true)) {
            val imagePrompt = userMessageText.substring(imageCommandPrefix.length).trim()

            if (imagePrompt.isNotBlank()) {
                Log.d("ChatViewModel", "Image generation command detected. Prompt: '$imagePrompt'")

                // Chama a função de gerar imagem.
                // A função generateImage já lida com a criação de nova conversa se necessário.
                generateImage(prompt = imagePrompt)

                // Salva a mensagem de comando do usuário no banco de dados.
                viewModelScope.launch {
                    // Um pequeno delay para dar tempo a generateImage de potencialmente atualizar
                    // _currentConversationId.value se uma nova conversa foi criada.
                    delay(300)
                    val conversationIdForUserCommand = _currentConversationId.value
                    val userIdForUserCommand = _userIdFlow.value

                    if (conversationIdForUserCommand != null && conversationIdForUserCommand != NEW_CONVERSATION_ID) {
                        saveMessageToDb( // ESTA É A SUA ÚNICA FUNÇÃO saveMessageToDb
                            uiMessage = com.ivip.brainstormia.ChatMessage(userMessageText, Sender.USER),
                            conversationId = conversationIdForUserCommand,
                            timestamp = System.currentTimeMillis() - 500 // Um pouco antes da imagem do bot
                        )
                        Log.d("ChatViewModel", "User's image command '$userMessageText' saved to conversation $conversationIdForUserCommand")
                    } else {
                        Log.w("ChatViewModel", "Could not save user's image command: Invalid conversationId ($conversationIdForUserCommand) after image generation call.")
                    }
                }
                // A UI (ChatScreen) é responsável por limpar o userMessage após o envio.
            } else {
                // USA A STRING RESOURCE CORRIGIDA
                _errorMessage.value = context.getString(R.string.error_prompt_required_after_command, imageCommandPrefix.trim())
            }
            return // Importante: Não processar como mensagem de texto normal
        }
        // --- FIM DA LÓGICA DE COMANDO DE IMAGEM ---

        // Lógica original de envio de mensagem de texto (se não for comando de imagem)
        if (_loadingState.value == LoadingState.LOADING) {
            Log.w("ChatViewModel", "sendMessage cancelled: Already loading (text message).")
            _errorMessage.value = context.getString(R.string.error_wait_previous)
            return
        }
        _loadingState.value = LoadingState.LOADING
        _errorMessage.value = null

        val timestamp = System.currentTimeMillis()
        var targetConversationId = _currentConversationId.value
        val userId = _userIdFlow.value
        val isStartingNewConversation = (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID)

        if (isStartingNewConversation) {
            targetConversationId = timestamp
            Log.i("ChatViewModel", "Action: Creating new conversation for text message with ID: $targetConversationId for user $userId")
            _currentConversationId.value = targetConversationId
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    metadataDao.insertOrUpdateMetadata(
                        ConversationMetadataEntity(
                            conversationId = targetConversationId!!,
                            customTitle = null,
                            userId = userId
                        )
                    )
                    Log.d("ChatViewModel", "Initial metadata saved for new conversation $targetConversationId")
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error saving initial metadata for new conv $targetConversationId", e)
                }
            }
        }

        if (targetConversationId == null || targetConversationId == NEW_CONVERSATION_ID) {
            Log.e("ChatViewModel", "sendMessage Error: Invalid targetConversationId ($targetConversationId) after new conversation logic for text message.")
            _errorMessage.value = context.getString(R.string.error_internal_conversation)
            _loadingState.value = LoadingState.IDLE
            return
        }

        val userUiMessage = com.ivip.brainstormia.ChatMessage(userMessageText, Sender.USER)
        saveMessageToDb(userUiMessage, targetConversationId!!, timestamp) // ESTA É A SUA ÚNICA FUNÇÃO saveMessageToDb

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentMessagesFromDb = chatDao.getMessagesForConversation(targetConversationId!!, userId).first()
                val historyMessages = mapEntitiesToUiMessages(currentMessagesFromDb)
                    .takeLast(MAX_HISTORY_MESSAGES)

                Log.d("ChatViewModel", "API Call (Text): Sending ${historyMessages.size} messages to API for conv $targetConversationId using model ${_selectedModel.value.displayName}")
                callOpenAIApi(userMessageText, historyMessages, targetConversationId!!)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error preparing history or calling text API for conv $targetConversationId", e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = context.getString(R.string.error_process_history, e.message)
                    _loadingState.value = LoadingState.ERROR
                }
            }
        }
    }

    // ... (suas outras funções: deleteConversation, renameConversation, callOpenAIApi, mappers, etc.)
    // A função saveMessageToDb que você já tem:
    private fun saveMessageToDb(uiMessage: com.ivip.brainstormia.ChatMessage, conversationId: Long, timestamp: Long) {
        val entity = mapUiMessageToEntity(uiMessage, conversationId, timestamp)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                chatDao.insertMessage(entity)
                // Emitir evento aqui se a lista de mensagens na UI não estiver sendo atualizada automaticamente
                // ao salvar a mensagem do usuário ANTES da resposta do bot.
                // No entanto, o _messageAddedEvent é geralmente para após a resposta do BOT.
                // A reatividade do Flow do Room deve cuidar da mensagem do usuário.
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error saving message to DB", e)
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
                "Starting API call with model ${currentModel.displayName} (${currentModel.provider}) for conv $conversationId"
            )

            var responseText = StringBuilder()
            var modelUsed = currentModel

            withContext(Dispatchers.IO) {
                try {
                    // Choose client based on provider
                    val result = when (currentModel.provider) {
                        AIProvider.OPENAI -> {
                            Log.d("ChatViewModel", "Using OpenAI client")
                            withTimeoutOrNull(200000) {
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
                            Log.d("ChatViewModel", "Using Google client")
                            withTimeoutOrNull(200000) {
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
                            Log.d("ChatViewModel", "Using Anthropic client")
                            withTimeoutOrNull(300000) { // Give Claude more time
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

                    // Check for timeout
                    if (result == null) {
                        Log.w("ChatViewModel", "Timeout with model ${currentModel.id}")

                        // Try to use backup model (GPT-4o) only for OpenAI models
                        if (currentModel.provider == AIProvider.OPENAI && currentModel.id != "gpt-4o") {
                            responseText.clear()
                            Log.w("ChatViewModel", "Using backup model (GPT-4o)")

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
                                throw Exception("Timeout in API call (second attempt)")
                            }
                        } else {
                            throw Exception("Timeout in API call")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Error in API call: ${e.message}")
                    throw e
                }
            }

            // Response processing
            val finalResponse = responseText.toString()
            if (finalResponse.isNotBlank()) {
                Log.d("ChatViewModel", "API response received for conv $conversationId (${finalResponse.length} characters)")

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
                        Log.d("ChatViewModel", "Bot message saved to database")
                        if (historyMessages.size <= 1 ||
                            (conversationId != NEW_CONVERSATION_ID && historyMessages.count { it.sender == Sender.USER } == 0)) {
                            // It's the first interaction, generate automatic title
                            autoGenerateConversationTitle(conversationId, userMessageText, finalResponse)
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error saving bot message to database", e)
                    }
                }
            } else {
                Log.w("ChatViewModel", "Empty response from API for conv $conversationId")
                withContext(Dispatchers.Main) {
                    _errorMessage.value = context.getString(R.string.error_empty_response)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error in API call for conv $conversationId", e)
            withContext(Dispatchers.Main) {
                if (e.message?.contains("Timeout") == true) {
                    _errorMessage.value = context.getString(R.string.error_timeout)
                } else {
                    _errorMessage.value = context.getString(R.string.error_ai_communication, e.localizedMessage)
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



    companion object {
        private val titleDateFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}