package com.ivip.brainstormia.auth

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.ivip.brainstormia.BuildConfig
import com.ivip.brainstormia.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleSignInManager(private val context: Context) {

    private val tag = "GoogleSignInManager"
    private val auth = FirebaseAuth.getInstance()
    private val crashlytics = FirebaseCrashlytics.getInstance()

    init {
        // Registrar informações do dispositivo e ambiente
        crashlytics.setCustomKey("device_manufacturer", Build.MANUFACTURER)
        crashlytics.setCustomKey("device_model", Build.MODEL)
        crashlytics.setCustomKey("android_version", Build.VERSION.SDK_INT)
        crashlytics.setCustomKey("app_version", getAppVersionName())
        crashlytics.setCustomKey("has_network", isNetworkAvailable())
        crashlytics.setCustomKey("google_play_services_version", getGooglePlayServicesVersion())
        crashlytics.log("GoogleSignInManager inicializado")
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val webClientId = context.getString(R.string.default_web_client_id)
        Log.d(tag, "Initializing with webClientId: $webClientId")
        crashlytics.log("Iniciando GSO com webClientId: $webClientId")

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .build()

            crashlytics.log("GSO configurado com sucesso")
            GoogleSignIn.getClient(context, gso)
        } catch (e: Exception) {
            crashlytics.log("Erro na inicialização do GoogleSignInClient")
            crashlytics.recordException(e)
            throw e  // Re-lançar para tratamento superior
        }
    }

    fun getSignInIntent(): Intent {
        Log.d(tag, "Getting sign in intent")
        crashlytics.log("Obtendo intent de login Google")

        return try {
            val intent = googleSignInClient.signInIntent
            crashlytics.log("Intent de login Google obtida com sucesso")
            intent
        } catch (e: Exception) {
            crashlytics.log("Falha ao obter intent de login Google")
            crashlytics.recordException(e)
            throw e  // Re-lançar para tratamento superior
        }
    }

    suspend fun handleSignInResult(data: Intent?): SignInResult {
        return try {
            if (data == null) {
                val error = "Intent de resultado nula"
                crashlytics.log(error)
                // Forçar um registro mais visível no Crashlytics
                crashlytics.recordException(RuntimeException("GoogleSignIn Falhou: Intent nula"))

                // Toast para desenvolvedores
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro: Intent nula", Toast.LENGTH_LONG).show()
                }

                return SignInResult.Error(error)
            }

            Log.d(tag, "Processing sign in result")
            crashlytics.log("Processando resultado do login Google")

            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)

                Log.d(tag, "Google Account retrieved: ${account.email}")
                crashlytics.log("Conta Google recuperada com sucesso")
                crashlytics.setCustomKey("google_auth_email_domain", account.email?.substringAfter('@') ?: "unknown")
                crashlytics.setCustomKey("google_auth_display_name", account.displayName != null)
                crashlytics.setCustomKey("google_auth_photo_url", account.photoUrl != null)

                // Firebase authentication
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                val statusCode = e.statusCode
                val statusMessage = e.status?.statusMessage ?: "Sem mensagem de status"

                Log.e(tag, "Google sign in failed", e)
                Log.e(tag, "Status code: $statusCode, Status message: $statusMessage")

                // Record detailed error data to Crashlytics
                crashlytics.log("Falha no login Google - ApiException")
                crashlytics.setCustomKey("api_error_status_code", statusCode)
                crashlytics.setCustomKey("api_error_status_message", statusMessage)
                crashlytics.setCustomKey("api_error_has_resolution", e.status?.hasResolution() ?: false)
                crashlytics.setCustomKey("google_play_services_available", isGooglePlayServicesAvailable())
                crashlytics.setCustomKey("network_available", isNetworkAvailable())

                // Registrar como exceção "fatal" para aparecer como crash no dashboard
                crashlytics.recordException(RuntimeException("GoogleSignIn falhou: Código=$statusCode", e))

                // Exibir Toast com código de status
                withContext(Dispatchers.Main) {
                    val errorMsg = getErrorMessageByStatusCode(statusCode)
                    Toast.makeText(
                        context,
                        "Login Google falhou: $statusCode - $errorMsg",
                        Toast.LENGTH_LONG
                    ).show()

                    // Para erros de configuração, mostrar um toast adicional
                    if (statusCode == 10) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            Toast.makeText(
                                context,
                                "Erro de configuração: Verifique SHA-1 e SHA-256 no Firebase",
                                Toast.LENGTH_LONG
                            ).show()
                        }, 3000) // Mostrar um segundo toast após 3 segundos
                    }
                }

                val errorMsg = getErrorMessageByStatusCode(statusCode)
                SignInResult.Error(errorMsg)
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error during Google sign in", e)
                crashlytics.log("Erro inesperado no login Google")
                crashlytics.setCustomKey("exception_type", e.javaClass.simpleName)
                crashlytics.setCustomKey("network_available", isNetworkAvailable())

                // Registrar como exceção "fatal"
                crashlytics.recordException(RuntimeException("Erro inesperado no GoogleSignIn", e))

                // Mostrar Toast com detalhes
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Erro inesperado: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                SignInResult.Error("Erro inesperado: ${e.localizedMessage}")
            }
        } catch (e: Exception) {
            // Captura falhas no próprio método handleSignInResult
            Log.e(tag, "Fatal error in handleSignInResult", e)
            crashlytics.recordException(RuntimeException("Falha crítica no processamento do login Google", e))

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Falha crítica: ${e.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }

            SignInResult.Error("Falha crítica: ${e.localizedMessage}")
        }
    }

    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): SignInResult {
        return try {
            Log.d(tag, "Authenticating with Firebase")
            crashlytics.log("Autenticando com Firebase usando credencial Google")
            val idToken = account.idToken

            if (idToken == null) {
                Log.e(tag, "ID Token is null!")
                crashlytics.log("Token ID Google é nulo")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Erro: Token de autenticação ausente",
                        Toast.LENGTH_LONG
                    ).show()
                }

                return SignInResult.Error("Token de autenticação ausente")
            }

            Log.d(tag, "ID Token retrieved successfully, length: ${idToken.length}")
            crashlytics.log("Token ID Google obtido com sucesso")
            crashlytics.setCustomKey("token_length", idToken.length)

            val credential = GoogleAuthProvider.getCredential(idToken, null)

            crashlytics.log("Iniciando signInWithCredential")
            val authResult = auth.signInWithCredential(credential).await()

            val user = authResult.user
            if (user != null) {
                Log.d(tag, "Firebase authentication successful: ${user.uid}")
                crashlytics.log("Autenticação Firebase bem-sucedida")
                crashlytics.setUserId(user.uid)
                crashlytics.setCustomKey("is_new_user", authResult.additionalUserInfo?.isNewUser ?: false)
                SignInResult.Success(user)
            } else {
                Log.e(tag, "Firebase authentication failed: User is null")
                crashlytics.log("Autenticação Firebase falhou: usuário é nulo")

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Falha na autenticação: Usuário nulo",
                        Toast.LENGTH_LONG
                    ).show()
                }

                SignInResult.Error("Falha na autenticação")
            }
        } catch (e: Exception) {
            Log.e(tag, "Firebase authentication failed", e)
            crashlytics.log("Falha na autenticação Firebase")
            crashlytics.setCustomKey("auth_exception_type", e.javaClass.simpleName)

            // Registrar código de erro específico se for FirebaseAuthException
            if (e is com.google.firebase.auth.FirebaseAuthException) {
                crashlytics.setCustomKey("firebase_auth_error_code", e.errorCode)
            }

            crashlytics.recordException(e)

            // Exibir Toast com mensagem de erro
            withContext(Dispatchers.Main) {
                val errorMessage = when {
                    e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                        "Credencial Google inválida. Tente novamente."
                    e is com.google.firebase.FirebaseNetworkException ->
                        "Erro de rede ao conectar com Firebase. Verifique sua conexão."
                    e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                        "Esta conta Google já está vinculada a outro usuário."
                    else -> "Falha na autenticação: ${e.localizedMessage}"
                }

                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            }

            val errorMessage = when {
                e is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ->
                    "Credencial Google inválida. Tente novamente."
                e is com.google.firebase.FirebaseNetworkException ->
                    "Erro de rede ao conectar com Firebase. Verifique sua conexão."
                e is com.google.firebase.auth.FirebaseAuthUserCollisionException ->
                    "Esta conta Google já está vinculada a outro usuário."
                else -> "Falha na autenticação: ${e.localizedMessage}"
            }

            SignInResult.Error(errorMessage)
        }
    }

    fun signOut() {
        crashlytics.log("Realizando logout do Google e Firebase")
        try {
            googleSignInClient.signOut()
            auth.signOut()
            crashlytics.log("Logout realizado com sucesso")
            crashlytics.setUserId("")  // Limpar ID do usuário no Crashlytics
        } catch (e: Exception) {
            crashlytics.log("Erro durante o logout")
            crashlytics.recordException(e)
            Log.e(tag, "Error during sign out", e)
        }
    }

    // Método para traduzir códigos de erro em mensagens amigáveis
    private fun getErrorMessageByStatusCode(statusCode: Int): String {
        return when(statusCode) {
            7 -> "Rede indisponível. Verifique sua conexão."
            10 -> "O app não está configurado corretamente."
            12500 -> "Erro na configuração do Play Services. Tente atualizar o app."
            12501 -> "Erro ao conectar com o Google. Tente novamente."
            12502 -> "Login cancelado pelo usuário."
            16 -> "Erro no servidor do Google."
            8 -> "Erro interno do Google Play Services."
            15 -> "Timeout de conexão com Google."
            5 -> "Operação cancelada pelo cliente."
            13 -> "Erro na conexão com Google Play Services."
            14 -> "Serviço do Google desativado."
            4 -> "Operação interrompida."
            else -> "Erro desconhecido no login com Google: $statusCode"
        }
    }

    // Função para testar a captura de erros (pode ser usada em desenvolvimento)
    fun testCrashlyticsErrorReporting() {
        crashlytics.log("Teste de relatório de erro forçado")
        crashlytics.setCustomKey("test_forced_error", true)
        crashlytics.recordException(RuntimeException("Teste de captura de erro de autenticação Google"))

        // Exibir toast para confirmar
        Toast.makeText(
            context,
            "Teste de erro enviado para Crashlytics",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Métodos auxiliares para Crashlytics
    private fun getAppVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            crashlytics.recordException(e)
            "unknown"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo ?: return false
                @Suppress("DEPRECATION")
                return networkInfo.isConnected
            }
        } catch (e: Exception) {
            crashlytics.recordException(e)
            return false
        }
    }

    private fun isGooglePlayServicesAvailable(): Boolean {
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            return resultCode == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (e: Exception) {
            crashlytics.recordException(e)
            return false
        }
    }

    private fun getGooglePlayServicesVersion(): Int {
        try {
            return com.google.android.gms.common.GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE
        } catch (e: Exception) {
            crashlytics.recordException(e)
            return -1
        }
    }

    sealed class SignInResult {
        data class Success(val user: com.google.firebase.auth.FirebaseUser) : SignInResult()
        data class Error(val message: String) : SignInResult()
    }
}