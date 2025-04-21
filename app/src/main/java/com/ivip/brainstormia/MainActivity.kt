package com.ivip.brainstormia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.ivip.brainstormia.theme.BrainstormiaTheme
import com.ivip.brainstormia.navigation.Routes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar handler para exceções não capturadas
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UncaughtException", "Thread: ${thread.name}", throwable)
            throwable.printStackTrace()
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
            val currentUser by authViewModel.currentUser.collectAsState()

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
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Log para debug
        Log.d("MainActivity", "onDestroy chamado")
    }
}