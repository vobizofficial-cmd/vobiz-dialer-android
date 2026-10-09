package com.grinch.rivo4.view.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.grinch.rivo4.R
import com.grinch.rivo4.controller.util.*
import com.grinch.rivo4.modal.data.Contact
import org.koin.compose.koinInject

class CallLauncher(
    private val onInitiate: (String, Contact?) -> Unit
) {
    fun dial(number: String, contact: Contact? = null) {
        onInitiate(number, contact)
    }
}

@Composable
fun rememberCallLauncher(): CallLauncher {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val mobileLabel = stringResource(R.string.label_mobile)

    var showNumberPicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }
    var pendingContact by remember { mutableStateOf<Contact?>(null) }

    val performFinalCall = { number: String, contactId: String? ->
        // Vobiz SIP path - makeCall enforces registration and shows its own errors
        makeCall(context, number, contactId = contactId)
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isSpecial = pendingNumber.startsWith("voicemail:", ignoreCase = true) || pendingNumber.contains('#')
        val micDenied = permissions[Manifest.permission.RECORD_AUDIO] == false
        if (!isSpecial && micDenied) {
            android.widget.Toast.makeText(
                context, com.grinch.rivo4.R.string.vobiz_permission_mic_required, android.widget.Toast.LENGTH_LONG
            ).show()
        } else {
            performFinalCall(pendingNumber, pendingContact?.id)
        }
    }

    val initiateCall = { number: String, contact: Contact? ->
        pendingNumber = number
        pendingContact = contact

        val isSpecial = number.startsWith("voicemail:", ignoreCase = true) || number.contains('#')
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

        when {
            !isSpecial && !micGranted -> callPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            else -> performFinalCall(number, contact?.id)
        }
    }

    val callLauncher = remember {
        CallLauncher { number, contact ->
            if (number.isBlank() && contact != null) {
                if (contact.phoneNumbers.size > 1) {
                    val favNum = prefs.getFavoriteNumber(contact.id)
                    if (favNum != null) {
                        initiateCall(favNum, contact)
                    } else {
                        pendingContact = contact
                        showNumberPicker = true
                    }
                } else if (contact.phoneNumbers.isNotEmpty()) {
                    initiateCall(contact.phoneNumbers.first(), contact)
                }
            } else if (number.isNotBlank()) {
                initiateCall(number, contact)
            }
        }
    }

    if (showNumberPicker && pendingContact != null) {
        val contact = pendingContact!!
        val lastUsed = prefs.getLastUsedNumber(contact.id)
        
        RivoSelectionDialog(
            onDismissRequest = { showNumberPicker = false },
            title = stringResource(R.string.select_number_title),
            items = contact.phoneNumbers,
            itemLabel = { formatPhoneNumber(it) },
            onItemSelected = { selectedNumber ->
                initiateCall(selectedNumber, contact)
            },
            itemSupporting = { mobileLabel },
            icon = Icons.Default.Phone,
            itemIcon = { Icons.Default.Phone },
            isSelected = { areNumbersEqual(lastUsed, it) }
        )
    }

    return callLauncher
}
