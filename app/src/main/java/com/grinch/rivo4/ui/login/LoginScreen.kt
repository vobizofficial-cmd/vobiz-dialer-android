package com.grinch.rivo4.ui.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grinch.rivo4.R
import com.grinch.rivo4.auth.CredentialStore
import com.grinch.rivo4.view.theme.vobizBackground
import com.grinch.rivo4.view.theme.vobizBorder
import com.grinch.rivo4.view.theme.vobizBorderStrong
import com.grinch.rivo4.view.theme.vobizButtonGhost
import com.grinch.rivo4.view.theme.vobizButtonGhostBorder
import com.grinch.rivo4.view.theme.vobizButtonOnPrimary
import com.grinch.rivo4.view.theme.vobizButtonPrimary
import com.grinch.rivo4.view.theme.vobizDanger
import com.grinch.rivo4.view.theme.vobizDangerBg
import com.grinch.rivo4.view.theme.vobizOnPrimary
import com.grinch.rivo4.view.theme.vobizPrimary
import com.grinch.rivo4.view.theme.vobizSuccess
import com.grinch.rivo4.view.theme.vobizSuccessBg
import com.grinch.rivo4.view.theme.vobizSurface
import com.grinch.rivo4.view.theme.vobizSurfaceElevated
import com.grinch.rivo4.view.theme.vobizSurfaceHover
import com.grinch.rivo4.view.theme.vobizText
import com.grinch.rivo4.view.theme.vobizTextDim
import com.grinch.rivo4.view.theme.vobizWarning
import com.grinch.rivo4.view.theme.vobizWarningBg
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val step by viewModel.step.collectAsState()
    val busy = state is LoginUiState.Validating || state is LoginUiState.Registering

    var manualMode by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    var sipUsername by rememberSaveable { mutableStateOf("") }
    var sipPassword by rememberSaveable { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf(CredentialStore.DEFAULT_DOMAIN) }
    var transport by rememberSaveable { mutableStateOf(CredentialStore.DEFAULT_TRANSPORT) }

    var authId by rememberSaveable { mutableStateOf("") }
    var authToken by rememberSaveable { mutableStateOf("") }

    val existingTrunk = remember { CredentialStore.getTrunkConfig() }
    var showTrunkSection by rememberSaveable { mutableStateOf(existingTrunk != null) }
    var trunkUsername by rememberSaveable { mutableStateOf(existingTrunk?.username.orEmpty()) }
    var trunkPassword by rememberSaveable { mutableStateOf(existingTrunk?.password.orEmpty()) }
    var trunkDomain by rememberSaveable { mutableStateOf(existingTrunk?.domain.orEmpty()) }

    val canSkip = remember { CredentialStore.hasSipCredentials() }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        } else {
            pendingAction = null
            viewModel.onPermissionDenied()
        }
    }

    fun connectWithPermissions(action: () -> Unit) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    fun connect() {
        connectWithPermissions {
            if (manualMode) {
                viewModel.onConnectClicked(sipUsername, sipPassword, domain, transport, authId, authToken, trunkUsername, trunkPassword, trunkDomain)
            } else {
                viewModel.onAccountLoginClicked(email, password)
            }
        }
    }

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) {
            delay(1200)
            onLoggedIn()
        } else if (state is LoginUiState.Failure) {
            delay(4000)
            viewModel.onRetry()
        }
    }

    // Reset ViewModel state when LoginScreen is shown (e.g., after logout)
    // This handles the case where ViewModel is retained across Activity recreation
    LaunchedEffect(Unit) {
        viewModel.reset()
    }

    // Theme color accessors
    val primaryColor = vobizPrimary()
    val onPrimaryColor = vobizOnPrimary()
    val surfaceColor = vobizSurface()
    val surfaceElevatedColor = vobizSurfaceElevated()
    val surfaceHoverColor = vobizSurfaceHover()
    val textColor = vobizText()
    val textDimColor = vobizTextDim()
    val borderColor = vobizBorder()
    val borderStrongColor = vobizBorderStrong()
    val buttonPrimaryColor = vobizButtonPrimary()
    val buttonOnPrimaryColor = vobizButtonOnPrimary()
    val buttonGhostColor = vobizButtonGhost()
    val buttonGhostBorderColor = vobizButtonGhostBorder()
    val dangerColor = vobizDanger()
    val dangerBgColor = vobizDangerBg()
    val successColor = vobizSuccess()
    val successBgColor = vobizSuccessBg()
    val warningColor = vobizWarning()
    val warningBgColor = vobizWarningBg()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surfaceColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) +
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = surfaceElevatedColor
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 16.dp,
                        pressedElevation = 20.dp,
                        focusedElevation = 16.dp,
                        hoveredElevation = 16.dp,
                        draggedElevation = 24.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Vobiz Logo - Square with rounded corners
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = surfaceHoverColor,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.logo),
                                    contentDescription = "Vobiz Logo",
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Vobiz Dialer",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Text(
                            text = "Internet Calling Endpoint",
                            fontSize = 14.sp,
                            color = textDimColor
                        )

                        Spacer(Modifier.height(28.dp))

                        if (!manualMode) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text(stringResource(R.string.vobiz_login_email)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = textDimColor
                                    )
                                },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = borderStrongColor,
                                    unfocusedBorderColor = borderColor,
                                    focusedLabelColor = textDimColor,
                                    cursorColor = primaryColor
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(14.dp))
                            SecretTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = stringResource(R.string.vobiz_login_password),
                                enabled = !busy,
                                accentColor = textDimColor,
                                borderColor = borderColor,
                                imeAction = ImeAction.Done,
                                onImeAction = { if (!busy) connect() },
                            )
                        } else {
                            OutlinedTextField(
                                value = sipUsername,
                                onValueChange = { sipUsername = it },
                                label = { Text(stringResource(R.string.vobiz_login_sip_username)) },
                                placeholder = { Text("play2042343509327874733") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = textDimColor
                                    )
                                },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = borderStrongColor,
                                    unfocusedBorderColor = borderColor,
                                    focusedLabelColor = textDimColor,
                                    cursorColor = primaryColor
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(14.dp))
                            SecretTextField(
                                value = sipPassword,
                                onValueChange = { sipPassword = it },
                                label = stringResource(R.string.vobiz_login_sip_password),
                                enabled = !busy,
                                accentColor = textDimColor,
                                borderColor = borderColor,
                                imeAction = ImeAction.Next,
                            )
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = domain,
                                onValueChange = { domain = it },
                                label = { Text(stringResource(R.string.vobiz_login_domain)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = textDimColor
                                    )
                                },
                                singleLine = true,
                                enabled = !busy,
                                shape = RoundedCornerShape(18.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = borderStrongColor,
                                    unfocusedBorderColor = borderColor,
                                    focusedLabelColor = textDimColor,
                                    cursorColor = primaryColor
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.vobiz_login_transport),
                                style = MaterialTheme.typography.labelMedium,
                                color = textDimColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            )
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                listOf("WSS", "TCP", "TLS", "UDP").forEachIndexed { index, option ->
                                    SegmentedButton(
                                        selected = transport.equals(option, ignoreCase = true),
                                        onClick = { transport = option },
                                        enabled = !busy,
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 4),
                                    ) {
                                        Text(option)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // SIP and Advanced controls
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            TextButton(onClick = { manualMode = !manualMode }, enabled = !busy) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = textDimColor)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(
                                        if (manualMode) R.string.vobiz_login_use_account
                                        else R.string.vobiz_login_use_sip
                                    ),
                                    color = textDimColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            TextButton(
                                onClick = { showAdvanced = !showAdvanced },
                                enabled = !busy,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = textDimColor)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.vobiz_login_advanced),
                                    color = textDimColor
                                )
                            }
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = authId,
                                    onValueChange = { authId = it },
                                    label = { Text(stringResource(R.string.vobiz_login_auth_id)) },
                                    placeholder = { Text("MA_XXXXXXXX") },
                                    singleLine = true,
                                    enabled = !busy,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = borderStrongColor,
                                        unfocusedBorderColor = borderColor,
                                        focusedLabelColor = textDimColor,
                                        cursorColor = primaryColor
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(14.dp))
                                SecretTextField(
                                    value = authToken,
                                    onValueChange = { authToken = it },
                                    label = stringResource(R.string.vobiz_login_auth_token),
                                    enabled = !busy,
                                    accentColor = textDimColor,
                                    borderColor = borderColor,
                                    imeAction = ImeAction.Next,
                                )

                                Spacer(Modifier.height(18.dp))
                                TextButton(
                                    onClick = { showTrunkSection = !showTrunkSection },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Advanced — Outbound Trunk (optional)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = textDimColor
                                        )
                                        Text(
                                            text = if (showTrunkSection) "▲ Hide" else "▼ Expand",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textDimColor
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = showTrunkSection) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = trunkUsername,
                                            onValueChange = { trunkUsername = it },
                                            label = { Text("Trunk Username") },
                                            placeholder = { Text("dialer_trunk_auth") },
                                            singleLine = true,
                                            enabled = !busy,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = borderStrongColor,
                                                unfocusedBorderColor = borderColor,
                                                focusedLabelColor = textDimColor,
                                                cursorColor = primaryColor
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Ascii,
                                                imeAction = ImeAction.Next
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        SecretTextField(
                                            value = trunkPassword,
                                            onValueChange = { trunkPassword = it },
                                            label = "Trunk Password",
                                            enabled = !busy,
                                            accentColor = textDimColor,
                                            borderColor = borderColor,
                                            imeAction = ImeAction.Next,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = trunkDomain,
                                            onValueChange = { trunkDomain = it },
                                            label = { Text("Trunk Domain") },
                                            placeholder = { Text("45b8afc2.sip.vobiz.ai") },
                                            singleLine = true,
                                            enabled = !busy,
                                            shape = RoundedCornerShape(18.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = borderStrongColor,
                                                unfocusedBorderColor = borderColor,
                                                focusedLabelColor = textDimColor,
                                                cursorColor = primaryColor
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Uri,
                                                imeAction = ImeAction.Done
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Enter these to route outbound calls through your Vobiz trunk. Leave blank to use the registrar default.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textDimColor,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Connect Button with theme colors and animation
                        Button(
                            onClick = { connect() },
                            enabled = !busy,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonPrimaryColor,
                                contentColor = buttonOnPrimaryColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Crossfade(targetState = busy) { target ->
                                if (target) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = buttonOnPrimaryColor,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        stringResource(R.string.vobiz_login_connect),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = buttonOnPrimaryColor
                                    )
                                }
                            }
                        }

                        if (canSkip) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onLoggedIn, enabled = !busy) {
                                Text(
                                    stringResource(R.string.vobiz_login_skip),
                                    color = textDimColor
                                )
                            }
                        }

                        AnimatedVisibility(visible = state != LoginUiState.Idle) {
                            LoginStatusPopup(
                                state = state,
                                step = step,
                                onRetry = { viewModel.onRetry() },
                                onDismiss = { viewModel.onRetry() },
                                primaryColor = primaryColor,
                                onPrimaryColor = onPrimaryColor,
                                surfaceColor = surfaceColor,
                                surfaceElevatedColor = surfaceElevatedColor,
                                textColor = textColor,
                                textDimColor = textDimColor,
                                borderColor = borderColor,
                                borderStrongColor = borderStrongColor,
                                successColor = successColor,
                                successBgColor = successBgColor,
                                dangerColor = dangerColor,
                                dangerBgColor = dangerBgColor,
                                warningColor = warningColor,
                                warningBgColor = warningBgColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: LoginUiState,
    step: String,
    onRetry: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    when (state) {
        is LoginUiState.Validating, is LoginUiState.Registering -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x22BDBDBD)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFFBDBDBD)
                    )
                    Column {
                        Text(
                            stringResource(
                                if (state is LoginUiState.Validating) R.string.vobiz_status_validating
                                else R.string.vobiz_status_registering
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (step.isNotBlank()) {
                            Text(
                                step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        is LoginUiState.Success -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x331DB954)
            ) {
                Text(
                    stringResource(R.string.vobiz_status_success),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1DB954)
                )
            }
        }

        is LoginUiState.Failure -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x33E53935)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFF6B6B)
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            stringResource(R.string.vobiz_status_retry),
                            color = Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        LoginUiState.Idle -> Unit
    }
}

