package com.grinch.rivo4

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.grinch.rivo4.auth.CredentialStore
import com.grinch.rivo4.fcm.TokenRegistrar
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RivoApp : Application() {

    companion object {
        private const val TAG = "VobizFCM"
        var isAppInForeground: Boolean = false
            private set
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.grinch.rivo4.controller.util.RivoText.resources = resources
    }

    private var resumedActivities = 0

    override fun onCreate() {
        super.onCreate()
        com.grinch.rivo4.controller.util.RivoText.resources = resources
        com.grinch.rivo4.auth.CredentialStore.init(this)
        
        // Initialize Firebase (optional: app must still work without google-services.json)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase init unavailable: ${t.message}")
        }

        startKoin {
            androidContext(this@RivoApp)
            modules(appModule)
        }

        // Register FCM token with backend
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.i(TAG, "FCM token obtained")
                    TokenRegistrar.registerAsync(this, task.result)
                } else {
                    Log.w(TAG, "FCM token fetch failed: ${task.exception?.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FCM unavailable (google-services.json missing?): ${t.message}")
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                isAppInForeground = resumedActivities > 0
            }
            override fun onActivityPaused(activity: Activity) {
                resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
                isAppInForeground = resumedActivities > 0
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
