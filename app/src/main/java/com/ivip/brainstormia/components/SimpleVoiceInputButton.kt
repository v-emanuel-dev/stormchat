package com.ivip.brainstormia.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivip.brainstormia.theme.PrimaryColor
import java.util.*

@Composable
fun SimpleVoiceInputButton(
    onTextResult: (String) -> Unit,
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit = {},
    isSendEnabled: Boolean,
    isDarkTheme: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Animação para o botão quando está gravando
    val infiniteTransition = rememberInfiniteTransition(label = "voiceButton")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isListening) Color.Red else PrimaryColor,
        animationSpec = tween(300),
        label = "background"
    )

    // Launcher para o reconhecimento de voz
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onStopListening()
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.let { text ->
                onTextResult(text)
            }
        }
    }

    // Launcher para solicitar permissão
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSpeechRecognition(context, speechRecognizerLauncher, onStartListening)
        } else {
            Toast.makeText(
                context,
                "Permissão de microfone necessária para usar reconhecimento de voz",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (!isSendEnabled)
                    PrimaryColor.copy(alpha = if (isDarkTheme) 0.5f else 0.4f)
                else
                    backgroundColor
            )
            .clickable(enabled = isSendEnabled) {
                if (activity == null) {
                    Toast.makeText(context, "Erro: Activity não disponível", Toast.LENGTH_SHORT).show()
                    return@clickable
                }

                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (!isListening) {
                        startSpeechRecognition(context, speechRecognizerLauncher, onStartListening)
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            .scale(if (isListening) scale else 1f),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Gravar mensagem",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun startSpeechRecognition(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onStartListening: () -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale sua mensagem...")
    }

    try {
        onStartListening()
        launcher.launch(intent)
    } catch (e: Exception) {
        Log.e("VoiceInput", "Erro ao iniciar reconhecimento", e)
        Toast.makeText(
            context,
            "Dispositivo não suporta reconhecimento de voz",
            Toast.LENGTH_SHORT
        ).show()
    }
}