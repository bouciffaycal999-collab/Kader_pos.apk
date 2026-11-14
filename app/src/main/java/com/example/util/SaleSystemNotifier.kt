package com.example.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.DepotNotification
import com.example.data.model.formatDa
import com.example.data.model.formatM2

object SaleSystemNotifier {
    const val CHANNEL_SALES_ALERTS = "ceramica_sales_alerts"
    const val CHANNEL_DEPOT_MONITOR = "ceramica_depot_monitor"

    const val EXTRA_OPEN_NOTIFICATIONS = "extra_open_notifications"
    const val EXTRA_INVOICE_ID = "extra_invoice_id"

    const val MONITOR_SERVICE_NOTIFICATION_ID = 9001

    private const val PREFS_NAME = "ceramica_notifier_prefs"
    private const val KEY_SHOWN_IDS = "shown_notification_ids"
    private const val KEY_LOCAL_IDS = "locally_created_ids"
    private const val KEY_BASELINE_INITIALIZED = "baseline_initialized"
    private const val KEY_DEPOT_WATCH_ENABLED = "depot_watch_enabled"
    private const val MAX_STORED_IDS = 500

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val alertsChannel = NotificationChannel(
            CHANNEL_SALES_ALERTS,
            "Ventes & Alertes Dépôt",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications instantanées dès qu'un vendeur enregistre une vente, une facture ou une demande de disponibilité."
            enableVibration(true)
            setShowBadge(true)
        }

        val monitorChannel = NotificationChannel(
            CHANNEL_DEPOT_MONITOR,
            "Veille Temps Réel Dépôt",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintient la réception instantanée des ventes hors application pour la Direction."
            setShowBadge(false)
        }

        nm.createNotificationChannel(alertsChannel)
        nm.createNotificationChannel(monitorChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isDepotWatchEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEPOT_WATCH_ENABLED, true)
    }

    fun setDepotWatchEnabled(context: Context, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEPOT_WATCH_ENABLED, enabled).apply()
    }

    fun recordLocalNotificationIds(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingLocal = prefs.getStringSet(KEY_LOCAL_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        existingLocal.addAll(ids)
        val trimmed = trimSet(existingLocal)
        prefs.edit().putStringSet(KEY_LOCAL_IDS, trimmed).apply()
    }

    fun processCloudNotifications(
        context: Context,
        notifications: List<DepotNotification>,
        isAdminOrManager: Boolean
    ): List<DepotNotification> {
        val appContext = context.applicationContext
        ensureChannels(appContext)

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val shownIds = prefs.getStringSet(KEY_SHOWN_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val localIds = prefs.getStringSet(KEY_LOCAL_IDS, emptySet()) ?: emptySet()
        val baselineDone = prefs.getBoolean(KEY_BASELINE_INITIALIZED, false)

        if (!baselineDone) {
            notifications.filter { it.read }.forEach { shownIds.add(it.id) }
            prefs.edit()
                .putStringSet(KEY_SHOWN_IDS, trimSet(shownIds))
                .putBoolean(KEY_BASELINE_INITIALIZED, true)
                .apply()
        }

        if (!isAdminOrManager) {
            return emptyList()
        }

        val candidates = notifications
            .filter { !it.read && it.id !in shownIds && it.id !in localIds }
            .sortedBy { it.date }

        if (candidates.isEmpty()) return emptyList()
        if (!hasNotificationPermission(appContext)) {
            return emptyList()
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return emptyList()

        val posted = mutableListOf<DepotNotification>()
        candidates.forEach { n ->
            val androidNotif = buildDepotAlertNotification(appContext, n)
            nm.notify(notificationIdFor(n.id), androidNotif)
            shownIds.add(n.id)
            posted.add(n)
        }

        prefs.edit().putStringSet(KEY_SHOWN_IDS, trimSet(shownIds)).apply()
        return posted
    }

    fun sendTestAdminNotification(context: Context): Boolean {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        if (!hasNotificationPermission(appContext)) return false
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        val sample = DepotNotification(
            id = "test_${System.currentTimeMillis()}",
            invoiceId = "",
            reference = "[VENTE] CRM-TEST-ADMIN",
            date = "",
            vendeur = "Amine",
            magasin = "Magasin Oran",
            client = "Client Test Dépôt",
            clientTel = "0550 00 00 00",
            totalNet = 28800.0,
            m2Total = 14.40,
            cartonsTotal = 10,
            articlesSummary = "10 ctn 60/60 BATI LUXOR (14,40 m²)",
            read = false
        )
        nm.notify(9099, buildDepotAlertNotification(appContext, sample))
        return true
    }

    fun buildMonitorServiceNotification(context: Context): Notification {
        ensureChannels(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            MONITOR_SERVICE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_DEPOT_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_ceramica)
            .setContentTitle("CERAMICA Dépôt • En ligne")
            .setContentText("Réception en direct des ventes et alertes de l'équipe active.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    internal fun buildDepotAlertNotification(context: Context, n: DepotNotification): Notification {
        val upperRef = n.reference.uppercase()
        val cleanRef = n.reference
            .removePrefix("[VENTE]")
            .removePrefix("[MODIF]")
            .removePrefix("[SUPPR]")
            .removePrefix("[DISPO]")
            .removePrefix("[ALERTE]")
            .trim()

        val title = when {
            upperRef.contains("[MODIF]") -> "Facture modifiée • ${n.vendeur} (${n.magasin})"
            upperRef.contains("[SUPPR]") -> "Facture supprimée • ${n.vendeur} (${n.magasin})"
            upperRef.contains("[DISPO]") -> "Demande Dispo • ${n.vendeur} (${n.magasin})"
            upperRef.contains("[ALERTE]") -> "Stock bas • ${n.magasin}"
            else -> "Nouvelle Vente • ${n.vendeur} (${n.magasin})"
        }

        val summaryLine = when {
            upperRef.contains("[DISPO]") || upperRef.contains("[ALERTE]") -> n.articlesSummary
            else -> "#$cleanRef • ${n.client} • ${formatDa(n.totalNet)}"
        }

        val bigBody = buildString {
            appendLine(summaryLine)
            if (n.clientTel.isNotBlank()) appendLine("Tél client: ${n.clientTel}")
            if (n.articlesSummary.isNotBlank()) appendLine(n.articlesSummary)
            if (n.cartonsTotal > 0 || n.m2Total > 0.0) {
                append("Volume: ${n.cartonsTotal} ctn • ${formatM2(n.m2Total)} m²")
            }
        }.trim()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
            if (n.invoiceId.isNotBlank()) {
                putExtra(EXTRA_INVOICE_ID, n.invoiceId)
            }
        }
        val reqCode = notificationIdFor(n.id)
        val pendingIntent = PendingIntent.getActivity(
            context,
            reqCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_SALES_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ceramica)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigBody))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun notificationIdFor(docId: String): Int =
        (docId.hashCode() and 0x7FFFFFFF).coerceAtLeast(10_000)

    private fun trimSet(set: Set<String>): Set<String> =
        if (set.size <= MAX_STORED_IDS) set
        else set.toList().takeLast(MAX_STORED_IDS).toSet()
}
