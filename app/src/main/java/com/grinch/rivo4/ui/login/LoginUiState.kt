package com.grinch.rivo4.ui.login

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Validating : LoginUiState()
    object Registering : LoginUiState()
    object Success : LoginUiState()
    data class Failure(val message: String) : LoginUiState()
}
