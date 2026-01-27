package com.esalinify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esalinify.ui.components.SignImageWithLabel
import com.esalinify.ui.screens.viewmodel.KeyboardViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    viewModel: KeyboardViewModel = hiltViewModel()
) {
    val inputText by viewModel.inputText.collectAsState()
    var showTooltip by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the text field when screen loads
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Function to show keyboard
    fun showKeyboard() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Auto-scroll to bottom when text changes
    LaunchedEffect(inputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
    ) {
        // Header with back button, camera switch, and info icon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Back button
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable { onNavigateBack() },
                tint = Color.Black
            )

            // Camera switch button (centered)
            IconButton(
                onClick = onNavigateToCamera,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Switch to Camera",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            // Info icon with tooltip
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(
                    onClick = { showTooltip = !showTooltip }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        modifier = Modifier.size(24.dp),
                        tint = Color.Black
                    )
                }

                // Tooltip
                if (showTooltip) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(top = 40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF333333),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            text = "Type using the keyboard to communicate",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Sign language display area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                contentAlignment = Alignment.Center
            ) {
                if (inputText.isEmpty()) {
                    // Empty state placeholder
                    Text(
                        text = "Your message in sign language will appear here",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // Display signs in a grid layout
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 5
                    ) {
                        inputText.uppercase().forEach { char ->
                            if (char.isLetter() || char == ' ') {
                                SignImageWithLabel(
                                    char = char,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // "Your Message in Sign Language" label
            Text(
                text = "Your Message in Sign Language",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Display the typed text in a pill shape - clickable to show keyboard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable { showKeyboard() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Start typing to see your message in sign language",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        text = inputText,
                        color = Color.Black,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Hidden text field to capture keyboard input
        BasicTextField(
            value = inputText,
            onValueChange = { viewModel.updateInputText(it) },
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = Color.Transparent,
                fontSize = 1.sp
            ),
            cursorBrush = SolidColor(Color.Transparent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            )
        )
    }
}
