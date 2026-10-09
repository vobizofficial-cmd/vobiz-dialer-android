package com.grinch.rivo4.view.screen.settings

import com.grinch.rivo4.controller.util.RivoText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.grinch.rivo4.R
import android.util.Log
import com.grinch.rivo4.auth.CallerIdMode
import com.grinch.rivo4.auth.CredentialStore
import com.grinch.rivo4.controller.util.PreferenceManager
import com.grinch.rivo4.controller.util.getAppVersion
import com.grinch.rivo4.sip.LinphoneService
import com.grinch.rivo4.sip.VobizProvisioner
import com.grinch.rivo4.sip.VobizRegistrationState
import com.grinch.rivo4.view.components.RivoDivider
import com.grinch.rivo4.view.components.RivoExpressiveCard
import com.grinch.rivo4.view.components.RivoListItem
import com.grinch.rivo4.view.components.RivoConfirmationDialog
import com.grinch.rivo4.view.theme.RivoMaterialShapes
import com.grinch.rivo4.view.theme.rememberRivoMorphShape
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingsScreen(
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalResources.current
    val appInfo = getAppVersion(context)
    val logoMorph = rememberRivoMorphShape(RivoMaterialShapes.Cookie12Sided, RivoMaterialShapes.Circle) { 0.2f }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // App Info Banner
            item {
                RivoExpressiveCard(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable { navigator.navigate(AboutScreenDestination) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = logoMorph,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(11.dp)) {
                                Image(
                                    painter = painterResource(R.drawable.logo),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.about_app_display_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "v${appInfo.first}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_top_card_subtext),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 0. Vobiz Account
            item {
                val regState by com.grinch.rivo4.sip.LinphoneService.registrationState.collectAsState()
                var showLogoutDialog by remember { mutableStateOf(false) }
                val sipUser = CredentialStore.getSip()?.username

                val statusSupporting = when {
                    regState is com.grinch.rivo4.sip.VobizRegistrationState.Registered ->
                        RivoText.get(R.string.vobiz_settings_account_support, sipUser ?: "-")
                    regState is com.grinch.rivo4.sip.VobizRegistrationState.Connecting ->
                        RivoText.get(R.string.vobiz_settings_registering)
                    sipUser == null -> RivoText.get(R.string.vobiz_not_signed_in)
                    else -> RivoText.get(R.string.vobiz_settings_account_not_registered)
                }
                RivoExpressiveCard(
                    title = "Vobiz Account",
                    icon = Icons.Outlined.AccountCircle
                ) {
                    RivoListItem(
                        headline = RivoText.get(R.string.vobiz_settings_account),
                        supporting = statusSupporting,
                        leadingIcon = Icons.Outlined.ManageAccounts,
                        onClick = {
                            if (regState !is VobizRegistrationState.Registered) {
                                com.grinch.rivo4.sip.LinphoneService.reconfigureAndRegister()
                            }
                        }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = RivoText.get(R.string.vobiz_logout),
                        supporting = RivoText.get(R.string.vobiz_logout_confirm_body),
                        leadingIcon = Icons.Outlined.Logout,
                        onClick = { showLogoutDialog = true }
                    )
                }

                if (showLogoutDialog) {
                    RivoConfirmationDialog(
                        onDismissRequest = { showLogoutDialog = false },
                        onConfirm = {
                            com.grinch.rivo4.auth.AuthSession.logout(context)
                            (context as? android.app.Activity)?.recreate()
                        },
                        title = RivoText.get(R.string.vobiz_logout_confirm_title),
                        message = RivoText.get(R.string.vobiz_logout_confirm_body),
                        confirmLabel = RivoText.get(R.string.vobiz_logout),
                        isDestructive = true,
                        icon = Icons.Outlined.Logout
                    )
                }
            }

            // 0b. Outbound Caller ID
            item {
                val loginMode = remember { CredentialStore.getLoginMode() }
                var savedMode by remember { mutableStateOf(CredentialStore.getCallerIdMode()) }
                var pendingMode by remember { mutableStateOf(savedMode) }
                var customInput by remember {
                    mutableStateOf((savedMode as? CallerIdMode.Custom)?.number ?: "")
                }
                val dids = remember { CredentialStore.getDids().sorted() }
                val isValidE164 = customInput.trim().matches(Regex("^\\+[1-9]\\d{7,14}$"))
                val customPending = pendingMode is CallerIdMode.Custom
                val dirty = pendingMode != savedMode
                val saveEnabled = dirty && (!customPending || isValidE164)

                RivoExpressiveCard(
                    title = "Outbound Caller ID",
                    icon = Icons.Outlined.Call
                ) {
                    if (loginMode == CredentialStore.LoginMode.SIP) {
                        val fixedNumber = CredentialStore.getEffectiveCallerId()
                            ?: CredentialStore.getSip()?.username
                            ?: "-"
                        RivoListItem(
                            headline = fixedNumber,
                            supporting = "Fixed to the number assigned to this SIP account",
                            leadingIcon = Icons.Outlined.Lock,
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        Text(
                            text = "Choose which number appears as the caller on outgoing calls. Changes apply only after Save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        RivoListItem(
                            headline = "Use default trunk number",
                            supporting = "Vobiz will use the trunk's configured caller ID",
                            leadingIcon = Icons.Outlined.SettingsPhone,
                            selected = pendingMode is CallerIdMode.Default,
                            onClick = { pendingMode = CallerIdMode.Default },
                            trailingContent = {
                                RadioButton(
                                    selected = pendingMode is CallerIdMode.Default,
                                    onClick = { pendingMode = CallerIdMode.Default }
                                )
                            }
                        )
                        dids.forEach { did ->
                            RivoListItem(
                                headline = did,
                                supporting = "Vobiz account number",
                                leadingIcon = Icons.Outlined.Phone,
                                selected = pendingMode is CallerIdMode.Did &&
                                    (pendingMode as CallerIdMode.Did).number == did,
                                onClick = { pendingMode = CallerIdMode.Did(did) },
                                trailingContent = {
                                    RadioButton(
                                        selected = pendingMode is CallerIdMode.Did &&
                                            (pendingMode as CallerIdMode.Did).number == did,
                                        onClick = { pendingMode = CallerIdMode.Did(did) }
                                    )
                                }
                            )
                        }
                        RivoListItem(
                            headline = "Custom number",
                            supporting = "E.164, must be verified on Vobiz",
                            leadingIcon = Icons.Outlined.Dialpad,
                            selected = customPending,
                            onClick = {
                                pendingMode = CallerIdMode.Custom(customInput.trim())
                            },
                            trailingContent = {
                                RadioButton(
                                    selected = customPending,
                                    onClick = { pendingMode = CallerIdMode.Custom(customInput.trim()) }
                                )
                            }
                        )
                        if (customPending) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = {
                                    customInput = it
                                    pendingMode = CallerIdMode.Custom(it.trim())
                                },
                                label = { Text("Custom Caller ID (E.164)") },
                                placeholder = { Text("+919999999999") },
                                supportingText = { Text("Format: + followed by digits. Must be verified on Vobiz.") },
                                isError = customInput.isNotEmpty() && !isValidE164,
                                singleLine = true,
                                enabled = customPending,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                CredentialStore.saveCallerIdMode(pendingMode)
                                savedMode = pendingMode
                                // Update Vobiz Voice Application Answer URL with new callerId if it's a DID
                                val apiCreds = CredentialStore.getApi()
                                val sipCreds = CredentialStore.getSip()
                                val applicationId = CredentialStore.getApplicationId() ?: "85076776948601220"
                                if (apiCreds != null && sipCreds != null) {
                                    val currentMode = pendingMode
                                    val newCallerId = when (currentMode) {
                                        is CallerIdMode.Did -> currentMode.number
                                        is CallerIdMode.Custom -> currentMode.number
                                        else -> null
                                    }
                                    if (newCallerId != null) {
                                        scope.launch {
                                            VobizProvisioner.updateAnswerUrlCallerId(
                                                authId = apiCreds.authId,
                                                token = apiCreds.authToken,
                                                applicationId = applicationId,
                                                newCallerId = newCallerId,
                                                sipUri = "sip:${sipCreds.username}@${sipCreds.domain}",
                                                onStep = { msg -> Log.i("VobizProvisioner", msg) }
                                            )
                                        }
                                    }
                                }
                                // The outbound auth info is keyed by the From user, so
                                // re-apply SIP config to register the new mapping.
                                LinphoneService.reconfigureAndRegister()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Caller ID saved - re-registering")
                                }
                            },
                            enabled = saveEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            // 1. Personalization & Display
            item {
                RivoExpressiveCard(
                    title = RivoText.get(com.grinch.rivo4.R.string.ui_personalization_display_336),
                    icon = Icons.Outlined.Palette
                ) {
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_theme_appearance_282),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_material_you_color_palette_amoled_dark_mode_animations_337),
                        leadingIcon = Icons.Outlined.Palette,
                        onClick = { navigator.navigate(InterfaceScreenDestination) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_navigation_bar_284),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_floating_bar_style_blur_effect_roundness_tab_layout_285),
                        leadingIcon = Icons.Outlined.Dock,
                        onClick = { navigator.navigate(BottomNavScreenDestination) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_avatars_contact_cards_286),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_11_avatar_shapes_contact_photos_initials_cards_287),
                        leadingIcon = Icons.Outlined.AccountCircle,
                        onClick = { navigator.navigate(AvatarSettingsScreenDestination) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = stringResource(R.string.settings_sound_vibration_headline),
                        supporting = stringResource(R.string.settings_sound_vibration_supporting),
                        leadingIcon = Icons.Outlined.VolumeUp,
                        onClick = { navigator.navigate(SoundVibrationScreenDestination) }
                    )
                }
            }

            // 2. Calling & Behavior
            item {
                RivoExpressiveCard(
                    title = RivoText.get(com.grinch.rivo4.R.string.ui_calling_behavior_338),
                    icon = Icons.Outlined.Phone
                ) {
                    RivoListItem(
                        headline = stringResource(R.string.settings_swipe_actions_title),
                        supporting = stringResource(R.string.settings_swipe_actions_supporting),
                        leadingIcon = Icons.Outlined.Swipe,
                        onClick = { navigator.navigate(SwipeActionsScreenDestination) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = stringResource(R.string.call_recordings_title),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_auto_recording_shizuku_internal_audio_saved_recordings_339),
                        leadingIcon = Icons.Outlined.FiberManualRecord,
                        onClick = { navigator.navigate(CallRecordingsScreenDestination()) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_call_analytics_insights_340),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_talk_time_leaderboard_peak_hours_distribution_341),
                        leadingIcon = Icons.Outlined.Analytics,
                        onClick = { navigator.navigate(CallAnalyticsScreenDestination()) }
                    )
                }
            }

            // 3. Call Protection & Security
            item {
                RivoExpressiveCard(
                    title = RivoText.get(com.grinch.rivo4.R.string.ui_call_protection_security_342),
                    icon = Icons.Outlined.Security
                ) {
                    val appLockEnabled = remember(settingsState) { prefs.isAppLockEnabled() }
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_app_lock_244),
                        supporting = if (appLockEnabled) RivoText.get(com.grinch.rivo4.R.string.ui_enabled_face_fingerprint_pin_343) else RivoText.get(com.grinch.rivo4.R.string.ui_protect_app_with_biometrics_or_pin_344),
                        leadingIcon = Icons.Outlined.Lock,
                        onClick = { navigator.navigate(AppLockScreenDestination) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    val secretCode = remember(settingsState) {
                        prefs.getString(PreferenceManager.KEY_SECRET_DIALPAD_CODE, PreferenceManager.DEFAULT_SECRET_DIALPAD_CODE) ?: PreferenceManager.DEFAULT_SECRET_DIALPAD_CODE
                    }
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_private_storage_88),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_secret_dialpad_vault_stored_only_in_app_memory_345, (secretCode).toString()),
                        leadingIcon = Icons.Outlined.FolderShared,
                        onClick = { navigator.navigate(PrivateContactsScreenDestination) }
                    )

                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_permissions_app_setup_202),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_review_granted_permissions_and_system_capabilities_346),
                        leadingIcon = Icons.Outlined.VerifiedUser,
                        onClick = { navigator.navigate(PermissionsChecklistScreenDestination) }
                    )
                }
            }

            // 4. Contacts & Data
            item {
                RivoExpressiveCard(
                    title = stringResource(R.string.settings_contacts_management_title),
                    icon = Icons.Outlined.ManageAccounts
                ) {
                    RivoListItem(
                        headline = stringResource(R.string.settings_contact_management_headline),
                        supporting = stringResource(R.string.settings_contact_management_supporting),
                        leadingIcon = Icons.Outlined.ManageAccounts,
                        onClick = { navigator.navigate(ContactManagementScreenDestination) }
                    )
                    RivoDivider(Modifier.padding(horizontal = 16.dp))
                    RivoListItem(
                        headline = stringResource(R.string.settings_manage_visibility),
                        supporting = stringResource(R.string.settings_manage_visibility_supporting),
                        leadingIcon = Icons.Outlined.Visibility,
                        onClick = { navigator.navigate(ContactVisibilityScreenDestination) }
                    )
                }
            }

            // 5. Support & About
            item {
                RivoExpressiveCard(
                    title = RivoText.get(com.grinch.rivo4.R.string.ui_support_about_347),
                    icon = Icons.Outlined.HelpOutline
                ) {
                    RivoListItem(
                        headline = RivoText.get(com.grinch.rivo4.R.string.ui_about_rivo_352),
                        supporting = RivoText.get(com.grinch.rivo4.R.string.ui_version_open_source_licenses_contributors_353),
                        leadingIcon = Icons.Outlined.Info,
                        onClick = { navigator.navigate(AboutScreenDestination) }
                    )
                }
            }

            item {
                RivoExpressiveCard(title = stringResource(R.string.caller_title), icon = Icons.Outlined.Search) {
                    RivoListItem(headline = stringResource(R.string.caller_title),
                        supporting = stringResource(R.string.caller_settings_summary),
                        leadingIcon = Icons.Outlined.Search,
                        onClick = { navigator.navigate(CallerIdentificationScreenDestination) })
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_copyright),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}
