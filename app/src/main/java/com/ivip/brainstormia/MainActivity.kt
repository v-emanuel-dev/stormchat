package com.ivip.brainstormia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import com.google.api.services.drive.DriveScopes
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.ivip.brainstormia.navigation.Routes
import com.ivip.brainstormia.theme.BrainstormiaTheme
import com.ivip.brainstormia.theme.PrimaryColor
import com.ivip.brainstormia.billing.PaymentScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// DataStore for theme preferences
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemePreferences(private val context: Context) {
    companion object { val DARK_THEME_ENABLED = booleanPreferencesKey("dark_mode_enabled") }

    val isDarkThemeEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME_ENABLED] ?: true // Default to dark theme
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { settings ->
            settings[DARK_THEME_ENABLED] = enabled
        }
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var themePreferences: ThemePreferences
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        firebaseAnalytics.logEvent("google_signin_result", Bundle().apply {
            putInt("result_code", result.resultCode)
        })

        if (result.resultCode == RESULT_OK) {
            val accountTask = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = accountTask.getResult(ApiException::class.java)
                handleLoginSuccess(account?.email, account?.idToken)
            } catch (e: ApiException) {
                Log.e("MainActivity", "Google sign in failed", e)
                Toast.makeText(this, "Google Sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Login canceled or failed", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        firebaseAnalytics = Firebase.analytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        themePreferences = ThemePreferences(this)

        googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()
        )

        requestNotificationPermission()
        handleNotificationIntent(intent)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.e("FCM_TOKEN_MANUAL", "=====================================")
                Log.e("FCM_TOKEN_MANUAL", "FCM TOKEN OBTAINED MANUALLY:")
                Log.e("FCM_TOKEN_MANUAL", token ?: "Null")
                Log.e("FCM_TOKEN_MANUAL", "=====================================")
                val localPrefs = getSharedPreferences("brainstormia_prefs", Context.MODE_PRIVATE)
                localPrefs.edit().putString("fcm_token", token).apply()
            } else {
                Log.e("FCM_TOKEN_MANUAL", "Failed to get token: ${task.exception}")
            }
        }

        setContent {
            val navController = rememberNavController()
            val isDarkThemeEnabled by themePreferences.isDarkThemeEnabled.collectAsState(initial = true)

            val authViewModel: AuthViewModel = viewModel()
            val currentUser by authViewModel.currentUser.collectAsState()

            val app = applicationContext as BrainstormiaApplication
            val chatViewModelInstance = app.chatViewModel
            val exportViewModelInstance = app.exportViewModel

            var showLoadingOverlay by remember { mutableStateOf(false) }
            val exportState by exportViewModelInstance.exportState.collectAsState()

            LaunchedEffect(exportState) {
                showLoadingOverlay = exportState is ExportState.Loading
            }

            val authState by authViewModel.authState.collectAsState()
            LaunchedEffect(authState, navController.currentDestination?.route) {
                // Só ative o overlay global quando NÃO estiver na tela de autenticação
                showLoadingOverlay = authState is AuthState.Loading &&
                        (navController.currentDestination?.route != Routes.AUTH &&
                                navController.currentDestination?.route != Routes.RESET_PASSWORD)
            }

            BrainstormiaTheme(darkTheme = isDarkThemeEnabled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Surface(Modifier.fillMaxSize()) {
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
                                    onNavigateToPasswordReset = {
                                        navController.navigate(Routes.RESET_PASSWORD)
                                    },
                                    authViewModel  = authViewModel,
                                    isDarkTheme    = isDarkThemeEnabled,
                                    onThemeChanged = { enabled -> lifecycleScope.launch { themePreferences.setDarkThemeEnabled(enabled) } }
                                )
                            }
                            composable(Routes.RESET_PASSWORD) {
                                PasswordResetScreen(
                                    onBackToLogin = { navController.popBackStack() },
                                    authViewModel  = authViewModel,
                                    isDarkTheme    = isDarkThemeEnabled,
                                    onThemeChanged = { enabled -> lifecycleScope.launch { themePreferences.setDarkThemeEnabled(enabled) } }
                                )
                            }
                            composable(Routes.MAIN) {
                                ChatScreen(
                                    onLogin         = { launchLogin() },
                                    onLogout        = { authViewModel.logout() },
                                    onNavigateToProfile = { navController.navigate(Routes.USER_PROFILE) },
                                    chatViewModel   = chatViewModelInstance,
                                    authViewModel   = authViewModel,
                                    exportViewModel = exportViewModelInstance,
                                    isDarkTheme     = isDarkThemeEnabled,
                                    onThemeChanged  = { enabled -> lifecycleScope.launch { themePreferences.setDarkThemeEnabled(enabled) } }
                                )
                            }
                            composable(Routes.USER_PROFILE) {
                                UserProfileScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToPayment = {
                                        navController.navigate(Routes.PAYMENT)
                                    },
                                    authViewModel = authViewModel,
                                    isDarkTheme = isDarkThemeEnabled
                                )
                            }
                            composable(Routes.PAYMENT) {
                                PaymentScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onPurchaseComplete = {
                                        navController.popBackStack(Routes.USER_PROFILE, inclusive = false)
                                    },
                                    isDarkTheme = isDarkThemeEnabled
                                )
                            }
                        }
                    }

                    if (showLoadingOverlay) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryColor)
                        }
                    }
                }
            }
        }
    }

    private fun launchLogin() {
        googleSignInClient.signOut().addOnCompleteListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun handleLoginSuccess(email: String?, idToken: String?) {
        firebaseAnalytics.logEvent("google_login_success", Bundle().apply {
            putString("email_domain", email?.substringAfter('@') ?: "unknown")
        })

        val app = applicationContext as BrainstormiaApplication
        // AuthScreen should handle calling authViewModel.signInWithGoogle(idToken)
        // idToken?.let {
        //     authViewModel.signInWithGoogle(it) // This might be redundant if AuthScreen handles it
        // }

        app.exportViewModel.setupDriveService()
        app.chatViewModel.handleLogin()

        Toast.makeText(
            this,
            "Welcome ${email ?: "back"}!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting notification permission", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        try {
            if (intent == null) return

            val app = applicationContext as BrainstormiaApplication
            if (intent.getBooleanExtra("check_subscription", false)) {
                Log.d("MainActivity", "Received subscription check notification, verifying status...")
                app.handleSubscriptionCancellationNotification()
            }

            val conversationId = intent.getLongExtra("conversation_id", -1L)
            if (conversationId != -1L) {
                Log.d("MainActivity", "Opening conversation from notification: $conversationId")
                app.chatViewModel.selectConversation(conversationId)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing notification intent", e)
        }
    }
}