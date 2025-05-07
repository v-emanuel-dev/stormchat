package com.ivip.brainstormia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.billing.BillingViewModel
import com.ivip.brainstormia.theme.BrainGold
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
    // Get BillingViewModel instance
    val context = LocalContext.current
    val billingViewModel = (context.applicationContext as BrainstormiaApplication).billingViewModel
        ?: throw IllegalStateException("BillingViewModel not initialized")

    // Colors
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF8F9FA)
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color(0xFF202124)
    val secondaryTextColor = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF5F6368)
    val goldColor = BrainGold
    val darkGoldColor = Color(0xFF8B6914)
    val subtleAccentColor = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF0F4F8)

    // States
    val currentUser by authViewModel.currentUser.collectAsState()
    val isPremiumUser by billingViewModel.isPremiumUser.collectAsState(initial = false)
    val userPlanType by billingViewModel.userPlanType.collectAsState()
    val isPremiumLoading by billingViewModel.isPremiumLoading.collectAsState(initial = false)
    var isRefreshing by remember { mutableStateOf(false) }

    val email = currentUser?.email ?: "Usuário não logado"
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Initial data loading
    LaunchedEffect(Unit) {
        isRefreshing = true
        billingViewModel.forceRefreshPremiumStatus()
        delay(800)
        isRefreshing = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background with subtle gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDarkTheme) {
                            listOf(Color(0xFF121212), Color(0xFF1A1A1A))
                        } else {
                            listOf(Color(0xFFF8F9FA), Color(0xFFECEFF1))
                        }
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Perfil", color = Color.White, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isRefreshing = true
                                    billingViewModel.forceRefreshPremiumStatus()
                                    delay(800)
                                    isRefreshing = false
                                }
                            },
                            enabled = !isPremiumLoading && !isRefreshing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar status",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFF1976D2)
                    )
                )
            },
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Modern profile header with animation
                ProfileHeader(
                    email = email,
                    isPremium = isPremiumUser,
                    planType = userPlanType,
                    isDarkTheme = isDarkTheme,
                    isLoading = isPremiumLoading || isRefreshing
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Content based on premium status
                when {
                    isPremiumLoading || isRefreshing -> {
                        LoadingContent(isDarkTheme = isDarkTheme)
                    }
                    isPremiumUser -> {
                        PremiumContent(
                            planType = userPlanType,
                            isDarkTheme = isDarkTheme,
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                    else -> {
                        BasicContent(
                            onUpgradeToPremium = onNavigateToPayment,
                            isDarkTheme = isDarkTheme,
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun ProfileHeader(
    email: String,
    isPremium: Boolean,
    planType: String?,
    isDarkTheme: Boolean,
    isLoading: Boolean
) {
    val goldColor = BrainGold
    val darkGoldColor = Color(0xFF8B6914)
    val cardColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color(0xFF202124)

    val transition = rememberInfiniteTransition(label = "ProfileHeaderAnimation")
    val scaleAnim by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScaleAnimation"
    )

    val glowAnim by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isDarkTheme) darkGoldColor.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isPremium) {
                        // Draw subtle gold accent pattern on top
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    goldColor.copy(alpha = 0.0f),
                                    goldColor.copy(alpha = 0.3f * glowAnim),
                                    goldColor.copy(alpha = 0.0f)
                                )
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 8f
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plan info card for premium users (moved to top)
                if (isPremium && !isLoading) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = goldColor.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = goldColor,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Seu Plano: ${planType?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Premium"}",
                                style = MaterialTheme.typography.titleMedium,
                                color = goldColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isPremium) {
                                    listOf(goldColor.copy(alpha = 0.3f), Color(0xFF333333))
                                } else {
                                    listOf(Color(0xFF444444), Color(0xFF222222))
                                }
                            )
                        )
                        .drawBehind {
                            if (isPremium) {
                                // Premium glow effect
                                drawCircle(
                                    color = goldColor.copy(alpha = 0.15f * glowAnim),
                                    radius = size.width * 0.6f
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Initial avatar based on email
                    val initial = email.firstOrNull()?.toString()?.uppercase() ?: "?"

                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isPremium) goldColor else Color.White,
                        modifier = Modifier.scale(if (isPremium) scaleAnim else 1f)
                    )
                }

                // Email
                Text(
                    text = email,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )

                // Status badge
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(120.dp),
                        color = goldColor,
                        trackColor = if (isDarkTheme) Color.DarkGray else Color.LightGray
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPremium) goldColor else Color.Gray.copy(alpha = 0.3f),
                        modifier = Modifier.scale(if (isPremium) scaleAnim else 1f)
                    ) {
                        Text(
                            text = if (isPremium) "Membro Premium" else "Membro Básico",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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

    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
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
}

@Composable
fun PremiumContent(
    planType: String?,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    val goldColor = BrainGold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "Benefícios Premium",
            style = MaterialTheme.typography.headlineSmall,
            color = goldColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Premium features
        FeatureCard(
            icon = Icons.Outlined.Psychology,
            title = "Modelos Avançados",
            description = "Acesso a todos os modelos de IA premium, incluindo Claude e GPT-4o.",
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            icon = Icons.Outlined.CloudUpload,
            title = "Exportação de Conversas",
            description = "Exporte e compartilhe suas conversas em múltiplos formatos.",
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            icon = Icons.Outlined.SupportAgent,
            title = "Suporte Prioritário",
            description = "Obtenha suporte técnico VIP e resolva problemas mais rapidamente.",
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Thank you message
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = goldColor.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Obrigado por apoiar o Brainstormia!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BasicContent(
    onUpgradeToPremium: () -> Unit,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    val goldColor = BrainGold

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upgrade button with animation
        PremiumButton(
            onClick = onUpgradeToPremium,
            text = "Atualizar para Premium",
            isDarkTheme = isDarkTheme
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Features section
        Text(
            text = "Por que escolher o Premium?",
            style = MaterialTheme.typography.headlineSmall,
            color = goldColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        FeatureCard(
            icon = Icons.Outlined.Psychology,
            title = "Modelos de IA Avançados",
            description = "Acesso a todos os modelos premium como Claude e GPT-4o para respostas mais precisas e criativas.",
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            isLocked = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            icon = Icons.Outlined.CloudUpload,
            title = "Exportação de Conversas",
            description = "Salve e compartilhe facilmente suas conversas e ideias em múltiplos formatos.",
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            isLocked = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        FeatureCard(
            icon = Icons.Outlined.SupportAgent,
            title = "Suporte Prioritário",
            description = "Atendimento VIP para solucionar suas dúvidas e problemas de forma rápida e eficiente.",
            isDarkTheme = isDarkTheme,
            cardColor = cardColor,
            textColor = textColor,
            secondaryTextColor = secondaryTextColor,
            isLocked = true
        )
    }
}

@Composable
fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    isDarkTheme: Boolean,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isLocked: Boolean = false
) {
    val goldColor = BrainGold

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Feature icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        color = if (isDarkTheme) {
                            Color(0xFF2C2C2C)
                        } else {
                            goldColor.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = goldColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )

                    if (isLocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Recurso bloqueado",
                            tint = goldColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor
                )
            }
        }
    }
}

@Composable
fun PremiumButton(
    onClick: () -> Unit,
    text: String,
    isDarkTheme: Boolean
) {
    val goldColor = BrainGold
    val darkGoldColor = Color(0xFF8B6914)

    // Animation
    val infiniteTransition = rememberInfiniteTransition(label = "ButtonAnimation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScaleAnimation"
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "ShimmerAnimation"
    )

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .scale(scale)
    ) {
        // Shadow/glow effect
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = darkGoldColor
                )
        )

        // Button
        Button(
            onClick = onClick,
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.8f)
                .drawBehind {
                    // Shimmer effect
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.0f),
                                Color.White.copy(alpha = 0.2f),
                                Color.White.copy(alpha = 0.0f)
                            ),
                            start = Offset(shimmerOffset - size.width, 0f),
                            end = Offset(shimmerOffset, size.height)
                        )
                    )
                },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = goldColor
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 0.dp
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Black
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = text,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}