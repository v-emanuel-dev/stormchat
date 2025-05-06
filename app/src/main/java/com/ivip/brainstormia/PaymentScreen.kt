package com.ivip.brainstormia

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.ivip.brainstormia.billing.BillingViewModel
import com.ivip.brainstormia.theme.BackgroundColor
import com.ivip.brainstormia.theme.TextColorLight
import com.ivip.brainstormia.theme.TextColorDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onNavigateBack: () -> Unit,
    onPurchaseComplete: () -> Unit,
    isDarkTheme: Boolean = true
) {
    // Obter a instância singleton do BillingViewModel
    val context = LocalContext.current
    val billingViewModel = (context.applicationContext as BrainstormiaApplication).billingViewModel
        ?: throw IllegalStateException("BillingViewModel não inicializado na Application")
    val productsResult by billingViewModel.products.collectAsState() // Renomeado para clareza
    val isPremiumUser by billingViewModel.isPremiumUser.collectAsState()
    val scrollState = rememberScrollState()

    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else BackgroundColor
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val accentColor = Color(0xFFFFD700) // Dourado

    var selectedProductIndex by remember { mutableStateOf<Int?>(null) }
    var isLaunchingPurchase by remember { mutableStateOf(false) } // Para o botão de compra
    var isLoadingProducts by remember { mutableStateOf(true) } // Estado para carregamento inicial

    // Efeito para tentar reconectar/recarregar produtos ao entrar na tela,
    // e para gerenciar o estado de isLoadingProducts.
    LaunchedEffect(Unit) {
        isLoadingProducts = true
        // billingViewModel.retryConnection() // Descomente se quiser forçar uma nova tentativa de conexão
        // Aguarda um pouco para ver se os produtos carregam.
        // O BillingViewModel já tenta carregar no init.
        // Se productsResult ainda estiver vazio após um delay, consideramos que não carregou.
        delay(3000) // Ajuste este tempo conforme necessário
        if (productsResult.isEmpty()) {
            Log.w("PaymentScreen", "Ainda não há produtos após o delay inicial.")
        }
        isLoadingProducts = false // Termina o estado de carregamento inicial
    }


    LaunchedEffect(isPremiumUser) {
        if (isPremiumUser == true) {
            isLaunchingPurchase = false // Reseta o estado do botão de compra
            onPurchaseComplete()
        }
    }

    // Observa mudanças em productsResult para resetar isLoading caso a compra falhe e products volte a ser vazio
    LaunchedEffect(productsResult) {
        if (productsResult.isNotEmpty()) {
            isLoadingProducts = false
        }
        // Se a compra estava em andamento e productsResult mudou (ex: falha), resetar
        if (isLaunchingPurchase && productsResult.isNotEmpty()) {
            // isLaunchingPurchase = false; // O BillingViewModel já trata o _purchaseInProgress
        }
    }

    Surface(color = backgroundColor, modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Planos Premium", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                    },
                    actions = {
                        // Botão para tentar recarregar os produtos
                        IconButton(onClick = {
                            isLoadingProducts = true
                            billingViewModel.retryConnection() // Tenta reconectar e recarregar produtos
                            // Adicionar um delay para reavaliar isLoadingProducts
                            kotlinx.coroutines.GlobalScope.launch { // Use um escopo apropriado
                                delay(3000)
                                if (productsResult.isEmpty()) {
                                    Log.w("PaymentScreen", "Produtos ainda vazios após retry.")
                                }
                                isLoadingProducts = false
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recarregar Planos", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else com.ivip.brainstormia.theme.PrimaryColor
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isLoadingProducts || productsResult.isEmpty()) Arrangement.Center else Arrangement.spacedBy(16.dp)
            ) {
                if (isLoadingProducts) {
                    CircularProgressIndicator(color = accentColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Carregando planos...",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else if (productsResult.isEmpty()) {
                    Text(
                        "Nenhum plano disponível no momento.",
                        color = textColor,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Por favor, verifique sua conexão com a internet ou tente recarregar.",
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            isLoadingProducts = true
                            billingViewModel.retryConnection()
                            kotlinx.coroutines.GlobalScope.launch {
                                delay(3000)
                                isLoadingProducts = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tentar Novamente", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                } else {
                    // Botão de Assinar Agora
                    Button(
                        onClick = {
                            selectedProductIndex?.let { index ->
                                if (index < productsResult.size) {
                                    isLaunchingPurchase = true
                                    billingViewModel.launchBillingFlow(
                                        activity = context as Activity,
                                        productDetails = productsResult[index]
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedProductIndex != null && !isLaunchingPurchase,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isLaunchingPurchase) "Processando..." else "Assinar Agora",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        "Escolha seu plano:",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    // Lista de produtos
                    productsResult.forEachIndexed { index, productDetails ->
                        ProductCard(
                            productDetails = productDetails,
                            isSelected = selectedProductIndex == index,
                            onSelected = { selectedProductIndex = index },
                            isDarkTheme = isDarkTheme,
                            accentColor = accentColor,
                            cardColor = cardColor,
                            textColor = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    productDetails: ProductDetails,
    isSelected: Boolean,
    onSelected: () -> Unit,
    isDarkTheme: Boolean,
    accentColor: Color,
    cardColor: Color,
    textColor: Color
) {
    val (title, period) = remember(productDetails.productId) { // Memoize para evitar recálculos
        when {
            productDetails.productId.contains("mensal", true) -> "Plano Mensal" to "/mês"
            productDetails.productId.contains("anual", true) -> "Plano Anual" to "/ano"
            productDetails.productId.contains("vital", true) -> "Plano Vitalício" to "pagamento único"
            else -> productDetails.title to ""
        }
    }

    val price = remember(productDetails.productId, productDetails.productType) { // Memoize
        when (productDetails.productType) {
            BillingClient.ProductType.SUBS -> productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            BillingClient.ProductType.INAPP -> productDetails.oneTimePurchaseOfferDetails?.formattedPrice
            else -> null
        } ?: "..." // Mostrar "..." se o preço não estiver disponível ainda
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() }
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) accentColor else Color.Gray.copy(alpha = if (isDarkTheme) 0.3f else 0.5f),
                shape = RoundedCornerShape(16.dp) // Bordas mais arredondadas
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.15f) else cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 20.dp) // Mais padding vertical
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = { onSelected() },
                colors = RadioButtonDefaults.colors(
                    selectedColor = accentColor,
                    unselectedColor = textColor.copy(alpha = 0.7f)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (period.isNotEmpty()) {
                    Text(period, color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(price, color = accentColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
