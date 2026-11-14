package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.auth.GoogleAuthManager
import com.example.data.local.AppDatabase
import com.example.data.remote.FirestorePosRepository
import com.example.data.repository.PosRepository
import com.example.util.SaleSystemNotifier
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

class DepotAlertMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        SaleSystemNotifier.ensureChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ongoingNotification = SaleSystemNotifier.buildMonitorServiceNotification(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SaleSystemNotifier.MONITOR_SERVICE_NOTIFICATION_ID,
                ongoingNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(
                SaleSystemNotifier.MONITOR_SERVICE_NOTIFICATION_ID,
                ongoingNotification
            )
        }

        if (!SaleSystemNotifier.isDepotWatchEnabled(applicationContext) ||
            !GoogleAuthManager.isFirebaseAvailable(applicationContext)
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitorJob?.isActive != true) {
            startRealtimeMonitor(uid)
        }
        return START_STICKY
    }

    private fun startRealtimeMonitor(uid: String) {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val localRepo = PosRepository(applicationContext, AppDatabase.getInstance(applicationContext))
            val dbId = FirestorePosRepository.resolveDatabaseId(applicationContext)
            val remoteRepo = FirestorePosRepository(FirebaseFirestore.getInstance(dbId))

            remoteRepo.observeNotifications(uid)
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) return@retryWhen false
                    Log.w(TAG, "Monitor reconnect after error: ${cause.message}")
                    val backoffMs = (2000L * (1L shl attempt.coerceAtMost(4).toInt())).coerceAtMost(30_000L)
                    delay(backoffMs)
                    true
                }
                .collect { cloudNotifications ->
                    runCatching { localRepo.replaceNotificationsFromCloud(cloudNotifications) }
                    val currentUserId = localRepo.currentUserIdFlow.firstOrNull()
                    val users = localRepo.getUsersSnapshot()
                    val activeUser = users.find { it.id == currentUserId && it.active }
                    val isAdminOrManager = activeUser == null || activeUser.role == "admin" || activeUser.role == "gerant"
                    SaleSystemNotifier.processCloudNotifications(
                        context = applicationContext,
                        notifications = cloudNotifications,
                        isAdminOrManager = isAdminOrManager
                    )
                }
        }
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DepotAlertService"

        fun syncServiceState(
            context: Context,
            isSignedIn: Boolean,
            isAdminOrManager: Boolean
        ) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, DepotAlertMonitorService::class.java)
            val shouldRun = isSignedIn &&
                isAdminOrManager &&
                SaleSystemNotifier.isDepotWatchEnabled(appContext) &&
                SaleSystemNotifier.hasNotificationPermission(appContext)

            runCatching {
                if (shouldRun) {
                    ContextCompat.startForegroundService(appContext, intent)
                } else {
                    appContext.stopService(intent)
                }
            }.onFailure { err ->
                Log.w(TAG, "Could not update DepotAlertMonitorService state: ${err.message}")
            }
        }
    }
}
