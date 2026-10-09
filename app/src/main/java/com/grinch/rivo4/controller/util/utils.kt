package com.grinch.rivo4.controller.util
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.text.format.DateUtils
import androidx.core.net.toUri
import com.grinch.rivo4.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun isYesterday(timestamp: Long): Boolean {
    return DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS)
}

private fun isSameYear(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
}

private fun getRelativeDay(context: Context, timestamp: Long): String? {
    return when {
        DateUtils.isToday(timestamp) -> context.getString(R.string.date_today)
        isYesterday(timestamp) -> context.getString(R.string.date_yesterday)
        else -> null
    }
}

fun formatDateHeader(context: Context, timestamp: Long): String {
    val relative = getRelativeDay(context, timestamp)
    if (relative != null) return relative

    val pattern = if (isSameYear(timestamp, System.currentTimeMillis())) "MMMM d" else "MMMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

fun formatDate(context: Context, timestamp: Long): String {
    val relative = getRelativeDay(context, timestamp)
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp))

    return if (relative != null) "$relative, $time" else "${formatDateHeader(context, timestamp)}, $time"
}

fun formatTime(context: Context, timestamp: Long): String {
    val time = android.text.format.DateFormat.getTimeFormat(context).format(Date(timestamp))
    return "$time"
}

fun formatDuration(durationSeconds: Long): String {
    return DateUtils.formatElapsedTime(durationSeconds)
}

fun formatPhoneNumber(number: String): String {
    return PhoneNumberUtils.formatNumber(number, Locale.getDefault().country) ?: number
}

fun normalizePhoneNumber(number: String): String {
    return PhoneNumberUtils.normalizeNumber(number)
}

fun areNumbersEqual(num1: String?, num2: String?): Boolean {
    if (num1 == null || num2 == null) return false
    return PhoneNumberUtils.compare(num1, num2)
}

fun deduplicateNumbers(numbers: List<String>): List<String> {
    val unique = mutableListOf<String>()
    numbers.forEach { number ->
        val existingIndex = unique.indexOfFirst { areNumbersEqual(it, number) }
        if (existingIndex == -1) {
            unique.add(number)
        } else {
            val existing = unique[existingIndex]
            if (number.contains("+") && !existing.contains("+")) {
                unique[existingIndex] = number
            } else if (number.length > existing.length && (number.contains("+") == existing.contains("+"))) {
                unique[existingIndex] = number
            }
        }
    }
    return unique
}

fun isVoicemailNumber(context: Context, number: String?): Boolean {
    if (number.isNullOrBlank()) return false
    val clean = number.trim()
    if (clean.equals("voicemail", ignoreCase = true) || clean.startsWith("voicemail:", ignoreCase = true)) {
        return true
    }
    val prefs = PreferenceManager(context)
    val configuredVm = prefs.getString(PreferenceManager.KEY_VOICEMAIL_NUMBER, null)
    if (!configuredVm.isNullOrBlank() && areNumbersEqual(clean, configuredVm)) {
        return true
    }
    return try {
        @Suppress("DEPRECATION")
        PhoneNumberUtils.isVoiceMailNumber(clean)
    } catch (e: Exception) {
        false
    }
}

fun getSystemVoicemailNumber(context: Context): String? {
    // SIM/telecom detection removed with READ_PHONE_STATE: the carrier voicemail
    // number is no longer queryable. Only the user-configured number applies.
    return null
}

fun isWifiConnected(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    } catch (e: Exception) {
        false
    }
}

fun makeCall(context: Context, number: String, contactId: String? = null) {
    val prefs = PreferenceManager(context)
    if (contactId != null) {
        prefs.setLastUsedNumber(contactId, number)
    }

    // Vobiz-only dialer: every call goes over SIP. Never hand numbers to the
    // system dialer / SIM stack (no ACTION_CALL, no ACTION_DIAL, no Telecom).
    if (com.grinch.rivo4.sip.SipCallController.placeCall(context, number)) return

    val msgRes = when {
        !com.grinch.rivo4.auth.CredentialStore.hasSipCredentials() -> R.string.vobiz_not_signed_in
        com.grinch.rivo4.sip.LinphoneService.registrationState.value
            !is com.grinch.rivo4.sip.VobizRegistrationState.Registered -> R.string.vobiz_not_registered
        else -> R.string.vobiz_call_failed
    }
    android.widget.Toast.makeText(context, msgRes, android.widget.Toast.LENGTH_LONG).show()
}

fun openInContacts(context: Context, contactId: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
    }
    context.startActivity(intent)
}

fun openLink(context: Context, link: String) {
    try {
        val uri = link.toUri()
        if (uri.scheme == "tel") {
            // Vobiz-only: dial through SIP instead of the system dialer.
            makeCall(context, uri.schemeSpecificPart ?: link)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getAppVersion(context: Context): Pair<String, Long> {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        val versionName = packageInfo.versionName ?: context.getString(R.string.label_unknown)
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        Pair(versionName, versionCode)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        Pair(context.getString(R.string.label_unknown), -1L)
    }
}

fun isCustomPermissionDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return manufacturer.contains("xiaomi") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("huawei") ||
            manufacturer.contains("meizu") ||
            manufacturer.contains("oneplus")
}
