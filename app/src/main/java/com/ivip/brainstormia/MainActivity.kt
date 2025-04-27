package com.ivip.brainstormia

/* ───────────── IMPORTS ───────────── */
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

    // Launcher para solicitação de permissão de notificação
    // No início da classe MainActivity (logo após a declaração de analytics)
    private val requestPermissionLauncher = registerForActivityResult(
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

        /* Firebase */
        analytics = Firebase.analytics
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        /* Tema */
        prefs = ThemePreferences(this)

        /* ViewModels singletons vindos do Application */
        val app = application as BrainstormiaApplication
        if (app.exportViewModel == null) app.exportViewModel = ExportViewModel(application)
        if (app.chatViewModel   == null) app.chatViewModel   = ChatViewModel(application)

        /* Google Sign-In + escopo Drive */
        signInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .requestIdToken(getString(R.string.default_web_client_id))
                .build()
        )

        // Solicitar permissão de notificação
        requestNotificationPermission()

        // Verificar se foi aberto a partir de uma notificação
        handleNotificationIntent(intent)

        /* Splash + insets */
        installSplashScreen()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.e("FCM_TOKEN_MANUAL", "=====================================")
                Log.e("FCM_TOKEN_MANUAL", "TOKEN FCM OBTIDO MANUALMENTE:")
                Log.e("FCM_TOKEN_MANUAL", token ?: "Nulo")
                Log.e("FCM_TOKEN_MANUAL", "=====================================")

                // Salvar o token
                val prefs = getSharedPreferences("brainstormia_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("fcm_token", token).apply()
            } else {
                Log.e("FCM_TOKEN_MANUAL", "Falha ao obter token: ${task.exception}")
            }
        }

        /* Compose UI */
        setContent {

            val navController = rememberNavController()
            val dark by prefs.isDark.collectAsState(initial = true)

            val authVM : AuthViewModel = viewModel()
            val currentUser by authVM.currentUser.collectAsState()

            val exportVM = app.exportViewModel!!
            val chatVM   = app.chatViewModel!!

            /* Mostra loader apenas durante exportação */
            var exporting by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                exportVM.exportState.collectLatest { exporting = it is ExportState.Loading }
                chatVM.checkIfUserIsPremium()

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
                                ChatScreen(
                                    onLogin         = { launchLogin() },
                                    onLogout        = { authVM.logout() },
                                    chatViewModel   = chatVM,
                                    authViewModel   = authVM,
                                    exportViewModel = exportVM,
                                    isDarkTheme     = dark,
                                    onThemeChanged  = { setDark(it) }
                                )
                            }
                        }
                    }

                    /* Loader somente enquanto exporta */
                    if (exporting) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        /* carrega conversas assim que a UI estiver pronta */
        (application as BrainstormiaApplication).chatViewModel?.handleLogin()
    }

    /* utilidades ---------------------------------------------------------- */

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

        app.exportViewModel?.setupDriveService()                     // inicia Drive
        app.chatViewModel = ChatViewModel(application).also {
            it.forceLoadConversationsAfterLogin()                    // recarrega conversas
        }

        Toast.makeText(
            this,
            "Bem-vindo(a) ${email ?: "de volta"}!",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Solicita permissão para enviar notificações no Android 13+
     */
    private fun requestNotificationPermission() {
        try {
            // Apenas para Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Verificar se já tem permissão
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // Solicitar permissão diretamente sem diálogo
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao solicitar permissão de notificação", e)
        }
    }

    /**
     * Trata o intent quando o app é aberto de uma notificação
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Processa o intent para verificar se contém dados de notificação
     */
    private fun handleNotificationIntent(intent: Intent?) {
        try {
            if (intent == null) return

            val conversationId = intent.getLongExtra("conversation_id", -1L)
            if (conversationId != -1L) {
                Log.d("MainActivity", "Abrindo conversa da notificação: $conversationId")

                val app = application as BrainstormiaApplication
                app.chatViewModel?.selectConversation(conversationId)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erro ao processar intent da notificação", e)
        }
    }
}