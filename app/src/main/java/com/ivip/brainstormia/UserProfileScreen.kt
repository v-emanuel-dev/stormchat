package com.ivip.brainstormia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    chatViewModel: ChatViewModel,
    isDarkTheme: Boolean = true
) {
    // Definições de cores do tema dourado
    val goldColor = Color(0xFFFFD700)
    val darkGoldColor = Color(0xFFFFBB33)

    // Cores de fundo baseadas no tema
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val cardBackgroundColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkTheme) Color.White else Color.Black

    val currentUser by authViewModel.currentUser.collectAsState()
    val isPremiumUser by chatViewModel.isPremiumUser.collectAsState()
    val email = currentUser?.email ?: "Usuário não logado"

    val scrollState = rememberScrollState()

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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else goldColor
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

                UserProfileHeader(email = email, isPremium = isPremiumUser == true, isDarkTheme = isDarkTheme)

                Spacer(modifier = Modifier.height(24.dp))

                when (isPremiumUser) {
                    null -> {
                        CircularProgressIndicator(
                            color = goldColor,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    true -> {
                        PremiumUserContent(isDarkTheme = isDarkTheme)
                    }
                    false -> {
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
fun UserProfileHeader(
    email: String,
    isPremium: Boolean,
    isDarkTheme: Boolean
) {
    val goldColor = Color(0xFFFFD700)
    val darkGoldColor = Color(0xFFFFBB33)

    val gradientColors = listOf(goldColor, darkGoldColor)

    val transition = rememberInfiniteTransition(label = "Selo Animation")
    val alphaAnim by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Alpha Animation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White
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
                    color = if (isDarkTheme) Color.White else Color.Black
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = goldColor.copy(alpha = alphaAnim)
            ) {
                Text(
                    text = if (isPremium) "Membro Premium" else "Membro Básico",
                    color = goldColor,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun PremiumUserContent(isDarkTheme: Boolean) {
    val goldColor = Color(0xFFFFD700)
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Obrigado por ser Premium!",
            style = MaterialTheme.typography.headlineMedium,
            color = goldColor
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Recursos premium - mantidos
                PremiumFeatureCard(title = "Modelos Avançados", description = "Acesse os melhores modelos de IA.", isDarkTheme = isDarkTheme)
                PremiumFeatureCard(title = "Exportação Avançada", description = "Exporte suas ideias em vários formatos.", isDarkTheme = isDarkTheme)
                PremiumFeatureCard(title = "Suporte Prioritário", description = "Receba atendimento VIP.", isDarkTheme = isDarkTheme)

                // A seção de estatísticas foi removida
            }
        }
    }
}

@Composable
fun PremiumFeatureCard(title: String, description: String, isDarkTheme: Boolean) {
    val goldColor = Color(0xFFFFD700)
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color.White,
            contentColor = textColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = goldColor)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun PulseButton(
    onClick: () -> Unit,
    text: String,
    isDarkTheme: Boolean
) {
    val goldColor = Color(0xFFFFD700)

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
        modifier = Modifier.scale(scale),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = goldColor,
            contentColor = Color.Black
        )
    ) {
        Text(text)
    }
}

@Composable
fun BasicUserContentWithButtonFirst(
    onUpgradeToPremium: () -> Unit,
    isDarkTheme: Boolean
) {
    val goldColor = Color(0xFFFFD700)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botão colocado antes do texto
        PulseButton(
            onClick = onUpgradeToPremium,
            text = "Atualizar para Premium",
            isDarkTheme = isDarkTheme
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Liberte seu potencial!",
            style = MaterialTheme.typography.headlineMedium,
            color = goldColor
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
                    description = "Tenha acesso aos modelos mais poderosos de IA.",
                    isDarkTheme = isDarkTheme
                )
                PremiumFeatureCard(
                    title = "Exportação de Ideias",
                    description = "Salve suas ideias em vários formatos de arquivo.",
                    isDarkTheme = isDarkTheme
                )
                PremiumFeatureCard(
                    title = "Suporte Prioritário",
                    description = "Seja atendido mais rápido com suporte dedicado.",
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}