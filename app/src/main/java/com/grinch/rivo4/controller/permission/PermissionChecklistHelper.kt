package com.grinch.rivo4.controller.permission

import com.grinch.rivo4.controller.util.RivoText
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat

enum class PermissionActionType {
    RUNTIME,
    BATTERY_OPTIMIZATION,
    SETTINGS
}

data class PermissionCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isEssential: Boolean,
    val actionType: PermissionActionType,
    val permissions: List<String> = emptyList()
)

object PermissionChecklistHelper {

    val ESSENTIAL_RUNTIME_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    fun hasContactsPermission(context: Context): Boolean {
        val readContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val writeContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return readContacts && writeContacts
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun getEssentialItems(context: Context): List<PermissionCheckItem> {
        return listOf(
            PermissionCheckItem(
                id = "contacts",
                title = RivoText.get(com.grinch.rivo4.R.string.ui_contacts_76),
                description = RivoText.get(com.grinch.rivo4.R.string.ui_required_to_display_contact_names_show_caller_info_and_search__77),
                icon = Icons.Outlined.Contacts,
                isGranted = hasContactsPermission(context),
                isEssential = true,
                actionType = PermissionActionType.RUNTIME,
                permissions = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            )
        )
    }

    fun getRecommendedItems(context: Context): List<PermissionCheckItem> {
        val items = mutableListOf<PermissionCheckItem>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                PermissionCheckItem(
                    id = "notifications",
                    title = RivoText.get(com.grinch.rivo4.R.string.ui_call_notifications_80),
                    description = RivoText.get(com.grinch.rivo4.R.string.ui_show_incoming_call_banners_active_call_status_and_missed_call__81),
                    icon = Icons.Outlined.Notifications,
                    isGranted = hasNotificationPermission(context),
                    isEssential = false,
                    actionType = PermissionActionType.RUNTIME,
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }

        items.add(
            PermissionCheckItem(
                id = "battery",
                title = RivoText.get(com.grinch.rivo4.R.string.ui_reliable_background_calls_83),
                description = RivoText.get(com.grinch.rivo4.R.string.ui_prevents_system_battery_saver_from_suppressing_incoming_calls__84),
                icon = Icons.Outlined.BatteryChargingFull,
                isGranted = isBatteryOptimizationIgnored(context),
                isEssential = false,
                actionType = PermissionActionType.BATTERY_OPTIMIZATION
            )
        )

        return items
    }

    fun getBatteryOptimizationIntent(context: Context): Intent {
        val directRequest = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (directRequest.resolveActivity(context.packageManager) != null) {
            return directRequest
        }

        // Some OEM builds omit the per-app consent activity. In that case, keep
        // the button useful by opening the system battery-optimization list.
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
}
