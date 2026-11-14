package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.auth.GoogleAuthManager
import com.example.util.SaleSystemNotifier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        if (!GoogleAuthManager.isFirebaseAvailable(appContext)) return
        val isSignedIn = runCatching { Firebase.auth.currentUser != null }.getOrDefault(false)
        if (isSignedIn &&
            SaleSystemNotifier.isDepotWatchEnabled(appContext) &&
            SaleSystemNotifier.hasNotificationPermission(appContext)
        ) {
            DepotAlertMonitorService.syncServiceState(
                context = appContext,
                isSignedIn = true,
                isAdminOrManager = true
            )
        }
    }
}
