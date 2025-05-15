package com.ivip.brainstormia

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.image.ImageCreation
import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage

/**
 * Client for communicating with the OpenAI API
 */
class OpenAIClient(apiKey: String) {

    val openAI: OpenAI

    init {
        val config = OpenAIConfig(
            token = apiKey
        )
        openAI = OpenAI(config)
        Log.d(TAG, "OpenAIClient initialized")
    }

    /**
     * Generates a chat response using the OpenAI API with streaming
     */
    suspend fun generateChatCompletion(
        modelId: String,
        systemPrompt: String,
        userMessage: String,
        historyMessages: List<com.ivip.brainstormia.ChatMessage>
    ): Flow<String> = flow {
        try {
            // Convert messages from application format to OpenAI format
            val openAIMessages = mutableListOf<OpenAIChatMessage>()

            // Add system prompt
            openAIMessages.add(OpenAIChatMessage(
                role = ChatRole.System,
                content = systemPrompt
            ))

            // Add history messages (limited to MAX_HISTORY_MESSAGES)
            val recentMessages = historyMessages.takeLast(MAX_HISTORY_MESSAGES)

            for (message in recentMessages) {
                val role = if (message.sender == Sender.USER) ChatRole.User else ChatRole.Assistant
                openAIMessages.add(OpenAIChatMessage(
                    role = role,
                    content = message.text
                ))
            }

            // Add current user message
            openAIMessages.add(OpenAIChatMessage(
                role = ChatRole.User,
                content = userMessage
            ))

            Log.d(TAG, "Sending ${openAIMessages.size} messages to OpenAI using model $modelId")

            // Create request
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId(modelId),
                messages = openAIMessages
            )

            var responseText = StringBuilder()

            // API call with streaming
            openAI.chatCompletions(chatCompletionRequest).collect { completion ->
                completion.choices.firstOrNull()?.delta?.content?.let { chunk ->
                    if (chunk.isNotEmpty()) {
                        responseText.append(chunk)
                        emit(chunk)
                    }
                }
            }

            Log.d(TAG, "Complete response from OpenAI: ${responseText.length} characters")

        } catch (e: Exception) {
            Log.e(TAG, "Error in generateChatCompletion", e)
            throw e
        }
    }

    /**
     * Generate an image using the OpenAI API
     */
    suspend fun generateImage(
        prompt: String,
        quality: String = "standard",
        size: String = "1024x1024",
        outputPath: String? = null,
        transparent: Boolean = false,
        isPremiumUser: Boolean = false,
        modelId: String = "" // Novo parâmetro com valor padrão vazio
    ): Flow<String> = flow {
        try {
            emit("Gerando imagem...")
            Log.d(TAG, "Generating image with prompt: $prompt, premium user: $isPremiumUser, model: ${modelId.ifEmpty { "default" }}")

            // Determine o modelo a usar
            val effectiveModelId = if (modelId.isNotEmpty()) {
                // Usar o modelo especificado se foi fornecido
                modelId
            } else {
                // Lista de modelos de fallback se nenhum modelo específico foi fornecido
                val modelIdsToTry = listOf(
                    "dall-e-3",      // Try DALL-E 3 if available (premium)
                    "dall-e-2",      // Fallback to DALL-E 2
                )
                // Escolher o primeiro modelo da lista como padrão
                modelIdsToTry.first()
            }

            Log.d(TAG, "Using model: $effectiveModelId for image generation")
            emit("Usando modelo: ${effectiveModelId}...")

            try {
                // Criar a requisição para o modelo específico
                val imageRequest = ImageCreation(
                    prompt = prompt,
                    model = ModelId(effectiveModelId),
                    n = 1,
                    size = if (effectiveModelId == "dall-e-3") ImageSize("1024x1024") else null
                )

                // API call
                emit("Baixando imagem gerada...")
                val result = openAI.imageJSON(imageRequest)

                if (result.isNotEmpty()) {
                    // First result (we only asked for 1 image)
                    val image = result.first()

                    // Log the entire response for debugging
                    val imageString = image.toString()
                    Log.d(TAG, "Image response from $effectiveModelId: $imageString")

                    // Try different methods to extract the data
                    // Method 1: Look for base64 data in the toString() output
                    val base64Pattern = "data:image/[^\"']+".toRegex()
                    val base64Match = base64Pattern.find(imageString)
                    if (base64Match != null) {
                        val match = base64Match.value
                        Log.d(TAG, "Found base64 data in response: ${match.take(50)}...")
                        emit(match)
                        return@flow
                    }

                    // Method 2: Try to access fields via reflection
                    for (field in image::class.java.declaredFields) {
                        field.isAccessible = true
                        try {
                            val fieldValue = field.get(image)
                            if (fieldValue is String) {
                                if (fieldValue.startsWith("data:image")) {
                                    Log.d(TAG, "Found base64 data via reflection in field ${field.name}")
                                    emit(fieldValue)
                                    return@flow
                                } else if (fieldValue.length > 1000) {
                                    Log.d(TAG, "Found possible base64 data (long string) in field ${field.name}")
                                    emit(fieldValue)
                                    return@flow
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error accessing field ${field.name}", e)
                        }
                    }

                    // Method 3: Look for URL pattern in the response
                    val urlPattern = "https?://[^\"'\\s]+".toRegex()
                    val urlMatch = urlPattern.find(imageString)
                    if (urlMatch != null) {
                        val url = urlMatch.value
                        Log.d(TAG, "Found URL in response: $url")
                        emit("URL:$url")
                        return@flow
                    }

                    // Method 4: Check for specific known fields by name
                    for (fieldName in listOf("url", "b64_json", "b64Json", "base64", "data")) {
                        try {
                            val field = image::class.java.getDeclaredField(fieldName)
                            field.isAccessible = true
                            val fieldValue = field.get(image)
                            if (fieldValue != null && fieldValue is String && fieldValue.isNotEmpty()) {
                                Log.d(TAG, "Found data in field '$fieldName': ${fieldValue.take(50)}...")
                                if (fieldName == "url") {
                                    emit("URL:$fieldValue")
                                } else {
                                    emit(fieldValue)
                                }
                                return@flow
                            }
                        } catch (e: NoSuchFieldException) {
                            // Field doesn't exist, continue to next one
                        } catch (e: Exception) {
                            Log.e(TAG, "Error accessing field $fieldName", e)
                        }
                    }

                    // Method 5: As last resort, return the full stringified response
                    Log.d(TAG, "Could not extract image data properly, returning full response")
                    emit("RESPONSE:$imageString")
                    return@flow
                } else {
                    Log.w(TAG, "Empty result from model $effectiveModelId")
                    throw Exception("Resposta vazia do modelo $effectiveModelId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error with model $effectiveModelId", e)
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating image", e)
            throw Exception("Failed to generate image: ${e.message ?: "Unknown error"}")
        }
    }

    companion object {
        private const val TAG = "OpenAIClient"
        private const val MAX_HISTORY_MESSAGES = 20
    }
}