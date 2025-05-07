package com.ivip.brainstormia // Certifique-se que o package está correto

import android.app.Application
import android.net.Uri
import android.util.Log // Import necessário para Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.storage.FirebaseStorage // Import para Firebase Storage
import com.google.firebase.storage.StorageException // Para apanhar exceções específicas do Storage
import com.google.firebase.storage.UploadTask // Import para UploadTask.TaskSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// Definição do AuthState (se ainda não estiver em um arquivo separado)
sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    class Success(val user: FirebaseUser) : AuthState()
    class Error(val message: String) : AuthState()
    object PasswordResetSent : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _logoutEvent = MutableStateFlow<Boolean>(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _isUpdatingProfilePicture = MutableStateFlow<Boolean>(false)
    val isUpdatingProfilePicture: StateFlow<Boolean> = _isUpdatingProfilePicture.asStateFlow()

    // TAG para logs
    private val TAG = "AuthViewModel"


    init {
        _currentUser.value = auth.currentUser
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            if (firebaseAuth.currentUser == null) {
                if (_authState.value is AuthState.Success) {
                    _authState.value = AuthState.Initial
                }
            } else {
                if (_authState.value is AuthState.Initial || _authState.value is AuthState.Error) {
                    _authState.value = AuthState.Success(firebaseAuth.currentUser!!)
                }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    _currentUser.value = user
                    Log.d(TAG, "signInWithGoogle: Sucesso. User: ${user?.email}")
                    user?.let {
                        _authState.value = AuthState.Success(it)
                    }
                } else {
                    Log.e(TAG, "signInWithGoogle: Falha.", task.exception)
                    _authState.value = AuthState.Error(task.exception?.message ?: "Erro desconhecido no login com Google")
                }
            }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            Log.d(TAG, "loginWithEmail: A tentar login para $email")
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                _currentUser.value = result.user
                Log.d(TAG, "loginWithEmail: Sucesso. User: ${result.user?.email}")
                result.user?.let {
                    _authState.value = AuthState.Success(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "loginWithEmail: Falha para $email", e)
                _authState.value = AuthState.Error(e.message ?: "Falha no login com e-mail")
            }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            Log.d(TAG, "registerWithEmail: A tentar registar $email")
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                _currentUser.value = result.user
                Log.d(TAG, "registerWithEmail: Sucesso. User: ${result.user?.email}")
                result.user?.let {
                    _authState.value = AuthState.Success(it)
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerWithEmail: Falha para $email", e)
                _authState.value = AuthState.Error(e.message ?: "Falha no registo com e-mail")
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            Log.d(TAG, "resetPassword: A tentar enviar email para $email")
            try {
                auth.sendPasswordResetEmail(email).await()
                Log.d(TAG, "resetPassword: Email enviado com sucesso para $email")
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                Log.e(TAG, "resetPassword: Falha para $email", e)
                _authState.value = AuthState.Error(e.message ?: "Falha ao redefinir a senha")
            }
        }
    }

    fun logout() {
        Log.d(TAG, "logout: A terminar sessão do utilizador: ${_currentUser.value?.email}")
        auth.signOut()
        _logoutEvent.value = true // O AuthStateListener deve cuidar de _currentUser e _authState
        viewModelScope.launch {
            delay(100) // Pequeno delay para garantir que o evento é coletado antes de resetar
            _logoutEvent.value = false
        }
        Log.d(TAG, "logout: Sessão terminada.")
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun updateProfilePicture(localImageUri: Uri) {
        val user = _currentUser.value
        Log.d(TAG, "updateProfilePicture: Iniciado com URI: $localImageUri")

        if (user == null) {
            Log.w(TAG, "updateProfilePicture: Utilizador é nulo. Abortando.")
            _userMessage.value = "Utilizador não autenticado para atualizar a foto."
            return
        }

        Log.d(TAG, "updateProfilePicture: Utilizador autenticado: ${user.uid} / ${user.email}")
        _isUpdatingProfilePicture.value = true
        _userMessage.value = null // Limpa mensagens anteriores

        viewModelScope.launch {
            try {
                val fileName = "profile_pic_${UUID.randomUUID()}.jpg"
                val storagePath = "profile_images/${user.uid}/$fileName"
                val storageRef = storage.reference.child(storagePath)

                Log.d(TAG, "updateProfilePicture: A tentar fazer upload para Storage path: $storagePath")
                // --- Upload ---
                val uploadTaskSnapshot = storageRef.putFile(localImageUri).await()
                Log.d(TAG, "updateProfilePicture: Upload concluído. Bytes: ${uploadTaskSnapshot.bytesTransferred}")

                // --- Obter URL de Download ---
                Log.d(TAG, "updateProfilePicture: A obter URL de download...")
                val downloadUrl = storageRef.downloadUrl.await()
                Log.d(TAG, "updateProfilePicture: URL de Download obtida: $downloadUrl")

                // --- Atualizar Perfil Firebase Auth ---
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(downloadUrl)
                    .build()
                Log.d(TAG, "updateProfilePicture: A atualizar perfil do FirebaseUser...")
                user.updateProfile(profileUpdates).await()
                Log.d(TAG, "updateProfilePicture: Perfil do FirebaseUser atualizado com sucesso.")

                // --- Atualizar StateFlow e Mensagem ---
                _currentUser.value = auth.currentUser // Re-obtém para garantir que photoUrl está atualizado no StateFlow
                _userMessage.value = "Foto de perfil atualizada com sucesso!"
                Log.i(TAG, "updateProfilePicture: Foto de perfil atualizada com sucesso para ${user.email}. Nova URL: ${auth.currentUser?.photoUrl}")

            } catch (e: StorageException) {
                // Tratar erros específicos do Firebase Storage
                Log.e(TAG, "updateProfilePicture: Erro de Storage - Código: ${e.errorCode}, Mensagem: ${e.message}", e)
                var errorMessage = "Erro de Storage: (${e.errorCode}) ${e.httpResultCode} - ${e.message}"
                // Adicionar mais detalhes baseados no errorCode se útil
                when (e.errorCode) {
                    StorageException.ERROR_BUCKET_NOT_FOUND -> errorMessage = "Erro: Bucket não encontrado. Verifique a configuração do Firebase."
                    StorageException.ERROR_PROJECT_NOT_FOUND -> errorMessage = "Erro: Projeto Firebase não encontrado."
                    StorageException.ERROR_QUOTA_EXCEEDED -> errorMessage = "Erro: Quota de armazenamento excedida."
                    StorageException.ERROR_NOT_AUTHENTICATED -> errorMessage = "Erro: Não autenticado para esta operação."
                    StorageException.ERROR_NOT_AUTHORIZED -> errorMessage = "Erro: Não autorizado. Verifique as Regras de Segurança do Firebase Storage."
                    StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> errorMessage = "Erro: Limite de tentativas excedido. Verifique a sua ligação."
                }
                _userMessage.value = errorMessage
            } catch (e: Exception) {
                Log.e(TAG, "updateProfilePicture: Falha genérica", e)
                _userMessage.value = "Erro ao atualizar foto: ${e.message}"
            } finally {
                Log.d(TAG, "updateProfilePicture: Finalizado.")
                _isUpdatingProfilePicture.value = false
            }
        }
    }

    class Factory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
