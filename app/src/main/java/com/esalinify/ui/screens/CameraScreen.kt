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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.esalinify.data.RecognitionMode
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
    onNavigateToKeyboard: () -> Unit,
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
                    onDeleteLast = { viewModel.deleteLastLetter() },
                    onNavigateToKeyboard = onNavigateToKeyboard,
                    viewModel = viewModel
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
    onDeleteLast: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    viewModel: CameraViewModel
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
                cameraFacing = uiState.cameraFacing,
                onFrameAnalyzed = onFrameAnalyzed
            )

            // Prediction overlay - show detected letter/phrase with mode indicator
            uiState.currentPrediction?.let { prediction ->
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mode indicator chip
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (uiState.recognitionMode) {
                                RecognitionMode.LETTER -> "Letter"
                                RecognitionMode.PHRASE -> "Phrase"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Prediction text with confidence
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = prediction.predictedChar,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSecondary
                            )

                            // Show confidence percentage
                            Text(
                                text = "${(prediction.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Top-right buttons
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Switch to Keyboard button
                IconButton(
                    onClick = onNavigateToKeyboard,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Switch to Keyboard",
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Camera flip button
                IconButton(
                    onClick = { viewModel.toggleCameraFacing() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mode toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.toggleRecognitionMode() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(25.dp)
            ) {
                val modeText = when (uiState.recognitionMode) {
                    RecognitionMode.LETTER -> "Mode: Letters (A-Z)"
                    RecognitionMode.PHRASE -> "Mode: Phrases"
                }
                Text(
                    text = modeText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            Text(
                text = if (uiState.translatedText.isEmpty()) "..." else uiState.translatedText,
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
