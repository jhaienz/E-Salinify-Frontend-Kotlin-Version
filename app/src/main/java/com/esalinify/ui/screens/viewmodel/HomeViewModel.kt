package com.esalinify.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _selectedOptionId = MutableStateFlow<Int?>(null)
    val selectedOptionId: StateFlow<Int?> = _selectedOptionId.asStateFlow()

    fun selectOption(id: Int) {
        _selectedOptionId.value = if (_selectedOptionId.value == id) null else id
    }
}
