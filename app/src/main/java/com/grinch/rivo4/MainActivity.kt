package com.grinch.rivo4

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.grinch.rivo4.controller.lock.AppLockManager
import com.grinch.rivo4.controller.util.PreferenceManager
import com.grinch.rivo4.view.screen.settings.AppLockOverlay
import com.grinch.rivo4.view.screen.transitions.getAppTransition
import com.grinch.rivo4.view.theme.Rivo4Theme
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.generated.destinations.MainScreenDestination
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val preferenceManager: PreferenceManager by inject()
    private var intentState by mutableStateOf<Intent?>(null)
    private var isAppLocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        intentState = intent
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (AppLockManager.isLocked(preferenceManager)) {
            isAppLocked = true
        }

        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidContext(this@MainActivity)
                modules(appModule)
            }
        }

        if (com.grinch.rivo4.auth.CredentialStore.hasSipCredentials()) {
            com.grinch.rivo4.sip.LinphoneService.start(this)
        }

        setContent {
            Rivo4Theme {
                val navController = rememberNavController()

                val prefs = koinInject<PreferenceManager>()
                val transitionStyle = prefs.getInt(PreferenceManager.KEY_TRANSITION_STYLE, 0)
                var showLogin by remember { mutableStateOf(!com.grinch.rivo4.auth.CredentialStore.hasSipCredentials()) }

                if (isAppLocked && prefs.isAppLockEnabled()) {
                    AppLockOverlay(
                        onUnlocked = {
                            isAppLocked = false
                        }
                    )
                } else if (showLogin) {
                    com.grinch.rivo4.ui.login.LoginScreen(
                        onLoggedIn = { showLogin = false }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        DestinationsNavHost(
                            navGraph = NavGraphs.root,
                            navController = navController,
                            defaultTransitions = getAppTransition(transitionStyle)
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (intentState?.action == null || intentState?.action == Intent.ACTION_MAIN) {
                            val startLocation = prefs.getInt(PreferenceManager.KEY_START_LOCATION, PreferenceManager.START_LOCATION_NORMAL)
                            if (startLocation == PreferenceManager.START_LOCATION_DIALPAD_RECENTS || startLocation == PreferenceManager.START_LOCATION_DIALPAD_CONTACTS) {
                                navController.navigate(DialPadScreenDestination().route)
                            }
                        }
                    }

                    LaunchedEffect(intentState) {
                        handleIntent(intentState, navController)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.onAppForegrounded(preferenceManager)
        if (AppLockManager.isLocked(preferenceManager)) {
            isAppLocked = true
        }
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onAppBackgrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentState = intent
    }

    private fun handleIntent(intent: Intent?, navController: androidx.navigation.NavController) {
        intent ?: return
        val data = intent.data
        val action = intent.action

        when (action) {
            com.grinch.rivo4.BuildConfig.APPLICATION_ID + ".ACTION_VIEW_RECENTS" -> {
                navController.navigate(MainScreenDestination(initialTab = 0).route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
            Intent.ACTION_VIEW -> {
                if (data?.toString()?.contains("contacts") == true || data?.toString()?.contains("com.android.contacts") == true || intent.hasExtra("contact_id")) {
                    val id = data?.lastPathSegment ?: intent.getStringExtra("contact_id")
                    if (id != null) {
                        navController.navigate(ContactDetailsScreenDestination(contactId = id).route)
                    }
                }
            }
            Intent.ACTION_INSERT -> {
                val name = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                val phone = intent.getStringExtra(ContactsContract.Intents.Insert.PHONE)
                navController.navigate(ContactEditScreenDestination(initialName = name, initialPhone = phone).route)
            }
            Intent.ACTION_EDIT -> {
                val id = data?.lastPathSegment
                if (id != null) {
                    navController.navigate(ContactEditScreenDestination(
                        contactId = id, initialName = intent.getStringExtra(ContactsContract.Intents.Insert.NAME)
                    ).route)
                }
            }
        }
    }

    private val volumeSqueezeHelper by lazy { com.grinch.rivo4.controller.util.VolumeSqueezeHelper(this, preferenceManager) }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event != null && volumeSqueezeHelper.handleKeyEvent(event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
