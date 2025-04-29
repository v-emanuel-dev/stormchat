package com.ivip.brainstormia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivip.brainstormia.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    onNavigateBack: () -> Unit,
    onPurchaseComplete: () -> Unit,
    isDarkTheme: Boolean = true
) {
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else BackgroundColor
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var selectedPlan by remember { mutableStateOf(1) } // 0: Mensal, 1: Anual, 2: Lifetime
    var isProcessing by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Planos Premium",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFF1976D2),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->

            // Exibir animação de sucesso quando o pagamento for confirmado
            AnimatedVisibility(
                visible = isSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                SuccessAnimation(
                    onComplete = onPurchaseComplete,
                    isDarkTheme = isDarkTheme
                )
            }

            // Conteúdo principal
            AnimatedVisibility(
                visible = !isSuccess,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Cabeçalho com título
                    Text(
                        text = "Atualize para Premium",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Desbloqueie todo o potencial do Brainstormia",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Planos de pagamento
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Plano mensal
                        PlanCard(
                            title = "Mensal",
                            price = "R$ 19,90",
                            period = "por mês",
                            isSelected = selectedPlan == 0,
                            onSelect = { selectedPlan = 0 },
                            isDarkTheme = isDarkTheme,
                            bestValue = false,
                            features = listOf(
                                "Acesso a todos os modelos",
                                "Respostas prioritárias",
                                "Armazenamento ilimitado"
                            )
                        )

                        // Plano anual (com desconto)
                        PlanCard(
                            title = "Anual",
                            price = "R$ 179,90",
                            period = "por ano",
                            discount = "25% OFF",
                            isSelected = selectedPlan == 1,
                            onSelect = { selectedPlan = 1 },
                            isDarkTheme = isDarkTheme,
                            bestValue = true,
                            features = listOf(
                                "Acesso a todos os modelos",
                                "Respostas prioritárias",
                                "Armazenamento ilimitado",
                                "Exportação avançada",
                                "Suporte prioritário"
                            )
                        )

                        // Plano vitalício
                        PlanCard(
                            title = "Vitalício",
                            price = "R$ 499,90",
                            period = "pagamento único",
                            isSelected = selectedPlan == 2,
                            onSelect = { selectedPlan = 2 },
                            isDarkTheme = isDarkTheme,
                            bestValue = false,
                            features = listOf(
                                "Acesso a todos os modelos",
                                "Respostas prioritárias",
                                "Armazenamento ilimitado",
                                "Exportação avançada",
                                "Suporte prioritário",
                                "Atualizações vitalícias"
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Seção de pagamento
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Detalhes do Pagamento",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Opções de pagamento
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PaymentOptionButton(
                                    icon = Icons.Default.CreditCard,
                                    label = "Cartão",
                                    isSelected = true,
                                    onClick = { },
                                    isDarkTheme = isDarkTheme
                                )

                                PaymentOptionButton(
                                    icon = Icons.Default.AccountBalance,
                                    label = "Pix",
                                    isSelected = false,
                                    onClick = { },
                                    isDarkTheme = isDarkTheme
                                )

                                PaymentOptionButton(
                                    icon = Icons.Default.Receipt,
                                    label = "Boleto",
                                    isSelected = false,
                                    onClick = { },
                                    isDarkTheme = isDarkTheme
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Inputs de cartão de crédito
                            // Inputs de cartão de crédito
                            OutlinedTextField(
                                value = "4111 1111 1111 1111",
                                onValueChange = { },
                                label = { Text("Número do Cartão") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                                    unfocusedBorderColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = "João Silva",
                                    onValueChange = { },
                                    label = { Text("Nome no Cartão") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                                        unfocusedBorderColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = "12/27",
                                    onValueChange = { },
                                    label = { Text("Validade") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                                        unfocusedBorderColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor
                                    )
                                )

                                OutlinedTextField(
                                    value = "123",
                                    onValueChange = { },
                                    label = { Text("CVV") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                                        unfocusedBorderColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f),
                                        focusedTextColor = textColor,
                                        unfocusedTextColor = textColor
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Resumo do pedido
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDarkTheme) Color(0xFF333333) else Color(0xFFF5F5F5)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Plano Premium " + when(selectedPlan) {
                                                0 -> "Mensal"
                                                1 -> "Anual"
                                                else -> "Vitalício"
                                            },
                                            color = textColor
                                        )

                                        Text(
                                            text = when(selectedPlan) {
                                                0 -> "R$ 19,90"
                                                1 -> "R$ 179,90"
                                                else -> "R$ 499,90"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }

                                    if (selectedPlan == 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Economia anual (25%)",
                                                color = Color(0xFF4CAF50)
                                            )

                                            Text(
                                                text = "- R$ 59,90",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4CAF50)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = if (isDarkTheme) Color.Gray.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Total",
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )

                                        Text(
                                            text = when(selectedPlan) {
                                                0 -> "R$ 19,90"
                                                1 -> "R$ 179,90"
                                                else -> "R$ 499,90"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botão de finalizar pagamento
                    Button(
                        onClick = {
                            // Simulação de processamento de pagamento
                            coroutineScope.launch {
                                isProcessing = true
                                delay(2000) // Simula processamento
                                isProcessing = false
                                isSuccess = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                            contentColor = if (isDarkTheme) Color.Black else Color.White
                        ),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = if (isDarkTheme) Color.Black else Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Finalizar Pagamento",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Nota sobre segurança
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Pagamento seguro com criptografia SSL",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    period: String,
    discount: String? = null,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isDarkTheme: Boolean,
    bestValue: Boolean,
    features: List<String>
) {
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val selectedColor = if (isDarkTheme) Color(0xFFFFD700).copy(alpha = 0.2f) else Color(0xFF1976D2).copy(alpha = 0.1f)
    val borderColor = if (isSelected) {
        if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .width(110.dp)
            .height(220.dp)
            .clickable { onSelect() }
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) selectedColor else cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (bestValue) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Mais Popular",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isDarkTheme) Color.Black else Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) {
                    if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
                } else {
                    textColor
                }
            )

            Text(
                text = period,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )

            if (discount != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF4CAF50)
                ) {
                    Text(
                        text = discount,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            features.take(2).forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
                        modifier = Modifier.size(12.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = feature,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            if (features.size > 2) {
                Text(
                    text = "+ ${features.size - 2} mais",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun PaymentOptionButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDarkTheme: Boolean
) {
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val backgroundColor = if (isSelected) {
        if (isDarkTheme) Color(0xFFFFD700).copy(alpha = 0.2f) else Color(0xFF1976D2).copy(alpha = 0.1f)
    } else {
        if (isDarkTheme) Color(0xFF333333) else Color(0xFFF5F5F5)
    }

    val iconColor = if (isSelected) {
        if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
    } else {
        textColor.copy(alpha = 0.7f)
    }

    Surface(
        modifier = Modifier
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .width(100.dp)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) {
                    if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
                } else {
                    textColor
                }
            )
        }
    }
}

@Composable
fun SuccessAnimation(
    onComplete: () -> Unit,
    isDarkTheme: Boolean
) {
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else BackgroundColor

    var showConfetti by remember { mutableStateOf(true) }

    // Animação de escala para o ícone de sucesso
    val scale = remember { Animatable(0f) }

    LaunchedEffect(key1 = Unit) {
        // Animar o ícone
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )

        // Esperar alguns segundos e navegar
        delay(3000)
        showConfetti = false
        delay(500)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Círculo de fundo
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDarkTheme) Color(0xFFFFD700).copy(alpha = 0.2f) else Color(0xFF1976D2).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Ícone de sucesso com animação
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Sucesso",
            tint = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2),
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )

        // Texto de agradecimento
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = 120.dp)
        ) {
            Text(
                text = "Parabéns!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFF1976D2)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Você agora é um usuário Premium",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) TextColorLight else TextColorDark
            )
        }
    }
}