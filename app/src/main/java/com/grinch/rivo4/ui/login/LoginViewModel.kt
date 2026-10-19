package com.grinch.rivo4.ui.login

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grinch.rivo4.auth.CredentialStore
import com.grinch.rivo4.auth.CredentialValidator
import com.grinch.rivo4.auth.ValidationResult
import com.grinch.rivo4.sip.LinphoneService
import com.grinch.rivo4.sip.RegistrationErrorMapper
import com.grinch.rivo4.sip.VobizProvisioner
import com.grinch.rivo4.sip.VobizRegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.regex.Pattern

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _step = MutableStateFlow("")
    val step: StateFlow<String> = _step.asStateFlow()

    private var retryAction: (() -> Unit)? = null

    private val isBusy: Boolean
        get() = _uiState.value is LoginUiState.Validating || _uiState.value is LoginUiState.Registering

    init {
        Log.d("LoginViewModel", "LoginViewModel created")
    }

    /** Resets the ViewModel state to Idle - call when login screen is shown after logout. */
    fun reset() {
        Log.d("LoginViewModel", "reset() called - clearing state to Idle")
        _uiState.value = LoginUiState.Idle
        _step.value = ""
        retryAction = null
    }

    /**
     * Direct SIP credential login (agent/manual mode). Validates input, persists it
     * and then waits for the SIP engine to reach Registered/Failed.
     */
    fun onConnectClicked(
        username: String,
        password: String,
        domain: String,
        transport: String,
        authId: String?,
        authToken: String?,
        trunkUsername: String? = null,
        trunkPassword: String? = null,
        trunkDomain: String? = null
    ) {
        if (isBusy) return
        when (val r = CredentialValidator.validateSip(username.trim(), password, domain.trim(), transport)) {
            is ValidationResult.Invalid -> {
                setState(LoginUiState.Failure("${r.field}: ${r.message}"))
                return
            }
            ValidationResult.Valid -> Unit
        }
        val hasTrunkInput = !trunkUsername.isNullOrBlank() || !trunkPassword.isNullOrBlank() || !trunkDomain.isNullOrBlank()
        if (hasTrunkInput) {
            if (trunkUsername.isNullOrBlank() || trunkPassword.isNullOrBlank() || trunkDomain.isNullOrBlank()) {
                setState(LoginUiState.Failure("All three trunk fields must be filled, or leave all blank"))
                return
            }
            CredentialStore.saveTrunkConfig(trunkUsername.trim(), trunkPassword, trunkDomain.trim())
            Log.i("VobizSip", "Trunk config saved for domain=${trunkDomain.trim()}")
        } else {
            CredentialStore.clearTrunkConfig()
            Log.i("VobizSip", "No trunk config provided - will fall back to registrar")
        }
        setState(LoginUiState.Validating)
        _step.value = ""
        CredentialStore.init(app)
        CredentialStore.saveSip(username.trim(), password, domain.trim(), transport)
        CredentialStore.saveLoginMode(CredentialStore.LoginMode.SIP)
        Log.i("VobizSip", "SIP credentials saved for ${username.trim()}@${domain.trim()}")
        if (!authId.isNullOrBlank() && !authToken.isNullOrBlank()) {
            when (val r = CredentialValidator.validateApi(authId.trim(), authToken)) {
                is ValidationResult.Invalid -> {
                    setState(LoginUiState.Failure("API ${r.field}: ${r.message}"))
                    return
                }
                ValidationResult.Valid -> CredentialStore.saveApi(authId.trim(), authToken)
            }
        }
        retryAction = {
            onConnectClicked(username, password, domain, transport, authId, authToken, trunkUsername, trunkPassword, trunkDomain)
        }
        registerAndWait()
    }

    /** Vobiz account login: REST provisioning, then SIP registration. */
    fun onAccountLoginClicked(email: String, password: String) {
        if (isBusy) return
        if (email.isBlank() || password.isBlank()) {
            setState(LoginUiState.Failure("email: Enter your Vobiz account email and password"))
            return
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            setState(LoginUiState.Failure("email: Enter a valid email address"))
            return
        }
        if (password.length < 8) {
            setState(LoginUiState.Failure("password: Password must be at least 8 characters"))
            return
        }
        setState(LoginUiState.Validating)
        _step.value = "Signing in to Vobiz..."
        retryAction = { onAccountLoginClicked(email, password) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    VobizProvisioner.ensureReady(app, email.trim(), password) { s -> _step.value = s }
                }
            }
            val provision = result.getOrNull()
            when {
                result.isFailure ->
                    setState(LoginUiState.Failure(
                        result.exceptionOrNull()?.message ?: "Login failed"
                    ))
                provision == null ->
                    setState(LoginUiState.Failure("Provisioning returned no result"))
                provision.status != "OK" ->
                    setState(LoginUiState.Failure(provision.status))
                else -> {
                    // Provisioning succeeded - credentials are saved in CredentialStore by VobizProvisioner
                    // Now register with SIP
                    registerAndWait()
                }
            }
        }
    }

    private fun setState(newState: LoginUiState) {
        Log.d("LoginViewModel", "State change: ${_uiState.value.javaClass.simpleName} -> ${newState.javaClass.simpleName}")
        _uiState.value = newState
    }

    fun onSkipClicked(): Boolean = CredentialStore.hasSipCredentials()

    fun onRetry() {
        retryAction?.invoke()
    }

fun onPermissionDenied() {
        setState(LoginUiState.Failure(
            getApplication<Application>().getString(com.grinch.rivo4.R.string.vobiz_permission_mic_required)
        ))
    }

    private fun registerAndWait() {
        setState(LoginUiState.Registering)
        _step.value = "Starting SIP engine..."
        LinphoneService.start(app)
        // Resets state to Connecting synchronously so the collector below never
        // observes a stale Failed from a previous attempt.
        LinphoneService.reconfigureAndRegister()
        viewModelScope.launch {
            val finalState = withTimeoutOrNull(REGISTRATION_TIMEOUT_MS) {
                LinphoneService.registrationState.first {
                    it is VobizRegistrationState.Registered || it is VobizRegistrationState.Failed
                }
            }
            when (finalState) {
                is VobizRegistrationState.Registered -> {
                    _step.value = ""
                    setState(LoginUiState.Success)
                }
                is VobizRegistrationState.Failed -> {
                    setState(LoginUiState.Failure(RegistrationErrorMapper.map(finalState.reason)))
                }
                null -> {
                    setState(LoginUiState.Failure(RegistrationErrorMapper.map("timeout")))
                }
                else -> Unit
            }
        }
    }

    companion object {
        // SIP registration timers resolve an unreachable registrar within ~32-64s;
        // 45s keeps the UX bounded while still covering the normal failure path.
        private const val REGISTRATION_TIMEOUT_MS = 45_000L
        private val EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
