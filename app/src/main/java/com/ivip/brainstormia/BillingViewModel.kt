package com.ivip.brainstormia.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers

class BillingViewModel(application: Application) : AndroidViewModel(application), PurchasesUpdatedListener {
    private val TAG = "BillingViewModel"

    private val billingClient = BillingClient.newBuilder(application)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products = _products.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    private val _connectionAttempts = MutableStateFlow(0)

    private val handler = Handler(Looper.getMainLooper())
    private val reconnectBackoffMs = 1000L // 1 segundo inicial para reconexão

    // Os IDs de produto para assinaturas
    private val SUBSCRIPTION_IDS = listOf(
        "mensal",
        "anual",
        "vitalicio"
    )

    init {
        Log.d(TAG, "Inicializando BillingViewModel")
        startBillingConnection()
        queryAvailableProducts()
    }

    private fun startBillingConnection() {
        if (billingClient.isReady) {
            Log.d(TAG, "BillingClient já está pronto")
            queryAvailableProducts()
            return
        }

        Log.d(TAG, "Iniciando conexão com BillingClient (tentativa: ${_connectionAttempts.value + 1})")
        _connectionAttempts.value = _connectionAttempts.value + 1

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Conectado com sucesso ao BillingClient")
                    queryAvailableProducts()
                    checkUserSubscription()
                } else {
                    Log.e(TAG, "Erro na conexão: ${billingResult.responseCode} - ${billingResult.debugMessage}")

                    if (_connectionAttempts.value < 3) {
                        scheduleReconnection()
                    } else {
                        Log.w(TAG, "Máximo de tentativas atingido.")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Conexão com BillingClient perdida")
                if (_connectionAttempts.value < 3) {
                    scheduleReconnection()
                }
            }
        })
    }

    private fun scheduleReconnection() {
        val delay = reconnectBackoffMs * (1 shl (_connectionAttempts.value - 1))
        Log.d(TAG, "Agendando reconexão em $delay ms")

        handler.postDelayed({
            if (!billingClient.isReady) {
                startBillingConnection()
            }
        }, delay)
    }

    private fun queryAvailableProducts() {
        Log.d(TAG, "Consultando assinaturas (SUBS) e produtos (INAPP)")

        // 1. Consultar assinaturas (SUBS)
        val subscriptionList = listOf("mensal", "anual").map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subscriptionList)
            .build()

        billingClient.queryProductDetailsAsync(subsParams) { billingResultSubs, subsList ->
            if (billingResultSubs.responseCode == BillingClient.BillingResponseCode.OK) {

                // 2. Agora consultar produtos únicos (INAPP)
                val inAppList = listOf("vitalicio").map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }

                val inAppParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(inAppList)
                    .build()

                billingClient.queryProductDetailsAsync(inAppParams) { billingResultInApp, inAppListResult ->
                    if (billingResultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                        val allProducts = subsList + inAppListResult

                        if (allProducts.isNotEmpty()) {
                            Log.d(TAG, "Produtos encontrados: ${allProducts.size}")
                            allProducts.forEach { product ->
                                Log.d(TAG, "Produto: ${product.productId}, Título: ${product.title}")
                            }
                            _products.value = allProducts
                        } else {
                            Log.w(TAG, "Nenhum produto encontrado")
                        }

                    } else {
                        Log.e(TAG, "Erro ao buscar produtos INAPP: ${billingResultInApp.debugMessage}")
                    }
                }

            } else {
                Log.e(TAG, "Erro ao buscar produtos SUBS: ${billingResultSubs.debugMessage}")
            }
        }
    }

    // Função para permitir retry manual
    fun retryConnection() {
        Log.d(TAG, "Retry manual solicitado")
        _connectionAttempts.value = 0
        startBillingConnection()
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        if (_purchaseInProgress.value) {
            Log.w(TAG, "Compra já em andamento")
            return
        }

        _purchaseInProgress.value = true
        Log.d(TAG, "Iniciando fluxo de compra para ${productDetails.productId}")

        // Verificar se é um produto INAPP ou SUBS
        val isSubscription = productDetails.productType == BillingClient.ProductType.SUBS

        val billingFlowParamsBuilder = BillingFlowParams.newBuilder()

        if (isSubscription) {
            // Para assinaturas, precisamos do offerToken
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken == null) {
                Log.e(TAG, "Erro: offerToken não encontrado para ${productDetails.productId}")
                _purchaseInProgress.value = false
                return
            }

            billingFlowParamsBuilder.setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
        } else {
            // Para produtos únicos (INAPP), não precisamos do offerToken
            billingFlowParamsBuilder.setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
        }

        val billingFlowParams = billingFlowParamsBuilder.build()
        val responseCode = billingClient.launchBillingFlow(activity, billingFlowParams).responseCode

        if (responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Erro ao iniciar fluxo de cobrança: $responseCode")
            _purchaseInProgress.value = false
        } else {
            Log.d(TAG, "Fluxo de cobrança iniciado com sucesso")
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        Log.d(TAG, "onPurchasesUpdated: ${result.responseCode} - ${result.debugMessage}")
        _purchaseInProgress.value = false

        // Log detalhado para depuração
        if (purchases != null) {
            for (purchase in purchases) {
                Log.d(TAG, "Detalhes da compra: ID=${purchase.orderId}, Produtos=${purchase.products}, Estado=${purchase.purchaseState}")
            }
        } else {
            Log.d(TAG, "Lista de compras é nula")
        }

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    Log.d(TAG, "Compras atualizadas: ${purchases.size}")
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                } else {
                    Log.d(TAG, "Nenhuma compra para processar")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "Compra cancelada pelo usuário")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Usuário já possui esta assinatura")
                checkUserSubscription() // Atualizar estado
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                Log.e(TAG, "Serviço desconectado, tentando reconectar")
                startBillingConnection()
            }
            else -> {
                Log.e(TAG, "Erro na compra: ${result.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "Processando compra: ${purchase.products[0]}, estado: ${purchase.purchaseState}")
        Log.d(TAG, "Detalhes completos da compra: ${purchase}")

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verificar se é uma assinatura válida
            val isSubscriptionValid = isPurchaseValid(purchase)
            Log.d(TAG, "Assinatura válida? $isSubscriptionValid")

            // Persistir status localmente
            if (isSubscriptionValid) {
                Log.d(TAG, "Assinatura válida, atualizando status premium")
                savePremiumStatusLocally(true)

                // Atualizar estado no app
                _isPremiumUser.value = true

                // Atualizar no Firebase para persistência entre dispositivos
                saveUserStatusToFirebase(true)

                // Reconhecer a compra para que a Google saiba que foi processada
                if (!purchase.isAcknowledged) {
                    acknowledgePurchase(purchase)
                }
            } else {
                Log.e(TAG, "Assinatura inválida, não atualizando status")
            }
        } else {
            Log.d(TAG, "Compra em estado diferente de PURCHASED: ${purchase.purchaseState}")
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        Log.d(TAG, "Reconhecendo compra: ${purchase.orderId}")

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Compra reconhecida com sucesso")
            } else {
                Log.e(TAG, "Erro ao reconhecer compra: ${result.debugMessage}")
            }
        }
    }

    private fun isPurchaseValid(purchase: Purchase): Boolean {
        // Validação básica sem backend
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Compra não está no estado PURCHASED")
            return false
        }

        // Verificar o tipo de assinatura
        val isLifetime = purchase.products.any {
            it.contains("lifetime") || it.contains("vitalicio")
        }

        // Verificar expiração para assinaturas não-vitalícias
        return if (isLifetime) {
            // Assinatura vitalícia é sempre válida se foi comprada
            Log.d(TAG, "Assinatura vitalícia, sempre válida")
            true
        } else {
            // Para assinaturas normais, verificar se não expirou
            val notExpired = !isPurchaseExpired(purchase)
            Log.d(TAG, "Assinatura regular, expirada: ${!notExpired}")
            notExpired
        }
    }

    private fun isPurchaseExpired(purchase: Purchase): Boolean {
        // Esta é uma verificação básica para assinaturas
        // Na implementação real, você precisa verificar a data de expiração
        // que seria obtida do Google Play Developer API

        return false // Simplificado para este exemplo
    }

    private fun checkUserSubscription() {
        // Primeiro, tentar resgatar o status do Firebase
        checkFirebaseUserStatus()

        // Depois, verificar as compras recentes no dispositivo
        if (billingClient.isReady) {
            Log.d(TAG, "Verificando assinaturas ativas")

            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ) { billingResult, purchasesList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Compras encontradas: ${purchasesList.size}")

                    var hasActiveSubscription = false

                    for (purchase in purchasesList) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            !isPurchaseExpired(purchase)) {
                            hasActiveSubscription = true
                            Log.d(TAG, "Assinatura ativa encontrada: ${purchase.products[0]}")

                            // Reconhecer a compra caso ainda não tenha sido
                            if (!purchase.isAcknowledged) {
                                acknowledgePurchase(purchase)
                            }

                            // Não precisamos continuar verificando
                            break
                        }
                    }

                    // Atualizar o estado
                    _isPremiumUser.value = hasActiveSubscription

                    // Persistir localmente
                    savePremiumStatusLocally(hasActiveSubscription)

                    // Atualizar no Firebase
                    saveUserStatusToFirebase(hasActiveSubscription)

                    Log.d(TAG, "Status premium do usuário atualizado: $hasActiveSubscription")
                } else {
                    Log.e(TAG, "Erro ao verificar assinaturas: ${billingResult.debugMessage}")

                    // Usar status armazenado localmente como fallback
                    loadPremiumStatusLocally()
                }
            }
        } else {
            Log.w(TAG, "BillingClient não está pronto, usando status armazenado")
            // Billing Client não está pronto, usar status armazenado
            loadPremiumStatusLocally()

            // Tentar reconectar
            startBillingConnection()
        }
    }

    private fun saveUserStatusToFirebase(isPremium: Boolean) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "Nenhum usuário logado, não atualizando status no Firebase")
            return
        }

        val userEmail = currentUser.email ?: currentUser.uid
        Log.d(TAG, "Tentando atualizar status premium no Firebase para $userEmail: $isPremium")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = Firebase.firestore

                val userData = mapOf(
                    "isPremium" to isPremium,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "deviceId" to android.os.Build.MODEL,
                    "lastUpdatedMillis" to System.currentTimeMillis()
                )

                Log.d(TAG, "Dados a serem salvos no Firebase: $userData")

                db.collection("premium_users")
                    .document(userEmail)
                    .set(userData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Status premium atualizado no Firebase com sucesso para $userEmail")

                        // Verificar se o documento foi realmente criado
                        db.collection("premium_users")
                            .document(userEmail)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    Log.d(TAG, "Verificação: documento existe no Firebase: ${document.data}")
                                } else {
                                    Log.e(TAG, "Verificação falhou: documento não existe no Firebase após salvar")
                                }
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Erro ao atualizar status premium no Firebase: ${e.message}", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao salvar status no Firebase: ${e.message}", e)
            }
        }
    }

    private fun checkFirebaseUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "Nenhum usuário logado, não verificando status no Firebase")
            return
        }

        val userEmail = currentUser.email ?: currentUser.uid

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = Firebase.firestore
                val docRef = db.collection("premium_users").document(userEmail)

                Log.d(TAG, "Verificando status premium no Firebase para $userEmail")

                docRef.get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            val isPremium = document.getBoolean("isPremium") ?: false
                            Log.d(TAG, "Status premium carregado do Firebase: $isPremium")
                            _isPremiumUser.value = isPremium
                            savePremiumStatusLocally(isPremium)
                        } else {
                            Log.d(TAG, "Nenhum status premium encontrado no Firebase")
                            loadPremiumStatusLocally()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Erro ao verificar status premium no Firebase", e)
                        loadPremiumStatusLocally()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar status no Firebase", e)
                loadPremiumStatusLocally()
            }
        }
    }

    private fun savePremiumStatusLocally(isPremium: Boolean) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(
                "billing_prefs", Context.MODE_PRIVATE
            )
            prefs.edit()
                .putBoolean("is_premium", isPremium)
                .putLong("last_updated", System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Status premium salvo localmente: $isPremium")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar status premium localmente", e)
        }
    }

    private fun loadPremiumStatusLocally() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences(
                "billing_prefs", Context.MODE_PRIVATE
            )
            val isPremium = prefs.getBoolean("is_premium", false)
            val lastUpdated = prefs.getLong("last_updated", 0)

            // Verificar se os dados não são muito antigos (opcional)
            val now = System.currentTimeMillis()
            val isRecent = (now - lastUpdated) < 24 * 60 * 60 * 1000 // 24 horas

            if (isRecent) {
                _isPremiumUser.value = isPremium
                Log.d(TAG, "Status premium carregado localmente: $isPremium")
            } else {
                Log.d(TAG, "Status premium local expirado, mantendo valor atual")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar status premium localmente", e)
        }
    }
}