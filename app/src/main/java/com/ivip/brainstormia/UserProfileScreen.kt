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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.billing.BillingViewModel
import com.ivip.brainstormia.theme.PrimaryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    chatViewModel: ChatViewModel,
    billingViewModel: BillingViewModel = viewModel(),
    isDarkTheme: Boolean = true
) {
    // Definições de cores do tema dourado - ajustadas para melhor visibilidade no tema claro
    val goldColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B) // Dourado mais escuro para tema claro
    val darkGoldColor = if (isDarkTheme) Color(0xFFFFBB33) else Color(0xFF8B6914) // Dourado ainda mais escuro para tema claro

    // Cores de fundo baseadas no tema
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF5F5F5) // Cinza bem claro para tema claro

    val currentUser by authViewModel.currentUser.collectAsState()

    // Correção para os erros de inferência de tipo
    val isPremiumUser by chatViewModel.isPremiumUser.collectAsState()

    // Não utilizamos o userPlanType aqui, apenas o status premium

    val email = currentUser?.email ?: "Usuário não logado"

    val textColor = if (isDarkTheme) Color.White else Color.Black
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
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E1E1E) else PrimaryColor // Usando o azul PrimaryColor do tema
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

                UserProfileHeader(
                    email = email,
                    isPremium = isPremiumUser,
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Simplificado para usar apenas o status premium
                if (isPremiumUser) {
                    PremiumUserContent(isDarkTheme = isDarkTheme)
                } else {
                    BasicUserContentWithButtonFirst(
                        onUpgradeToPremium = onNavigateToPayment,
                        isDarkTheme = isDarkTheme
                    )
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
    // No modo escuro, manter o dourado original mais brilhante
    val goldColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B)
    val darkGoldColor = if (isDarkTheme) Color(0xFFFFBB33) else Color(0xFF8B6914)
    val cardBackground = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFEEEEEE)
    // textColor já está sendo usado diretamente em componentes

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

    // Animação para o selo premium
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
                    color = if (isDarkTheme) Color.White else Color.Black,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = goldColor, // Cor sólida sem efeito fade para ambos os temas
                modifier = Modifier.scale(if (isPremium) scaleAnim else 1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPremium) "Membro Premium" else "Membro Básico",
                        color = Color.Black, // Texto preto sobre fundo dourado para melhor contraste
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PremiumUserContent(isDarkTheme: Boolean) {
    val goldColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B)
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
                // Recursos premium - mantidos
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

                // A seção de estatísticas foi removida
            }
        }
    }
}

@Composable
fun PremiumFeatureCard(title: String, description: String, isDarkTheme: Boolean) {
    val goldColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B)
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
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
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
    val goldColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B)

    // Definir cor do texto como preto quando for botão Premium
    val textColor = if (text.contains("Premium")) Color.Black else Color.White

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
            contentColor = textColor // Cor condicional baseada no texto
        )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BasicUserContentWithButtonFirst(
    onUpgradeToPremium: () -> Unit,
    isDarkTheme: Boolean
) {
    val goldColor = if (isDarkTheme) Color(0xFFFFD700) else Color(0xFFB8860B)
    val textColor = if (isDarkTheme) Color.White else Color.Black

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