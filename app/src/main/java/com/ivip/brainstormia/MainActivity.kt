package com.ivip.brainstormia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
}
