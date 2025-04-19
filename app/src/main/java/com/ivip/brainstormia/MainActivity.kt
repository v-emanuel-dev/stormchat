package com.ivip.brainstormia

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.ivip.brainstormia.theme.BrainstormiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set window to edge-to-edge with better control
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // You can change this default value based on your preference
            var isDarkTheme by remember { mutableStateOf(true) }

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
                        var isLoggedIn by remember { mutableStateOf(false) }

                        if (isLoggedIn) {
                            ChatScreen(
                                onLogin = { /* Handle login */ },
                                onLogout = {
                                    isLoggedIn = false
                                },
                                isDarkTheme = isDarkTheme
                            )
                        } else {
                            AuthScreen(
                                onNavigateToChat = {
                                    isLoggedIn = true
                                },
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