@Composable
private fun SecretTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    accentColor: Color,
    borderColor: Color,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = accentColor
            )
        },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            unfocusedBorderColor = borderColor,
            focusedLabelColor = accentColor,
            cursorColor = accentColor
        ),
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff
                    else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.vobiz_login_hide_password
                        else R.string.vobiz_login_show_password
                    ),
                    tint = accentColor
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onDone = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() },
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LoginStatusPopup(
    state: LoginUiState,
    step: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    primaryColor: Color,
    onPrimaryColor: Color,
    surfaceColor: Color,
    surfaceElevatedColor: Color,
    textColor: Color,
    textDimColor: Color,
    borderColor: Color,
    borderStrongColor: Color,
    successColor: Color,
    successBgColor: Color,
    dangerColor: Color,
    dangerBgColor: Color,
    warningColor: Color,
    warningBgColor: Color,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Dimmed overlay - tap to dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            // Empty content - just for overlay tap handling
        }

        // Popup card
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    slideInVertically(
                        initialOffsetY = { -it / 6 },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                    slideOutVertically(
                        targetOffsetY = { -it / 6 },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = surfaceElevatedColor
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is LoginUiState.Validating, is LoginUiState.Registering -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = primaryColor,
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(
                                    if (state is LoginUiState.Validating) R.string.vobiz_status_validating
                                    else R.string.vobiz_status_registering
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            if (step.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    step,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textDimColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        is LoginUiState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = successColor
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.vobiz_status_success),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = successColor
                            )
                        }

                        is LoginUiState.Failure -> {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = dangerColor
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = dangerColor,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            TextButton(onClick = onRetry) {
                                Text(
                                    stringResource(R.string.vobiz_status_retry),
                                    color = dangerColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LoginUiState.Idle -> Unit
                    }
                }
            }
        }
    }
}
