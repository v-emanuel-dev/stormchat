package com.ivip.brainstormia

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Cliente para comunicação com a API da OpenAI
 */
class OpenAIClient(apiKey: String) {

    val openAI: OpenAI

    init {
        val config = OpenAIConfig(
            token = apiKey
        )
        openAI = OpenAI(config)
        Log.d(TAG, "OpenAIClient inicializado")
    }

    /**
     * Gera uma resposta de chat usando a API da OpenAI com streaming
     */
    suspend fun generateChatCompletion(
        modelId: String,
        systemPrompt: String,
        userMessage: String,
        historyMessages: List<com.ivip.brainstormia.ChatMessage>
    ): Flow<String> = flow {
        try {
            // Converter mensagens do formato da aplicação para formato OpenAI
            val openAIMessages = mutableListOf<OpenAIChatMessage>()

            // Adicionar o prompt do sistema
            openAIMessages.add(OpenAIChatMessage(
                role = ChatRole.System,
                content = systemPrompt
            ))

            // Adicionar as mensagens do histórico (limitado a MAX_HISTORY_MESSAGES)
            val recentMessages = historyMessages.takeLast(MAX_HISTORY_MESSAGES)

            for (message in recentMessages) {
                val role = if (message.sender == Sender.USER) ChatRole.User else ChatRole.Assistant
                openAIMessages.add(OpenAIChatMessage(
                    role = role,
                    content = message.text
                ))
            }

            // Adicionar a mensagem atual do usuário
            openAIMessages.add(OpenAIChatMessage(
                role = ChatRole.User,
                content = userMessage
            ))

            Log.d(TAG, "Enviando ${openAIMessages.size} mensagens para OpenAI usando modelo $modelId")

            // Criar a requisição
            val chatCompletionRequest = ChatCompletionRequest(
                model = ModelId(modelId),
                messages = openAIMessages
            )

            var responseText = StringBuilder()

            // Fazer a chamada à API com streaming
            openAI.chatCompletions(chatCompletionRequest).collect { completion ->
                completion.choices.firstOrNull()?.delta?.content?.let { chunk ->
                    if (chunk.isNotEmpty()) {
                        responseText.append(chunk)
                        emit(chunk)
                    }
                }
            }

            Log.d(TAG, "Resposta completa da OpenAI: ${responseText.length} caracteres")

        } catch (e: Exception) {
            Log.e(TAG, "Erro em generateChatCompletion", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "OpenAIClient"
        private const val MAX_HISTORY_MESSAGES = 20
    }
}