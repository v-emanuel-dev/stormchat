package com.ivip.brainstormia

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.theme.BrainstormiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        // Set window to edge-to-edge with better control
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // You can change this default value based on your preference
            var isDarkTheme by remember { mutableStateOf(true) }

            // Obter o ViewModel de autenticação
            val authViewModel: AuthViewModel = viewModel()
            val currentUser by authViewModel.currentUser.collectAsState()

            BrainstormiaTheme(darkTheme = isDarkTheme) {
                // Add status bar and navigation bar padding
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding() // Adds padding equal to the status bar height
                        .navigationBarsPadding() // Adds padding equal to the navigation bar height
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Auth state to control which screen to show
                        if (currentUser != null) {
                            Log.d("MainActivity", "Mostrando ChatScreen, usuário: ${currentUser?.uid}")
                            ChatScreen(
                                onLogin = { /* Não é necessário aqui */ },
                                onLogout = {
                                    Log.d("MainActivity", "Iniciando logout")
                                    authViewModel.logout()
                                },
                                isDarkTheme = isDarkTheme
                            )
                        } else {
                            Log.d("MainActivity", "Mostrando AuthScreen, usuário não logado")
                            AuthScreen(
                                onNavigateToChat = { /* Não é necessário aqui, a navegação será automática */ },
                                onBackToChat = { /* Handle back to chat */ },
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }
                }
            }
        }
    }
}