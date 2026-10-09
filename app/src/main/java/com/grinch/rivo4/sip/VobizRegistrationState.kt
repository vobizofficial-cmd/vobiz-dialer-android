package com.grinch.rivo4.sip

/**
 * Observable SIP registration state for the whole app.
 * Kept separate from [org.linphone.core.RegistrationState] to avoid name clashes
 * and to expose only the states the UI cares about.
 */
sealed class VobizRegistrationState {
    object Idle : VobizRegistrationState()
    object Connecting : VobizRegistrationState()
    object Registered : VobizRegistrationState()
    data class Failed(val reason: String) : VobizRegistrationState()
}
