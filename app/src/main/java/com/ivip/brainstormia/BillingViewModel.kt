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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.pow

class BillingViewModel(application: Application) : AndroidViewModel(application), PurchasesUpdatedListener {
    private val TAG = "BillingViewModel"

    private val billingClient = BillingClient.newBuilder(application)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    // Adicionando a definição que estava faltando
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _isPremiumLoading = MutableStateFlow(false)
    val isPremiumLoading = _isPremiumLoading.asStateFlow()

    private val _userPlanType = MutableStateFlow<String?>(null)
    val userPlanType = _userPlanType.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products = _products.asStateFlow()

    private val _purchaseInProgress = MutableStateFlow(false)
    val purchaseInProgress = _purchaseInProgress.asStateFlow()

    private val _connectionAttempts = MutableStateFlow(0)
    private val MAX_CONNECTION_ATTEMPTS = 5

    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null

    // Controle de verificação ativa
    private var activeCheckJob: Job? = null

    // --- IDs Atuais ---
    private val SUBSCRIPTION_IDS_ONLY = listOf("mensal", "anual")
    private val INAPP_IDS_ONLY = listOf("vital") // Usando o novo ID
    // --------------------

    // Adicionando cache para melhorar performance
    private var lastVerifiedTimestamp = 0L
    private val CACHE_VALIDITY_PERIOD = 30000L // 30 segundos
    private var isInitialCheckComplete = false

    init {
        Log.d(TAG, "Inicializando BillingViewModel")
        loadPremiumStatusLocally() // Primeiro carrega do cache local
        startBillingConnection() // Depois conecta ao serviço de billing
    }

