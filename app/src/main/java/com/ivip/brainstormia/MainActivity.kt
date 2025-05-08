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
import com.google.api.services.drive.DriveScopes
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.ivip.brainstormia.navigation.Routes
import com.ivip.brainstormia.theme.BrainstormiaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/* ───────────── DataStore (tema claro/escuro) ───────────── */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class ThemePreferences(private val context: Context) {
    companion object { val DARK = booleanPreferencesKey("dark_mode") }

    val isDark: Flow<Boolean> = context.dataStore.data.map { it[DARK] ?: true }

    suspend fun setDark(enabled: Boolean) =
        context.dataStore.edit { it[DARK] = enabled }
}

/* ───────────── MainActivity ───────────── */
class MainActivity : ComponentActivity() {

    /* Google Sign-In */
    private lateinit var signInClient: GoogleSignInClient

    /* Utilitários globais */
    private lateinit var prefs: ThemePreferences
    private lateinit var analytics: FirebaseAnalytics

    /* Launcher do login Google */
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        analytics.logEvent("google_signin_result", Bundle().apply {
            putInt("result_code", result.resultCode)
        })

        if (result.resultCode == RESULT_OK) {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).result
            handleLoginSuccess(account?.email)
        } else {
            Toast.makeText(this, "Login cancelado ou falhou", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Permissão de notificação concedida")
        } else {
            Log.w("MainActivity", "Permissão de notificação negada")
        }
    }

    /* onCreate */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        analytics = Firebase.analytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        prefs = ThemePreferences(this)

        val app = application as BrainstormiaApplication
        // Ensure ViewModels are initialized if they are meant to be singletons via Application
        initViewModels(app)

        signInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()
        )

        requestNotificationPermission()
        handleNotificationIntent(intent)
        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.e("FCM_TOKEN_MANUAL", "=====================================")
                Log.e("FCM_TOKEN_MANUAL", "TOKEN FCM OBTIDO MANUALMENTE:")
                Log.e("FCM_TOKEN_MANUAL", token ?: "Nulo")
                Log.e("FCM_TOKEN_MANUAL", "=====================================")
                val localPrefs = getSharedPreferences("brainstormia_prefs", Context.MODE_PRIVATE)
                localPrefs.edit().putString("fcm_token", token).apply()
            } else {
                Log.e("FCM_TOKEN_MANUAL", "Falha ao obter token: ${task.exception}")
            }
        }

        setContent {
            val navController = rememberNavController()
            val dark by prefs.isDark.collectAsState(initial = true)

            val authVM : AuthViewModel = viewModel()
            val currentUser by authVM.currentUser.collectAsState()

            val app = application as BrainstormiaApplication
            val exportVM = app.exportViewModel
            val chatVM = app.chatViewModel
            val billingVM = app.billingViewModel

            var exporting by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                exportVM?.exportState?.collectLatest { exporting = it is ExportState.Loading }
            }

            BrainstormiaTheme(darkTheme = dark) {
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
                                    authViewModel  = authVM,
                                    isDarkTheme    = dark,
                                    onThemeChanged = { setDark(it) }
                                )
                            }
                            composable(Routes.RESET_PASSWORD) {
                                PasswordResetScreen(
                                    onBackToLogin = { navController.popBackStack() },
                                    authViewModel  = authVM,
                                    isDarkTheme    = dark,
                                    onThemeChanged = { setDark(it) }
                                )
                            }
                            composable(Routes.MAIN) {
                                chatVM?.let { chatViewModel ->
                                    exportVM?.let { exportViewModel ->
                                        ChatScreen(
                                            onLogin         = { launchLogin() },
                                            onLogout        = { authVM.logout() },
                                            onNavigateToProfile = { navController.navigate(Routes.USER_PROFILE) },
                                            chatViewModel   = chatViewModel,
                                            authViewModel   = authVM,
                                            exportViewModel = exportViewModel,
                                            isDarkTheme     = dark,
                                            onThemeChanged  = { setDark(it) }
                                        )
                                    }
                                }
                            }
                            composable(Routes.USER_PROFILE) {
                                UserProfileScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToPayment = {
                                        navController.navigate(Routes.PAYMENT)
                                    },
                                    authViewModel = authVM,
                                    isDarkTheme = dark
                                )
                            }
                            composable(Routes.PAYMENT) {
                                PaymentScreen(
                                    onNavigateBack = { navController.popBackStack() },
                                    onPurchaseComplete = {
                                        navController.navigate(Routes.MAIN) {
                                            popUpTo(Routes.MAIN) { inclusive = true }
                                        }
                                    },
                                    isDarkTheme = dark
                                )
                            }
                        }
                    }

                    if (exporting) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    // Helper function to initialize ViewModels if they're null
    private fun initViewModels(app: BrainstormiaApplication) {
        if (app.chatViewModel == null) {
            app.chatViewModel = ChatViewModel(application)
        }
        if (app.exportViewModel == null) {
            app.exportViewModel = ExportViewModel(application)
        }
        // BillingViewModel is lazy-initialized in the Application class
    }

    private fun setDark(enabled: Boolean) =
        lifecycleScope.launch { prefs.setDark(enabled) }

    private fun launchLogin() {
        signInClient.signOut().addOnCompleteListener {
            signInLauncher.launch(signInClient.signInIntent)
        }
    }

    private fun handleLoginSuccess(email: String?) {
        analytics.logEvent("google_login_success", Bundle().apply {
            putString("email_domain", email?.substringAfter('@') ?: "unknown")
        })

        val app = application as BrainstormiaApplication
        app.exportViewModel?.setupDriveService()
        app.chatViewModel?.handleLogin()

        Toast.makeText(
            this,
            "Bem-vindo(a) ${email ?: "de volta"}!",
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
            Log.e("MainActivity", "Erro ao solicitar permissão de notificação", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        try {
            if (intent == null) return

            // Check if we need to verify subscription status (from a notification)
            if (intent.getBooleanExtra("check_subscription", false)) {
                Log.d("MainActivity", "Recebida notificação sobre assinatura, verificando status...")
                (application as BrainstormiaApplication).handleSubscriptionCancellationNotification()
            }

            // Handle conversation opening from notification
            val conversationId = intent.getLongExtra("conversation_id", -1L)
            if (conversationId != -1L) {
                Log.d("MainActivity", "Abrindo conversa da notificação: $conversationId")
                (application as BrainstormiaApplication).chatViewModel?.selectConversation(conversationId)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao processar intent da notificação", e)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}