package com.ivip.brainstormia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    // Modify the signInLauncher's onActivityResult handling:
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                Log.d("MainActivity", "Google Sign-In successful: ${account.email}")
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()

                // Get Application instance
                val appInstance = application as BrainstormiaApplication

                // Setup Drive service in ExportViewModel
                appInstance.exportViewModel?.setupDriveService()

                // IMPORTANT: We need to force a complete reload cycle
                // This will reset all state and reload everything from scratch
                Handler(Looper.getMainLooper()).post {
                    Log.d("MainActivity", "Forcing complete ViewModel reload after login")

                    // Recreate ViewModels to ensure clean state
                    appInstance.chatViewModel = ChatViewModel(application)
                    appInstance.chatViewModel?.forceLoadConversationsAfterLogin()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing Google Sign-In result", e)
                Toast.makeText(this, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("MainActivity", "Google Sign-In canceled or failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Configurar handler para exceções não capturadas
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("UncaughtException", "Thread: ${thread.name}", throwable)
                throwable.printStackTrace()
            }

            // Initialize ViewModels in Application
            val appInstance = application as BrainstormiaApplication
            if (appInstance.exportViewModel == null) {
                appInstance.exportViewModel = ExportViewModel(application)
            }
            if (appInstance.chatViewModel == null) {
                appInstance.chatViewModel = ChatViewModel(application)
            }

            // Configurar Google Sign-In com escopo completo DRIVE
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE))
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)

            // Verificar se já está conectado ao Google
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account != null) {
                Log.d("MainActivity", "Já conectado com Google: ${account.email}")

                // Verificar se temos as permissões necessárias
                val hasDrivePermission = GoogleSignIn.hasPermissions(
                    account, Scope(DriveScopes.DRIVE)
                )
                Log.d("MainActivity", "Permissão Drive: $hasDrivePermission")

                // Inicializar o ExportViewModel com a conta existente
                appInstance.exportViewModel?.setupDriveService()
            }

            // Verificar permissão de áudio
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w("MainActivity", "Permissão de áudio não concedida")
            }

            installSplashScreen()
            WindowCompat.setDecorFitsSystemWindows(window, false)

            setContent {
                // Initialize UI
                val navController = rememberNavController()
                var isDarkTheme by remember { mutableStateOf(true) }

                // Create ViewModels directly in Compose
                val authViewModel: AuthViewModel = viewModel()
                val currentUser by authViewModel.currentUser.collectAsState()

                // Use ViewModels from Application
                val exportVM = appInstance.exportViewModel
                val chatVM = appInstance.chatViewModel

                if (exportVM != null && chatVM != null) {
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
                                            onLogin = { signInToGoogle() },
                                            chatViewModel = chatVM,
                                            authViewModel = authViewModel,
                                            exportViewModel = exportVM,
                                            isDarkTheme = isDarkTheme
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show loading if ViewModels aren't ready
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Initialize conversation list after UI is ready
            Handler(Looper.getMainLooper()).postDelayed({
                appInstance.chatViewModel?.handleLogin()
            }, 500)

        } catch (e: Exception) {
            Log.e("MainActivity", "Erro crítico em onCreate", e)
            setContentView(android.R.layout.simple_list_item_1)
            val tv = findViewById<android.widget.TextView>(android.R.id.text1)
            tv.text = "Erro crítico ao iniciar: ${e.message}"
        }
    }

    // Método para login silencioso (em segundo plano)
    private fun silentSignIn() {
        try {
            googleSignInClient.silentSignIn()
                .addOnSuccessListener { account ->
                    Log.d("MainActivity", "Login silencioso bem-sucedido: ${account.email}")

                    // Verificar permissões
                    val hasDrivePermission = GoogleSignIn.hasPermissions(
                        account, Scope(DriveScopes.DRIVE)
                    )
                    Log.d("MainActivity", "Permissão Drive após login silencioso: $hasDrivePermission")

                    // Acessar a instância do Application
                    val appInstance = application as? BrainstormiaApplication

                    // Inicializar o ExportViewModel após login silencioso bem-sucedido
                    appInstance?.exportViewModel?.setupDriveService()

                    // Recarregar conversas
                    Handler(Looper.getMainLooper()).post {
                        Log.d("MainActivity", "Notificando ChatViewModel sobre login silencioso")
                        appInstance?.chatViewModel?.handleLogin()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MainActivity", "Falha no login silencioso: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao tentar login silencioso", e)
        }
    }

    // Método para iniciar o login interativo com Google
    private fun signInToGoogle() {
        try {
            Log.d("MainActivity", "Starting Google Sign-In process")
            googleSignInClient.signOut().addOnCompleteListener {
                try {
                    val signInIntent = googleSignInClient.signInIntent
                    signInLauncher.launch(signInIntent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error launching sign-in intent", e)
                    Toast.makeText(this, "Error starting login: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in signInToGoogle", e)
            Toast.makeText(this, "Login process error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}