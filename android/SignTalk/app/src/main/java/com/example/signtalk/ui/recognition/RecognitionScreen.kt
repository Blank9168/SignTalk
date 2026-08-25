package com.example.signtalk.ui.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.signtalk.camera.CameraPreview
import com.example.signtalk.domain.model.RecognitionState
import com.example.signtalk.ui.components.ConfidenceMeter
import com.example.signtalk.ui.components.SignTalkTopBar

@Composable
fun RecognitionScreen(
    viewModel: RecognitionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val recognitionState by viewModel.recognitionState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Scaffold(topBar = { SignTalkTopBar("Live Recognition", onBack) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hasCameraPermission) {
                CameraPermissionRequest { permissionLauncher.launch(Manifest.permission.CAMERA) }
                return@Scaffold
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                contentAlignment = Alignment.BottomCenter
            ) {
                CameraPreview(
                    useFrontCamera = settings.useFrontCamera,
                    onFrame = { bitmap, timestamp -> viewModel.onFrame({ bitmap }, timestamp) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RecognitionStatus(recognitionState)
            }
        }
    }
}

@Composable
private fun CameraPermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "SignTalk needs camera access to recognize signs.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Your camera feed is processed entirely on-device and is never uploaded.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onRequestPermission) { Text("Grant camera access") }
    }
}

@Composable
private fun RecognitionStatus(state: RecognitionState) {
    when (state) {
        is RecognitionState.Initializing -> {
            CircularProgressIndicator()
            Text("Starting recognition...", modifier = Modifier.padding(top = 8.dp))
        }
        is RecognitionState.ModelUnavailable -> {
            Text(
                "Recognition model unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                state.reason,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        is RecognitionState.Error -> {
            Text(
                "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(state.message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        is RecognitionState.WaitingForHands -> {
            Text("Show a hand sign to the camera", style = MaterialTheme.typography.titleMedium)
        }
        is RecognitionState.Buffering -> {
            Text("Reading gesture...", style = MaterialTheme.typography.titleMedium)
            CircularProgressIndicator(
                progress = { state.framesCollected / state.framesNeeded.toFloat() },
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        is RecognitionState.NotRecognized -> {
            Text(
                "Gesture not recognized",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                "Try holding the sign steadier, or closer to the camera.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is RecognitionState.Recognized -> {
            Text(
                state.result.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            ConfidenceMeter(state.result.confidence, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
