package com.esalinify.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esalinify.R
import com.esalinify.data.CommunicationData
import com.esalinify.ui.components.CommunicationCard
import com.esalinify.ui.components.PrimaryButton
import com.esalinify.ui.screens.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val selectedOptionId by viewModel.selectedOptionId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Welcome text
        Text(
            text = stringResource(R.string.home_welcome),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Communication options
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            CommunicationData.options.forEach { option ->
                CommunicationCard(
                    option = option,
                    isSelected = selectedOptionId == option.id,
                    onSelect = { viewModel.selectOption(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Now button container - fixed height to prevent layout shift
        val buttonAlpha by animateFloatAsState(
            targetValue = if (selectedOptionId != null) 1f else 0f,
            label = "buttonAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .alpha(buttonAlpha),
            contentAlignment = Alignment.Center
        ) {
            PrimaryButton(
                text = stringResource(R.string.start_now),
                onClick = {
                    when (selectedOptionId) {
                        1 -> onNavigateToCamera()
                        2 -> onNavigateToKeyboard()
                    }
                },
                enabled = selectedOptionId != null
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
