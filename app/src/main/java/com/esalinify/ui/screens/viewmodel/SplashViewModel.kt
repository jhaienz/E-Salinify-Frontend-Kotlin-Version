package com.esalinify.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esalinify.data.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    sealed class NavigationTarget {
        object Loading : NavigationTarget()
        object Onboarding : NavigationTarget()
        object Home : NavigationTarget()
    }

    private val _navigationTarget = MutableStateFlow<NavigationTarget>(NavigationTarget.Loading)
    val navigationTarget: StateFlow<NavigationTarget> = _navigationTarget.asStateFlow()

    init {
        checkOnboardingStatus()
    }

    private fun checkOnboardingStatus() {
        viewModelScope.launch {
            // Always show onboarding first on every app launch
            _navigationTarget.value = NavigationTarget.Onboarding
        }
    }
}
