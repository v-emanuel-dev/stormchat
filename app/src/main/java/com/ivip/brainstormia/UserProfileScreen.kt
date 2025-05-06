package com.ivip.brainstormia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.billing.BillingViewModel
import com.ivip.brainstormia.theme.BrainGold
import com.ivip.brainstormia.theme.PrimaryColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    isDarkTheme: Boolean = true
) {
    // Obter a instância singleton do BillingViewModel
    val context = LocalContext.current
    val billingViewModel = (context.applicationContext as BrainstormiaApplication).billingViewModel
        ?: throw IllegalStateException("BillingViewModel não inicializado na Application")

    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF5F5F5)
    val currentUser by authViewModel.currentUser.collectAsState()

    // Usar a instância singleton do BillingViewModel
    val isPremiumUser by billingViewModel.isPremiumUser.collectAsState(initial = false)
    val userPlanType by billingViewModel.userPlanType.collectAsState()
    val isPremiumLoading by billingViewModel.isPremiumLoading.collectAsState(initial = false)

    // Estado para controlar loading extendido e animações
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val email = currentUser?.email ?: "Usuário não logado"
    val scrollState = rememberScrollState()

    // Efeito para verificar o status premium com timeout
    LaunchedEffect(Unit) {
        // Iniciar visualização com carregamento enquanto dados são buscados
        isRefreshing = true

        // Forçar verificação do status premium
        billingViewModel.forceRefreshPremiumStatus()

        // Adicionar tempo mínimo de animação para evitar flashes
        delay(800)
        isRefreshing = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Seu Perfil", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Botão de atualização para recarregar os dados
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isRefreshing = true
                                    billingViewModel.forceRefreshPremiumStatus()
                                    delay(800) // Tempo mínimo para visualização do spinner
                                    isRefreshing = false
                                }
                            },
                            enabled = !isPremiumLoading && !isRefreshing
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Atualizar status",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else PrimaryColor
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Cabeçalho do perfil com informações do usuário
                UserProfileHeader(
                    email = email,
                    isPremium = isPremiumUser,
                    planType = userPlanType,
                    isDarkTheme = isDarkTheme,
                    isLoading = isPremiumLoading || isRefreshing
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Conteúdo principal baseado no status premium
                when {
                    isPremiumLoading || isRefreshing -> {
                        LoadingContent(isDarkTheme = isDarkTheme)
                    }
                    isPremiumUser -> {
                        PremiumUserContent(isDarkTheme = isDarkTheme, planType = userPlanType)
                    }
                    else -> {
                        BasicUserContentWithButtonFirst(
                            onUpgradeToPremium = onNavigateToPayment,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingContent(isDarkTheme: Boolean) {
    val goldColor = BrainGold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = goldColor,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Verificando seu status premium...",
            color = if (isDarkTheme) Color.White else Color.Black,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UserProfileHeader(
    email: String,
    isPremium: Boolean,
    planType: String?,
    isDarkTheme: Boolean,
    isLoading: Boolean = false
) {
    val goldColor = BrainGold
    val darkGoldColor = Color(0xFF8B6914)
    val cardBackground = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)
    val textColorPrimary = if (isDarkTheme) Color.White else Color.Black

    val gradientColors = listOf(goldColor, darkGoldColor)

    val transition = rememberInfiniteTransition(label = "Selo Animation")
    val scaleAnim by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Scale Animation Premium"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn()
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF333333), Color(0xFF222222))))
                        .border(3.dp, Brush.linearGradient(gradientColors), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = goldColor
                    )
                }
            }

            AnimatedVisibility(visible = true, enter = fadeIn()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColorPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    color = goldColor,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = goldColor,
                    modifier = Modifier.scale(if (isPremium) scaleAnim else 1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isPremium) "Membro Premium" else "Membro Básico",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (isPremium && !planType.isNullOrBlank()) {
                            Text(
                                text = planType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                color = Color.Black.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumUserContent(isDarkTheme: Boolean, planType: String?) {
    val goldColor = BrainGold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Obrigado por ser Premium!",
            style = MaterialTheme.typography.headlineMedium,
            color = goldColor,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PremiumFeatureCard(
                    title = "Modelos Avançados",
                    description = "Acesse os melhores modelos de IA.",
                    isDarkTheme = isDarkTheme
                )
                PremiumFeatureCard(
                    title = "Exportação Avançada",
                    description = "Exporte suas ideias em vários formatos.",
                    isDarkTheme = isDarkTheme
                )
                PremiumFeatureCard(
                    title = "Suporte Prioritário",
                    description = "Receba atendimento VIP.",
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@Composable
fun PremiumFeatureCard(title: String, description: String, isDarkTheme: Boolean) {
    val goldColor = BrainGold
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val cardBgColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor,
            contentColor = textColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = goldColor,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun PulseButton(
    onClick: () -> Unit,
    text: String,
    isDarkTheme: Boolean
) {
    val buttonBackgroundColor = BrainGold
    val buttonTextColor = Color.Black

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse Button")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Scale Animation"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .height(50.dp)
            .fillMaxWidth(0.8f),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonBackgroundColor,
            contentColor = buttonTextColor
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun BasicUserContentWithButtonFirst(
    onUpgradeToPremium: () -> Unit,
    isDarkTheme: Boolean
) {
    val goldColor = BrainGold
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PulseButton(
            onClick = onUpgradeToPremium,
            text = "Atualizar para Premium",
            isDarkTheme = isDarkTheme
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Liberte seu potencial!",
            style = MaterialTheme.typography.headlineMedium,
            color = goldColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Desbloqueie recursos exclusivos com o plano Premium.",
            style = MaterialTheme.typography.titleSmall,
            color = textColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PremiumFeatureCard(
                    title = "Modelos de IA Avançados",
                    description = "Acesso ilimitado aos modelos de IA mais poderosos e atualizados.",
                    isDarkTheme = isDarkTheme
                )
                PremiumFeatureCard(
                    title = "Exportação Completa de Conversas",
                    description = "Salve e compartilhe suas conversas e ideias em diversos formatos.",
                    isDarkTheme = isDarkTheme
                )
                PremiumFeatureCard(
                    title = "Suporte Prioritário",
                    description = "Receba atendimento mais rápido e dedicado para suas dúvidas.",
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}