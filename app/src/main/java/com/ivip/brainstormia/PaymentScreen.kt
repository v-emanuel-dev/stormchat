package com.ivip.brainstormia

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.billing.BillingViewModel
import kotlinx.coroutines.delay

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

    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val scrollState = rememberScrollState()

    val goldColor = Color(0xFFFFD700)

    var selectedProductIndex by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isPremiumUser) {
        if (isPremiumUser) {
            onPurchaseComplete()
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Membro Básico", color = goldColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = goldColor)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = backgroundColor
                )
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    selectedProductIndex?.let { index ->
                        if (index < products.size) {
                            isLoading = true
                            billingViewModel.launchBillingFlow(
                                context as Activity,
                                products[index]
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = goldColor,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.Gray
                ),
                enabled = selectedProductIndex != null && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.Black)
                } else {
                    Text("Atualizar para Premium", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Liberte seu potencial",
                color = goldColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            products.forEachIndexed { index, product ->
                val price = product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: "Indisponível"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedProductIndex = index },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedProductIndex == index) goldColor.copy(alpha = 0.2f) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(product.title.substringBefore("("), color = goldColor, fontWeight = FontWeight.Bold)
                        Text(price, color = Color.Black)
                    }
                }
            }

            if (products.isEmpty()) {
                CircularProgressIndicator(color = goldColor)
                Text("Carregando planos...", color = goldColor)
            }
        }
    }
}
