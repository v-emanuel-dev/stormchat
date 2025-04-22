package com.ivip.brainstormia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.ivip.brainstormia.theme.BrainstormiaTheme
import com.ivip.brainstormia.navigation.Routes

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient

    // Registrar o resultado da tela de login Google
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result

                // Verificar se as permissões necessárias foram concedidas
                val hasDrivePermission = GoogleSignIn.hasPermissions(
                    account, Scope(DriveScopes.DRIVE)
                )
                Log.d("MainActivity", "Login Google bem-sucedido: ${account.email}, Permissão Drive: $hasDrivePermission")
                Toast.makeText(this, "Login Google bem-sucedido", Toast.LENGTH_SHORT).show()

                // Inicializar o ExportViewModel com a conta recém-conectada
                val exportViewModel = (application as? BrainstormiaApplication)?.exportViewModel
                exportViewModel?.setupDriveService()

                // Recarregar conversas após login bem-sucedido
                val chatViewModel = (application as? BrainstormiaApplication)?.chatViewModel
                if (chatViewModel != null) {
                    // Usar método handleLogin para garantir que as conversas sejam carregadas
                    chatViewModel.handleLogin()
                }

            } catch (e: Exception) {
                Log.e("MainActivity", "Erro no login Google", e)
                Toast.makeText(this, "Erro no login Google: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("MainActivity", "Login Google cancelado ou falhou")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar handler para exceções não capturadas
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UncaughtException", "Thread: ${thread.name}", throwable)
            throwable.printStackTrace()
        }

        // Configurar Google Sign-In com escopo completo DRIVE
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE)) // Usando escopo completo DRIVE
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Verificar se já está conectado ao Google
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            Log.d("MainActivity", "Nenhuma conta Google conectada, iniciando login silencioso")
            silentSignIn()
        } else {
            Log.d("MainActivity", "Já conectado com Google: ${account.email}")

            // Verificar se temos as permissões necessárias
            val hasDrivePermission = GoogleSignIn.hasPermissions(
                account, Scope(DriveScopes.DRIVE)
            )
            Log.d("MainActivity", "Permissão Drive: $hasDrivePermission")

            // Inicializar o ExportViewModel com a conta existente
            val exportViewModel = (application as? BrainstormiaApplication)?.exportViewModel
            exportViewModel?.setupDriveService()

            // Recarregar as conversas
            val chatViewModel = (application as? BrainstormiaApplication)?.chatViewModel
            chatViewModel?.handleLogin()
        }

        // Verificar se o reconhecimento de voz está disponível
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("MainActivity", "Reconhecimento de voz não disponível neste dispositivo")
            Toast.makeText(this, "Reconhecimento de voz não disponível", Toast.LENGTH_LONG).show()
        } else {
            Log.d("MainActivity", "Reconhecimento de voz disponível")
        }

        // Verificar permissão de áudio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w("MainActivity", "Permissão de áudio não concedida")
        } else {
            Log.d("MainActivity", "Permissão de áudio concedida")
        }

        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()
            var isDarkTheme by remember { mutableStateOf(true) }
            val authViewModel: AuthViewModel = viewModel()
            val exportViewModel: ExportViewModel = viewModel() // Adicionar o ExportViewModel
            val chatViewModel: ChatViewModel = viewModel()      // Adicionar o ChatViewModel
            val currentUser by authViewModel.currentUser.collectAsState()

            // Registrar os ViewModels no Application para acesso global
            (application as? BrainstormiaApplication)?.exportViewModel = exportViewModel
            (application as? BrainstormiaApplication)?.chatViewModel = chatViewModel

            BrainstormiaTheme(darkTheme = isDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = if (currentUser != null) Routes.MAIN else Routes.AUTH
                        ) {
                            composable(Routes.AUTH) {
                                AuthScreen(
                                    onNavigateToChat = {
                                        navController.navigate(Routes.MAIN) {
                                            popUpTo(Routes.AUTH) { inclusive = true }
                                        }
                                    },
                                    onBackToChat = { navController.popBackStack() },
                                    onNavigateToPasswordReset = { navController.navigate(Routes.RESET_PASSWORD) },
                                    authViewModel = authViewModel,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                            composable(Routes.RESET_PASSWORD) {
                                PasswordResetScreen(
                                    onBackToLogin = { navController.popBackStack() },
                                    authViewModel = authViewModel,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                            composable(Routes.MAIN) {
                                ChatScreen(
                                    onLogout = { authViewModel.logout() },
                                    onLogin = { signInToGoogle() }, // Novo callback para login Google
                                    chatViewModel = chatViewModel,
                                    authViewModel = authViewModel,
                                    exportViewModel = exportViewModel, // Passar o ExportViewModel
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Método para login silencioso (em segundo plano)
    private fun silentSignIn() {
        googleSignInClient.silentSignIn()
            .addOnSuccessListener { account ->
                Log.d("MainActivity", "Login silencioso bem-sucedido: ${account.email}")

                // Verificar permissões
                val hasDrivePermission = GoogleSignIn.hasPermissions(
                    account, Scope(DriveScopes.DRIVE)
                )
                Log.d("MainActivity", "Permissão Drive após login silencioso: $hasDrivePermission")

                // Inicializar o ExportViewModel após login silencioso bem-sucedido
                val exportViewModel = (application as? BrainstormiaApplication)?.exportViewModel
                exportViewModel?.setupDriveService()

                // Recarregar conversas
                val chatViewModel = (application as? BrainstormiaApplication)?.chatViewModel
                chatViewModel?.handleLogin()
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Falha no login silencioso: ${e.message}")
                // Não iniciamos o login interativo automaticamente para não interromper o fluxo do usuário
            }
    }

    // Método para iniciar o login interativo com Google
    private fun signInToGoogle() {
        // Desconectar primeiro para forçar uma nova solicitação de permissão
        googleSignInClient.signOut().addOnCompleteListener {
            // Agora solicitar login com novas permissões
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Log para debug
        Log.d("MainActivity", "onDestroy chamado")
    }
}
