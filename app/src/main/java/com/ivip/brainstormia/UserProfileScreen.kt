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
import com.ivip.brainstormia.theme.BrainGold // Importar a cor dourada consistente
import com.ivip.brainstormia.theme.PrimaryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPayment: () -> Unit,
    authViewModel: AuthViewModel = viewModel(),
    chatViewModel: ChatViewModel, // <<< PARÂMETRO DESCOMENTADO AQUI
    billingViewModel: BillingViewModel = viewModel(),
    isDarkTheme: Boolean = true
) {
    val backgroundColor = if (isDarkTheme) Color(0xFF121212) else Color(0xFFF5F5F5)
    val currentUser by authViewModel.currentUser.collectAsState()
    val isPremiumUser by billingViewModel.isPremiumUser.collectAsState()
    val userPlanType by billingViewModel.userPlanType.collectAsState() // Coletar o tipo de plano

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
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
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

                UserProfileHeader(
                    email = email,
                    isPremium = isPremiumUser,
                    planType = userPlanType, // Passar o tipo de plano
                    isDarkTheme = isDarkTheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // O when original para exibir conteúdo baseado no status premium pode ser mantido
                // ou ajustado conforme necessário.
                when (isPremiumUser) {
                    true -> { // Explicitamente true
                        PremiumUserContent(isDarkTheme = isDarkTheme, planType = userPlanType)
                    }
                    false -> { // Explicitamente false
                        BasicUserContentWithButtonFirst(
                            onUpgradeToPremium = onNavigateToPayment,
                            isDarkTheme = isDarkTheme
                        )
                    }
                    null -> { // Estado de carregamento ou indeterminado
                        CircularProgressIndicator(
                            color = BrainGold, // Usar a cor dourada consistente
                            modifier = Modifier.padding(16.dp)
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
    planType: String?, // Receber o tipo de plano
    isDarkTheme: Boolean
) {
    val goldColor = BrainGold // Usar a cor dourada consistente para o indicador premium
    val darkGoldColor = Color(0xFF8B6914) // Um dourado mais escuro para gradientes ou variações
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
                        tint = goldColor // Usar BrainGold para o ícone também
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

            // Indicador de Membro Premium/Básico
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = goldColor, // Fundo dourado consistente
                modifier = Modifier.scale(if (isPremium) scaleAnim else 1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp) // Ajuste no padding
                ) {
                    Text(
                        text = if (isPremium) "Membro Premium" else "Membro Básico",
                        color = Color.Black, // Texto preto para contraste em fundo dourado
                        style = MaterialTheme.typography.labelLarge, // Um pouco maior
                        fontWeight = FontWeight.Bold
                    )

                    // Exibir o tipo de plano se for premium e o plano estiver disponível
                    if (isPremium && !planType.isNullOrBlank()) {
                        Text(
                            text = planType.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, // Ex: "Mensal", "Anual", "Vitalício"
                            color = Color.Black.copy(alpha = 0.85f), // Contraste
                            style = MaterialTheme.typography.labelMedium, // Tamanho adequado
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumUserContent(isDarkTheme: Boolean, planType: String?) { // Adicionado planType para consistência, embora não usado diretamente aqui
    val goldColor = BrainGold
    // val textColor = if (isDarkTheme) Color.White else Color.Black // Definido no contexto superior

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
                color = goldColor, // Destaque dourado para o título do card
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium, // Um pouco maior para legibilidade
                color = textColor,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun PulseButton( // Botão de upgrade
    onClick: () -> Unit,
    text: String,
    isDarkTheme: Boolean // isDarkTheme pode não ser necessário se a cor for sempre BrainGold
) {
    val buttonBackgroundColor = BrainGold // Cor de fundo sempre dourada
    val buttonTextColor = Color.Black    // Texto sempre preto para contraste

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
            .height(50.dp) // Altura padrão
            .fillMaxWidth(0.8f), // Ocupar boa parte da largura
        shape = RoundedCornerShape(12.dp), // Cantos um pouco menos arredondados
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
    val goldColor = BrainGold // Usar BrainGold para consistência
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PulseButton( // Este é o botão "Atualizar para Premium"
            onClick = onUpgradeToPremium,
            text = "Atualizar para Premium",
            isDarkTheme = isDarkTheme // Passando para manter a lógica interna do botão se houver
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Liberte seu potencial!",
            style = MaterialTheme.typography.headlineMedium,
            color = goldColor, // Destaque dourado
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
