package com.ivip.brainstormia

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    class Success(val user: FirebaseUser) : AuthState()
    class Error(val message: String) : AuthState()
    object PasswordResetSent : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _logoutEvent = MutableStateFlow<Boolean>(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent

    init {
        _currentUser.value = auth.currentUser
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                _currentUser.value = result.user
                result.user?.let {
                    _authState.value = AuthState.Success(it)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "signInWithGoogle failed", e)
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    fun loginWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                _currentUser.value = result.user
                result.user?.let {
                    _authState.value = AuthState.Success(it)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "loginWithEmail failed", e)
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                _currentUser.value = result.user
                result.user?.let {
                    _authState.value = AuthState.Success(it)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "registerWithEmail failed", e)
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.PasswordResetSent
            } catch (e: Exception) {
                Log.e("AuthViewModel", "resetPassword failed", e)
                _authState.value = AuthState.Error(e.message ?: "Password reset failed")
            }
        }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
        _logoutEvent.value = true
        _authState.value = AuthState.Initial
    }
}