    private fun startBillingConnection() {
        if (billingClient.isReady) {
            Log.i(TAG, "BillingClient já está pronto. Consultando produtos e compras.")
            queryAvailableProducts()
            if (!isInitialCheckComplete) {
                checkUserSubscription() // Fonte da verdade
                isInitialCheckComplete = true
            }
            return
        }
        if (_connectionAttempts.value >= MAX_CONNECTION_ATTEMPTS && !_purchaseInProgress.value) {
            Log.w(TAG, "Máximo de tentativas de conexão atingido (${MAX_CONNECTION_ATTEMPTS}).")
            _isPremiumLoading.value = false // Importante: Encerrar o loading
            return
        }
        Log.i(TAG, "Iniciando conexão com BillingClient (tentativa: ${_connectionAttempts.value + 1})")
        _connectionAttempts.value++
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                Log.d(TAG, "onBillingSetupFinished - Resposta: ${billingResult.responseCode}, Mensagem: ${billingResult.debugMessage}")
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.i(TAG, "Conectado com sucesso ao BillingClient.")
                    _connectionAttempts.value = 0
                    queryAvailableProducts()
                    if (!isInitialCheckComplete) {
                        checkUserSubscription() // Fonte da verdade
                        isInitialCheckComplete = true
                    }
                } else {
                    Log.e(TAG, "Erro na conexão com BillingClient: ${billingResult.responseCode} - ${billingResult.debugMessage}")
                    _isPremiumLoading.value = false // Importante: Encerrar o loading em caso de erro
                    if (_connectionAttempts.value < MAX_CONNECTION_ATTEMPTS) {
                        scheduleReconnection()
                    } else {
                        Log.w(TAG, "Máximo de tentativas de reconexão atingido.")
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Conexão com BillingClient perdida.")
                scheduleReconnection()
            }
        })
    }

    private fun scheduleReconnection() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        if (_connectionAttempts.value >= MAX_CONNECTION_ATTEMPTS && !_purchaseInProgress.value) {
            Log.w(TAG, "Não agendando reconexão: máximo de tentativas.")
            _isPremiumLoading.value = false // Importante: Encerrar o loading
            return
        }
        val delayMs = 1000L * (2.0.pow(_connectionAttempts.value.coerceAtMost(6) - 1)).toLong()
        Log.d(TAG, "Agendando reconexão em $delayMs ms")
        reconnectRunnable = Runnable {
            if (!billingClient.isReady) startBillingConnection()
        }
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }

    fun queryAvailableProducts() {
        if (!billingClient.isReady) {
            Log.w(TAG, "queryAvailableProducts: BillingClient não está pronto.")
            startBillingConnection()
            return
        }
        Log.i(TAG, "queryAvailableProducts (Aninhado): Consultando produtos...")
        val combinedProductList = mutableListOf<ProductDetails>()
        val subscriptionProductQueryList = SUBSCRIPTION_IDS_ONLY.mapNotNull { id -> if (id.isBlank()) null else QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.SUBS).build() }

        if (subscriptionProductQueryList.isNotEmpty()) {
            val subsParams = QueryProductDetailsParams.newBuilder().setProductList(subscriptionProductQueryList).build()
            Log.d(TAG, "Consultando SUBS com IDs: ${SUBSCRIPTION_IDS_ONLY.joinToString()}")
            billingClient.queryProductDetailsAsync(subsParams) { resSubs, subsList ->
                Log.d(TAG, "queryProductDetailsAsync SUBS CALLBACK: Resposta=${resSubs.responseCode}, Tamanho=${subsList?.size ?: "null"}")
                if (resSubs.responseCode == BillingClient.BillingResponseCode.OK && subsList != null) combinedProductList.addAll(subsList)
                else Log.e(TAG, "Erro SUBS: ${resSubs.responseCode} - ${resSubs.debugMessage}")
                queryInAppProducts(combinedProductList)
            }
        } else {
            Log.d(TAG, "Nenhum ID SUBS. Prosseguindo para INAPP.")
            queryInAppProducts(combinedProductList)
        }
    }

    private fun queryInAppProducts(currentCombinedList: MutableList<ProductDetails>) {
        val inAppProductQueryList = INAPP_IDS_ONLY.mapNotNull { id -> if (id.isBlank()) null else QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(BillingClient.ProductType.INAPP).build() }
        if (inAppProductQueryList.isNotEmpty()) {
            val inAppParams = QueryProductDetailsParams.newBuilder().setProductList(inAppProductQueryList).build()
            Log.d(TAG, "Consultando INAPP com IDs: ${INAPP_IDS_ONLY.joinToString()}")
            billingClient.queryProductDetailsAsync(inAppParams) { resInApp, inAppList ->
                Log.d(TAG, "queryProductDetailsAsync INAPP CALLBACK: Resposta=${resInApp.responseCode}, Tamanho=${inAppList?.size ?: "null"}")
                if (resInApp.responseCode == BillingClient.BillingResponseCode.OK && inAppList != null) currentCombinedList.addAll(inAppList)
                else Log.e(TAG, "Erro INAPP: ${resInApp.responseCode} - ${resInApp.debugMessage}")
                processFinalProductList(currentCombinedList)
            }
        } else {
            Log.d(TAG, "Nenhum ID INAPP. Processando lista atual.")
            processFinalProductList(currentCombinedList)
        }
    }

    private fun processFinalProductList(finalList: List<ProductDetails>) {
        if (finalList.isNotEmpty()) {
            Log.i(TAG, "Processando lista final (${finalList.size}):")
            finalList.forEach { p ->
                val price = if (p.productType == BillingClient.ProductType.SUBS)
                    p.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                else p.oneTimePurchaseOfferDetails?.formattedPrice
                Log.i(TAG, "  - ID: ${p.productId}, Nome: ${p.name}, Tipo: ${p.productType}, Preço: $price")
            }
            _products.value = finalList.sortedBy { prod ->
                when { prod.productId.contains("mensal") -> 1; prod.productId.contains("anual") -> 2; prod.productId.equals("vital", ignoreCase = true) -> 3; else -> 4 }
            }
        } else {
            Log.w(TAG, "Lista final de produtos vazia.")
            _products.value = emptyList()
        }
    }

    fun retryConnection() {
        Log.i(TAG, "Tentativa manual de reconexão e recarga de produtos solicitada.")
        _connectionAttempts.value = 0
        handler.removeCallbacksAndMessages(null)
        startBillingConnection()
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        if (!billingClient.isReady) {
            Log.e(TAG, "launchBillingFlow: BillingClient não está pronto.")
            _purchaseInProgress.value = false
            retryConnection()
            return
        }
        if (_purchaseInProgress.value) {
            Log.w(TAG, "launchBillingFlow: Compra já em andamento.")
            return
        }

        _purchaseInProgress.value = true
        Log.i(TAG, "Iniciando fluxo de compra para ${productDetails.productId}")

        val productDetailsParamsList = mutableListOf<BillingFlowParams.ProductDetailsParams>()

        if (productDetails.productType == BillingClient.ProductType.SUBS) {
            val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (offerToken.isNullOrBlank()) {
                Log.e(TAG, "Erro CRÍTICO: offerToken não encontrado para ${productDetails.productId}. A compra não pode prosseguir.")
                _purchaseInProgress.value = false
                return
            }
            Log.d(TAG, "Usando offerToken: $offerToken para ${productDetails.productId}")
            productDetailsParamsList.add(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        } else { // INAPP
            productDetailsParamsList.add(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        }

        if (productDetailsParamsList.isEmpty()) {
            Log.e(TAG, "Nenhum ProductDetailsParams construído para ${productDetails.productId}.")
            _purchaseInProgress.value = false
            return
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)

        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.e(TAG, "Erro ao iniciar fluxo de cobrança para ${productDetails.productId}: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            _purchaseInProgress.value = false
        } else {
            Log.i(TAG, "Fluxo de cobrança iniciado com sucesso para ${productDetails.productId}")
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        Log.i(TAG, "onPurchasesUpdated: Código de Resposta=${billingResult.responseCode}, Mensagem=${billingResult.debugMessage}")
        _purchaseInProgress.value = false

        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            Log.i(TAG, "Compras atualizadas com sucesso (${purchases.size} itens). Processando...")
            purchases.forEach { purchase ->
                Log.d(TAG, "Detalhes da compra: OrderId=${purchase.orderId}, Produtos=${purchase.products.joinToString()}, Estado=${purchase.purchaseState}, Token=${purchase.purchaseToken}, É Reconhecida=${purchase.isAcknowledged}")
                handlePurchase(purchase)
            }

            // Após processar uma compra bem-sucedida, força atualização imediata do cache
            lastVerifiedTimestamp = 0
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "Compra cancelada pelo usuário.")
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Log.i(TAG, "Usuário já possui este item/assinatura. Verificando status...")
            // Força atualização imediata do cache
            lastVerifiedTimestamp = 0
            checkUserSubscription()
        } else {
            Log.e(TAG, "Erro na atualização de compras: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            if (billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ||
                billingResult.responseCode == BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE) {
                scheduleReconnection()
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        Log.d(TAG, "Processando compra: ${purchase.products.joinToString()}, estado: ${purchase.purchaseState}")

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val productId = purchase.products.firstOrNull()
            val planType = determinePlanType(productId)

            Log.i(TAG, "Compra VÁLIDA para ${productId}. Atualizando status premium. Plano: $planType")
            // A atualização do estado agora é feita principalmente por checkUserSubscription após a compra ser confirmada pelo Play.
            // Mas podemos definir aqui para uma resposta mais rápida na UI, checkUserSubscription confirmará depois.
            _isPremiumUser.value = true
            _userPlanType.value = planType

            savePremiumStatusLocally(true, planType)
            saveUserStatusToFirebase(true, planType, purchase.orderId, purchase.purchaseTime, productId)

            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }

            // Atualizar o timestamp de verificação
            lastVerifiedTimestamp = System.currentTimeMillis()
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            Log.i(TAG, "Compra PENDENTE: ${purchase.products.joinToString()}. Aguardando confirmação. OrderId: ${purchase.orderId}")
        } else if (purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
            Log.w(TAG, "Compra em estado NÃO ESPECIFICADO: ${purchase.products.joinToString()}. OrderId: ${purchase.orderId}")
        } else {
            Log.d(TAG, "Compra em estado não processável (ex: ${purchase.purchaseState}): ${purchase.products.joinToString()}. OrderId: ${purchase.orderId}")
        }
    }

    private fun determinePlanType(productId: String?): String {
        return when {
            productId == null -> "Desconhecido"
            productId.equals("mensal", ignoreCase = true) -> "Mensal"
            productId.equals("anual", ignoreCase = true) -> "Anual"
            productId.equals("vital", ignoreCase = true) -> "Vitalício" // <-- USA O NOVO ID
            else -> {
                Log.w(TAG, "Tipo de plano não reconhecido para productId: $productId. Usando 'Premium'.")
                "Premium"
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.purchaseToken.isNullOrBlank()) {
            Log.e(TAG, "Token de compra nulo ou vazio. OrderId: ${purchase.orderId}")
            return
        }
        Log.i(TAG, "Reconhecendo compra: ${purchase.orderId}")
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        billingClient.acknowledgePurchase(params) { ackResult ->
            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.i(TAG, "Compra RECONHECIDA com sucesso: ${purchase.orderId}")
            } else {
                Log.e(TAG, "Erro ao RECONHECER compra ${purchase.orderId}: ${ackResult.responseCode} - ${ackResult.debugMessage}")
            }
        }
    }

    private fun isSubscriptionActive(purchase: Purchase): Boolean {
        if (purchase.products.any { it.equals("vital", ignoreCase = true) || it.equals("vitalicio", ignoreCase = true) }) {
            return purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        return purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                (purchase.isAutoRenewing || (purchase.purchaseTime + GRACE_PERIOD_MS > System.currentTimeMillis()))
    }

    private val GRACE_PERIOD_MS = 2 * 24 * 60 * 60 * 1000L // 2 dias

    fun checkUserSubscription() {
        // Cancela qualquer verificação em andamento para evitar conflitos
        activeCheckJob?.cancel()

        // Se já temos uma verificação recente, use o cache
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVerifiedTimestamp < CACHE_VALIDITY_PERIOD && lastVerifiedTimestamp > 0) {
            Log.d(TAG, "Usando cache de status premium (verificado há ${(currentTime - lastVerifiedTimestamp)/1000}s)")
            return
        }

        // Inicia o loading apenas se não estamos usando o cache
        _isPremiumLoading.value = true

        // Verifica o status atual do usuário
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            // Se não há usuário logado, definir como não premium e terminar
            _isPremiumUser.value = false
            _userPlanType.value = null
            _isPremiumLoading.value = false
            return
        }

        // Inicia a verificação de forma assíncrona com timeout
        activeCheckJob = viewModelScope.launch {
            try {
                // Obtém informações do usuário
                val currentUserEmail = currentUser.email ?: currentUser.uid
                Log.i(TAG, "--- Iniciando checkUserSubscription (Fonte da Verdade) para usuário: $currentUserEmail ---")

                // Executa a verificação com timeout
                val verificationSuccess = withTimeoutOrNull(4000) { // 4 segundos máximo
                    performUserStatusVerification(currentUserEmail)
                    true
                }

                // Se chegou ao timeout, apenas use o que temos
                if (verificationSuccess == null) {
                    Log.w(TAG, "Verificação de assinatura atingiu timeout. Usando último estado conhecido.")
                }

                // Atualiza o timestamp da última verificação
                lastVerifiedTimestamp = System.currentTimeMillis()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar assinatura: ${e.message}", e)
            } finally {
                // Garante que o estado de loading seja finalizado
                _isPremiumLoading.value = false
            }
        }
    }

    // Função que realiza a verificação propriamente dita (pode ser cancelada pelo timeout)
    private suspend fun performUserStatusVerification(currentUserEmail: String) = coroutineScope {
        // Primeiro carrega os dados locais para resposta rápida
        val localData = async(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
            val isPremiumLocal = prefs.getBoolean("is_premium", false)
            val planTypeLocal = prefs.getString("plan_type", null)
            Pair(isPremiumLocal, planTypeLocal)
        }

        // Em paralelo, inicia a verificação com Firebase
        val firebaseData = async(Dispatchers.IO) {
            try {
                val db = Firebase.firestore
                val document = db.collection("premium_users").document(currentUserEmail).get().await()

                val registeredIsPremium = document.getBoolean("isPremium") ?: false
                val registeredOrderId = document.getString("orderId")
                val registeredProductId = document.getString("productId")
                val registeredPlanType = document.getString("planType")

                Triple(registeredIsPremium, registeredOrderId, registeredPlanType)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao verificar dados do Firebase: ${e.message}", e)
                Triple(false, null, null)
            }
        }

        // Usa os dados locais para resposta imediata enquanto a verificação completa acontece
        val (localIsPremium, localPlanType) = localData.await()
        withContext(Dispatchers.Main) {
            if (localIsPremium) {
                _isPremiumUser.value = true
                _userPlanType.value = localPlanType
            }
        }

        // Continua com a verificação completa
        val (firebaseIsPremium, firebaseOrderId, firebasePlanType) = firebaseData.await()

        // Verifica as compras no billing client para confirmar o status
        if (billingClient.isReady) {
            checkBillingPurchases(currentUserEmail, firebaseIsPremium, firebaseOrderId, firebasePlanType)
        } else {
            withContext(Dispatchers.Main) {
                _isPremiumUser.value = firebaseIsPremium
                _userPlanType.value = firebasePlanType
            }
        }
    }

    // Verifica as compras no Play Store
    private suspend fun checkBillingPurchases(
        userEmail: String,
        firebaseIsPremium: Boolean,
        registeredOrderId: String?,
        registeredPlanType: String?
    ) {
        try {
            // Primeiro verificar compras únicas (INAPP)
            val inAppResult = billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            )

            var foundMatchingPurchase = false
            var isActivePremiumResult = false
            var activePlanTypeResult: String? = null

            // Verificar compras INAPP
            if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val inAppPurchases = inAppResult.purchasesList

                // Procura por correspondência exata com orderId registrado
                if (!registeredOrderId.isNullOrBlank()) {
                    val purchase = inAppPurchases.find { it.orderId == registeredOrderId }
                    if (purchase != null && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        isActivePremiumResult = true
                        activePlanTypeResult = determinePlanType(purchase.products.firstOrNull())
                        foundMatchingPurchase = true
                    }
                }

                // Ou qualquer compra vitalícia válida
                if (!foundMatchingPurchase && firebaseIsPremium) {
                    val vitalPurchase = inAppPurchases.find { p ->
                        p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                                p.products.any { it.equals("vital", ignoreCase = true) }
                    }

                    if (vitalPurchase != null) {
                        isActivePremiumResult = true
                        activePlanTypeResult = determinePlanType(vitalPurchase.products.firstOrNull())
                        foundMatchingPurchase = true
                    }
                }
            }

            // Se não encontrou compra única, verificar assinaturas
            if (!foundMatchingPurchase) {
                val subsResult = billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                )

                if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val subPurchases = subsResult.purchasesList

                    // Correspondência exata
                    if (!registeredOrderId.isNullOrBlank()) {
                        val purchase = subPurchases.find { it.orderId == registeredOrderId }
                        if (purchase != null && isSubscriptionActive(purchase)) {
                            isActivePremiumResult = true
                            activePlanTypeResult = determinePlanType(purchase.products.firstOrNull())
                            foundMatchingPurchase = true
                        }
                    }

                    // Ou qualquer assinatura ativa
                    if (!foundMatchingPurchase && firebaseIsPremium) {
                        val activeSub = subPurchases.find { isSubscriptionActive(it) }
                        if (activeSub != null) {
                            isActivePremiumResult = true
                            activePlanTypeResult = determinePlanType(activeSub.products.firstOrNull())
                            foundMatchingPurchase = true
                        }
                    }
                }
            }

            // Atualiza UI com resultado final
            withContext(Dispatchers.Main) {
                if (isActivePremiumResult) {
                    _isPremiumUser.value = true
                    _userPlanType.value = activePlanTypeResult
                    // Salva status localmente
                    savePremiumStatusLocally(true, activePlanTypeResult)
                } else if (firebaseIsPremium) {
                    // Confiar no Firebase se Play Store não retornar nada
                    _isPremiumUser.value = true
                    _userPlanType.value = registeredPlanType
                    savePremiumStatusLocally(true, registeredPlanType)
                } else {
                    _isPremiumUser.value = false
                    _userPlanType.value = null
                    savePremiumStatusLocally(false, null)
                }

                // Atualizamos o estado de loading aqui para garantir
                _isPremiumLoading.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao verificar compras: ${e.message}", e)

            withContext(Dispatchers.Main) {
                // Em caso de erro, confiamos no Firebase
                _isPremiumUser.value = firebaseIsPremium
                _userPlanType.value = registeredPlanType
                _isPremiumLoading.value = false
            }
        }
    }

    private fun saveUserStatusToFirebase(isPremium: Boolean, planType: String?, orderId: String?, purchaseTime: Long?, productId: String?) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userEmail = currentUser.email ?: currentUser.uid
        Log.i(TAG, "Salvando Firebase para $userEmail: Premium=$isPremium, Plano=$planType, productId=$productId")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = Firebase.firestore
                val userDocRef = db.collection("premium_users").document(userEmail)

                val userData = mutableMapOf<String, Any?>(
                    "isPremium" to isPremium,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "planType" to if (isPremium) planType else null,
                    "orderId" to if (isPremium) orderId else null,
                    "purchaseTime" to if (isPremium) purchaseTime else null,
                    "productId" to if (isPremium) productId else null,
                    "userEmail" to userEmail,  // Sempre armazenar o email do usuário
                    "userId" to currentUser.uid // Sempre armazenar o ID do usuário
                )

                Log.d(TAG, "Firebase Save Data: $userData")
                userDocRef.set(userData, SetOptions.merge())
                    .addOnSuccessListener { Log.i(TAG, "Firebase Save Success para usuário $userEmail.") }
                    .addOnFailureListener { e -> Log.e(TAG, "Firebase Save Error para usuário $userEmail: ${e.message}", e) }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Save Exception para usuário $userEmail: ${e.message}", e)
            }
        }
    }

    private fun loadPremiumStatusLocally() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
                val isPremiumLocal = prefs.getBoolean("is_premium", false)
                val planTypeLocal = prefs.getString("plan_type", null)
                val lastUpdated = prefs.getLong("last_updated_local", 0L)

                Log.d(TAG, "Carregado localmente: Premium=$isPremiumLocal, Plano=$planTypeLocal, Última atualização=${lastUpdated}")

                // Carregar imediatamente o estado dos dados locais para resposta rápida
                if (isPremiumLocal) {
                    _isPremiumUser.value = isPremiumLocal
                    _userPlanType.value = planTypeLocal

                    // Se os dados são muito antigos, marcar para revalidação mas manter estado atual
                    if (System.currentTimeMillis() - lastUpdated > 24 * 60 * 60 * 1000L) { // 24 horas
                        Log.d(TAG, "Dados locais antigos, revalidando...")
                        lastVerifiedTimestamp = 0 // Força verificação
                    } else {
                        // Dados recentes, considerar verificado
                        lastVerifiedTimestamp = lastUpdated
                    }
                }

                // Não modificamos o estado isPremiumLoading aqui para não interferir
                // com a verificação completa que acontecerá em seguida
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar dados locais", e)
                // A verificação completa ainda será executada
            }
        }
    }

    private fun savePremiumStatusLocally(isPremium: Boolean, planType: String? = null) {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("is_premium", isPremium)
                if (planType != null) putString("plan_type", planType) else remove("plan_type")
                putLong("last_updated_local", System.currentTimeMillis())
                apply()
            }
            Log.i(TAG, "Salvo localmente: Premium=$isPremium, Plano=$planType")
        } catch (e: Exception) {
            Log.e(TAG, "Erro save local", e)
        }
    }

    fun forceRefreshPremiumStatus() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Forçando atualização de status premium...")
                _isPremiumLoading.value = true

                // Invalidar cache
                lastVerifiedTimestamp = 0

                // Garantir que qualquer verificação anterior seja cancelada
                activeCheckJob?.cancel()

                // Iniciar nova verificação com timeout garantido
                withTimeoutOrNull(3000) {
                    checkUserSubscription()
                }

                // Apesar do timeout, garantir tempo suficiente para mostrar indicador de progresso
                delay(800)

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao atualizar status premium: ${e.message}", e)
            } finally {
                // Sempre resetar o estado de loading após 3 segundos no máximo
                _isPremiumLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        if (billingClient.isReady) {
            Log.d(TAG, "Fechando conexão com BillingClient")
            billingClient.endConnection()
        }
    }
}