package com.proxichat.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proxichat.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentStep: Int = 0,
    val displayName: String = "",
    val isComplete: Boolean = false,
    val permissionsGranted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isComplete = userPreferences.isOnboardingComplete.first()
            val name = userPreferences.displayName.first()
            _uiState.value = _uiState.value.copy(
                isComplete = isComplete,
                displayName = if (name == "User") "" else name
            )
        }
    }

    fun onDisplayNameChanged(name: String) {
        if (name.length <= 20) {
            _uiState.value = _uiState.value.copy(displayName = name)
        }
    }

    fun onPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(permissionsGranted = granted)
        if (granted) {
            nextStep()
        }
    }

    fun nextStep() {
        val current = _uiState.value.currentStep
        if (current < 2) {
            _uiState.value = _uiState.value.copy(currentStep = current + 1)
        }
    }

    fun previousStep() {
        val current = _uiState.value.currentStep
        if (current > 0) {
            _uiState.value = _uiState.value.copy(currentStep = current - 1)
        }
    }

    fun completeOnboarding() {
        val name = _uiState.value.displayName.trim().ifEmpty { "User" }
        viewModelScope.launch {
            userPreferences.setDisplayName(name)
            userPreferences.setOnboardingComplete(true)
            _uiState.value = _uiState.value.copy(isComplete = true)
        }
    }
}
