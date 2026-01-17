package com.esalinify.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.esalinify.R
import com.esalinify.ui.components.PrimaryButton
import com.esalinify.ui.screens.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onGetStartedClick: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Title
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        // Content section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            // Tagline with styled "Sign" word
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.onboarding_tagline_start))
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.secondary)) {
                        append(stringResource(R.string.onboarding_tagline_sign))
                    }
                    append(stringResource(R.string.onboarding_tagline_end))
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Hero image
            Image(
                painter = painterResource(id = R.drawable.ic_onboarding),
                contentDescription = null,
                modifier = Modifier.size(width = 350.dp, height = 260.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Description text
            Text(
                text = stringResource(R.string.onboarding_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Get Started button
        PrimaryButton(
            text = stringResource(R.string.get_started),
            onClick = {
                viewModel.completeOnboarding()
                onGetStartedClick()
            }
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
