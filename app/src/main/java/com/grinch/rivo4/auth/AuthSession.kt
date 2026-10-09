package com.grinch.rivo4.auth

import android.content.Context
import com.grinch.rivo4.sip.LinphoneService
import com.grinch.rivo4.sip.VobizSession

/** Session-level actions shared by Settings and the login flow. */
object AuthSession {

    /**
     * Clears every stored credential (SIP + API + Vobiz session), then tears down the
     * SIP registration. After this the login gate re-appears on the next activity
     * (re)creation and the user can sign in with different credentials.
     */
    fun logout(context: Context) {
        CredentialStore.clearAll()
        runCatching { VobizSession.clear(context) }
        LinphoneService.reconfigureAndRegister()
    }
}
