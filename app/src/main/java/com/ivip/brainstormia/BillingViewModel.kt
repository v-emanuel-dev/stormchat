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
import kotlin.math.pow

class BillingViewModel(application: Application) : AndroidViewModel(application), PurchasesUpdatedListener {
    private val TAG = "BillingViewModel"

    private val billingClient = BillingClient.newBuilder(application)
        .enablePendingPurchases()
        .setListener(this)
        .build()

    private val _isPremiumUser = MutableStateFlow(false) // Non-nullable, default false
    val isPremiumUser = _isPremiumUser.asStateFlow()

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

    // --- IDs Atuais ---
    private val SUBSCRIPTION_IDS_ONLY = listOf("mensal", "anual")
    private val INAPP_IDS_ONLY = listOf("vital") // Usando o novo ID
    // --------------------

    init {
        Log.d(TAG, "Inicializando BillingViewModel")
        checkFirebaseUserStatus()
        startBillingConnection()
    }

    private fun startBillingConnection() {
        if (billingClient.isReady) {
            Log.i(TAG, "BillingClient já está pronto. Consultando produtos e compras.")
            queryAvailableProducts()
            checkUserSubscription() // Fonte da verdade
            return
        }
        if (_connectionAttempts.value >= MAX_CONNECTION_ATTEMPTS && !_purchaseInProgress.value) {
            Log.w(TAG, "Máximo de tentativas de conexão atingido (${MAX_CONNECTION_ATTEMPTS}).")
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
                    checkUserSubscription() // Fonte da verdade
                } else {
                    Log.e(TAG, "Erro na conexão com BillingClient: ${billingResult.responseCode} - ${billingResult.debugMessage}")
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
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "Compra cancelada pelo usuário.")
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            Log.i(TAG, "Usuário já possui este item/assinatura. Verificando status...")
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
            // Chama checkUserSubscription para garantir que o estado reflete a compra mais recente do Play
            checkUserSubscription()
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
        if (purchase.products.any { it.equals("vital", ignoreCase = true) }) { // Usa novo ID
            return purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        return purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                (purchase.isAutoRenewing || (purchase.purchaseTime + GRACE_PERIOD_MS > System.currentTimeMillis()))
    }

    private val GRACE_PERIOD_MS = 2 * 24 * 60 * 60 * 1000L // 2 dias

    fun checkUserSubscription() {
        if (!billingClient.isReady) {
            Log.w(TAG, "checkUserSubscription: BillingClient não pronto.")
            return
        }
        Log.i(TAG, "--- Iniciando checkUserSubscription (Fonte da Verdade) ---")
        var activePlanTypeResult: String? = null
        var isActivePremiumResult = false
        var foundPurchaseResult: Purchase? = null
        var sourceOfTruth = "Nenhuma Compra Ativa (Play)"

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { resultInApp, purchasesInApp ->
            Log.d(TAG, "checkUserSubscription/INAPP - Resposta: ${resultInApp.responseCode}, Compras: ${purchasesInApp?.size ?: "null"}")
            if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                val vitalPurchase = purchasesInApp?.firstOrNull { p ->
                    p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            p.products.any { it.equals("vital", ignoreCase = true) } // NOVO ID
                }
                if (vitalPurchase != null) {
                    Log.i(TAG, ">>> Play encontrou INAPP ATIVA <<< ID: ${vitalPurchase.products.joinToString()}")
                    isActivePremiumResult = true
                    activePlanTypeResult = determinePlanType(vitalPurchase.products.firstOrNull())
                    foundPurchaseResult = vitalPurchase
                    sourceOfTruth = "INAPP (vital - Play)"
                    if (!vitalPurchase.isAcknowledged) acknowledgePurchase(vitalPurchase)
                } else {
                    Log.d(TAG, "Play: Nenhuma compra INAPP ativa com ID '${INAPP_IDS_ONLY.joinToString()}'.")
                }
            } else { Log.e(TAG, "Erro Play INAPP: ${resultInApp.responseCode}") }

            if (!isActivePremiumResult) {
                billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
                ) { resultSubs, purchasesSubs ->
                    Log.d(TAG, "checkUserSubscription/SUBS - Resposta: ${resultSubs.responseCode}, Compras: ${purchasesSubs?.size ?: "null"}")
                    if (resultSubs.responseCode == BillingClient.BillingResponseCode.OK) {
                        val activeSubscription = purchasesSubs?.filter { p -> isSubscriptionActive(p) }?.maxByOrNull { it.purchaseTime }
                        if (activeSubscription != null) {
                            val productId = activeSubscription.products.firstOrNull()
                            Log.i(TAG, ">>> Play encontrou SUBS ATIVA <<< ID: $productId")
                            isActivePremiumResult = true
                            activePlanTypeResult = determinePlanType(productId)
                            foundPurchaseResult = activeSubscription
                            sourceOfTruth = "SUBS ($activePlanTypeResult - Play)"
                            if (!activeSubscription.isAcknowledged) acknowledgePurchase(activeSubscription)
                        } else { Log.d(TAG, "Play: Nenhuma assinatura SUBS ativa.") }
                    } else { Log.e(TAG, "Erro Play SUBS: ${resultSubs.responseCode}") }
                    Log.i(TAG, "checkUserSubscription - Resultado Final (Play): isPremium=$isActivePremiumResult, planType=$activePlanTypeResult")
                    updatePremiumStatusFromCheck(isActivePremiumResult, activePlanTypeResult, foundPurchaseResult, sourceOfTruth)
                }
            } else {
                Log.i(TAG, "checkUserSubscription - Resultado Final (Play): isPremium=$isActivePremiumResult, planType=$activePlanTypeResult")
                updatePremiumStatusFromCheck(isActivePremiumResult, activePlanTypeResult, foundPurchaseResult, sourceOfTruth)
            }
        }
    }

    private fun updatePremiumStatusFromCheck(isPremiumFromPlay: Boolean, planTypeFromPlay: String?, purchaseFromPlay: Purchase?, source: String) {
        Log.i(TAG, ">>> updatePremiumStatusFromCheck <<< Fonte: $source, Premium(Play): $isPremiumFromPlay, Plano(Play): $planTypeFromPlay. Atual VM: Premium=${_isPremiumUser.value}, Plano=${_userPlanType.value}")
        if (_isPremiumUser.value != isPremiumFromPlay || _userPlanType.value != planTypeFromPlay) {
            Log.w(TAG, ">>> ATUALIZANDO ESTADO PREMIUM (Play Billing é a fonte) <<< Premium: $isPremiumFromPlay, Plano: $planTypeFromPlay.")
            _isPremiumUser.value = isPremiumFromPlay
            _userPlanType.value = planTypeFromPlay
            savePremiumStatusLocally(isPremiumFromPlay, planTypeFromPlay)
            saveUserStatusToFirebase(isPremiumFromPlay, planTypeFromPlay, purchaseFromPlay?.orderId, purchaseFromPlay?.purchaseTime, purchaseFromPlay?.products?.firstOrNull())
        } else {
            Log.d(TAG, "updatePremiumStatusFromCheck - Nenhuma mudança no estado premium necessária.")
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
                    "productId" to if (isPremium) productId else null
                )
                Log.d(TAG, "Firebase Save Data: $userData")
                userDocRef.set(userData, SetOptions.merge())
                    .addOnSuccessListener { Log.i(TAG, "Firebase Save Success.") }
                    .addOnFailureListener { e -> Log.e(TAG, "Firebase Save Error: ${e.message}", e) }
            } catch (e: Exception) { Log.e(TAG, "Firebase Save Exception: ${e.message}", e) }
        }
    }

    private fun checkFirebaseUserStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return Unit.also {
            Log.d(TAG, "checkFirebaseUserStatus: No user, loading local.")
            loadPremiumStatusLocally()
        }
        val userEmail = currentUser.email ?: currentUser.uid
        Log.i(TAG, "Verificando Firebase para $userEmail (inicial)")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = Firebase.firestore
                val docRef = db.collection("premium_users").document(userEmail)
                docRef.get()
                    .addOnSuccessListener { document ->
                        val isPremiumFb = if (document != null && document.exists()) document.getBoolean("isPremium") ?: false else false
                        val planTypeFb = if (document != null && document.exists()) document.getString("planType") else null
                        val productIdFb = if (document != null && document.exists()) document.getString("productId") else null
                        Log.i(TAG, "Lido do Firebase: Premium=$isPremiumFb, Plano=$planTypeFb, ProductId=$productIdFb")

                        // Define estado inicial APENAS se atual for 'false' e Firebase for 'true' com productId VÁLIDO
                        if (!_isPremiumUser.value && isPremiumFb) {
                            val isOldVitalicioId = productIdFb?.equals("vitalicio", ignoreCase = true) == true
                            if (planTypeFb == "Vitalício" && isOldVitalicioId) {
                                Log.w(TAG, "Firebase indica Vitalício com productId ANTIGO ('$productIdFb'). Ignorando estado inicial.")
                            } else {
                                Log.i(TAG, ">>> Definindo estado INICIAL via Firebase <<< Premium: $isPremiumFb, Plano: $planTypeFb")
                                _isPremiumUser.value = isPremiumFb
                                _userPlanType.value = planTypeFb
                                savePremiumStatusLocally(isPremiumFb, planTypeFb)
                            }
                        } else {
                            Log.d(TAG,"Leitura do Firebase não alterou estado inicial (${_isPremiumUser.value}).")
                        }
                    }
                    .addOnFailureListener { e -> Log.e(TAG, "Erro Firebase read: ${e.message}", e) }
            } catch (e: Exception) { Log.e(TAG, "Exceção Firebase read: ${e.message}", e) }
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
        } catch (e: Exception) { Log.e(TAG, "Erro save local", e) }
    }

    private fun loadPremiumStatusLocally() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val prefs = getApplication<Application>().getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)
                val isPremiumLocal = prefs.getBoolean("is_premium", false)
                val planTypeLocal = prefs.getString("plan_type", null)
                Log.d(TAG, "Lido localmente: Premium=$isPremiumLocal, Plano=$planTypeLocal")

                // Define estado inicial APENAS se atual for 'false' e local for 'true'
                if (!_isPremiumUser.value && isPremiumLocal) {
                    Log.i(TAG, ">>> Definindo estado INICIAL via Local <<< Premium: $isPremiumLocal, Plano: $planTypeLocal")
                    _isPremiumUser.value = isPremiumLocal
                    _userPlanType.value = planTypeLocal
                } else {
                    Log.d(TAG, "Leitura local não alterou estado inicial (${_isPremiumUser.value}).")
                }
            } catch (e: Exception) { Log.e(TAG, "Erro load local", e) }
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
