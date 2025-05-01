package com.ivip.brainstormia

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.ivip.brainstormia.billing.BillingViewModel
import com.ivip.brainstormia.theme.BackgroundColor
import com.ivip.brainstormia.theme.TextColorLight
import com.ivip.brainstormia.theme.TextColorDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onNavigateBack: () -> Unit,
    onPurchaseComplete: () -> Unit,
    isDarkTheme: Boolean = true
) {
    val billingViewModel: BillingViewModel = viewModel()
    val products by billingViewModel.products.collectAsState()
    val isPremiumUser by billingViewModel.isPremiumUser.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else BackgroundColor
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val accentColor = Color(0xFFFFD700)

    var selectedProductIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isPremiumUser) {
        if (isPremiumUser == true) onPurchaseComplete()
    }

    Surface(color = backgroundColor) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Planos Premium", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = backgroundColor
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        selectedProductIndex?.let { index ->
                            if (index < products.size) {
                                isLoading = true
                                billingViewModel.launchBillingFlow(
                                    activity = context as Activity,
                                    productDetails = products[index]
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedProductIndex != null && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isLoading) "Processando..." else "Assinar Agora",
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    "Escolha seu plano:",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                products.forEachIndexed { index, productDetails ->
                    val isSelected = selectedProductIndex == index
                    val (title, period) = when {
                        productDetails.productId.contains("mensal", true) -> "Plano Mensal" to "/mês"
                        productDetails.productId.contains("anual", true) -> "Plano Anual" to "/ano"
                        productDetails.productId.contains("vitalicio", true) -> "Plano Vitalício" to "pagamento único"
                        else -> productDetails.title to ""
                    }

                    val price = when (productDetails.productType) {
                        BillingClient.ProductType.SUBS -> productDetails.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                        BillingClient.ProductType.INAPP -> productDetails.oneTimePurchaseOfferDetails?.formattedPrice
                        else -> null
                    } ?: "Preço indisponível"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedProductIndex = index }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accentColor else Color.Gray.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) accentColor.copy(alpha = 0.1f) else cardColor
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(title, color = textColor, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(price, color = textColor, style = MaterialTheme.typography.bodyLarge)
                            if (period.isNotEmpty()) {
                                Text(period, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
