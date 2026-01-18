package com.esalinify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esalinify.R
import com.esalinify.ui.components.BackButton
import com.esalinify.ui.components.CameraPreview
import com.esalinify.ui.screens.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateBack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Initialize models when screen loads
    DisposableEffect(Unit) {
        viewModel.initializeModels()
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Back button
        BackButton(onClick = onNavigateBack)

        Spacer(modifier = Modifier.height(24.dp))

        // Check camera permission
        when {
            cameraPermissionState.status.isGranted -> {
                // Permission granted - show camera
                CameraContent(
                    uiState = uiState,
                    onFrameAnalyzed = { bitmap, timestamp ->
                        viewModel.processFrame(bitmap, timestamp)
                    },
                    onClearText = { viewModel.clearText() },
                    onDeleteLast = { viewModel.deleteLastLetter() }
                )
            }

            cameraPermissionState.status.shouldShowRationale -> {
                // Show rationale
                PermissionRationale(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }

            else -> {
                // Request permission
                PermissionRequest(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
private fun CameraContent(
    uiState: com.esalinify.data.CameraUiState,
    onFrameAnalyzed: (android.graphics.Bitmap, Long) -> Unit,
    onClearText: () -> Unit,
    onDeleteLast: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera preview section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f))
        ) {
            CameraPreview(
                onFrameAnalyzed = onFrameAnalyzed
            )

            // Prediction overlay
            uiState.currentPrediction?.let { prediction ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "${prediction.predictedChar} (${(prediction.confidence * 100).toInt()}%)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Translated text section
        Text(
            text = stringResource(R.string.translated_output),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // Build display text: translated text + current word (with cursor)
            val displayText = buildString {
                append(uiState.translatedText)
                if (uiState.currentWord.isNotEmpty()) {
                    if (uiState.translatedText.isNotEmpty()) {
                        append(" ")
                    }
                    append(uiState.currentWord)
                    append("_") // Blinking cursor effect
                }
                if (isEmpty()) {
                    append("...")
                }
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDeleteLast,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(R.string.delete_last),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Button(
                onClick = onClearText,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text(
                    text = stringResource(R.string.clear_text),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }

        // Error message if any
        uiState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.width(200.dp)
        ) {
            Text(
                text = "Grant Permission",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}

@Composable
private fun PermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_permission_denied),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.camera_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            shape = RoundedCornerShape(25.dp),
            modifier = Modifier.width(200.dp)
        ) {
            Text(
                text = "Try Again",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
    }
}
