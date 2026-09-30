package co.uk.salyant.sdr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val MONITOR_WORK = "salyant_background_monitor"
private const val CHANNEL_ID = "salyant_operations"
private const val PREFS = "salyant_settings"
private const val KEY_LAST_HEALTH = "last_health"

object SalyantMonitorWorker {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SalyantHealthWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MONITOR_WORK, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MONITOR_WORK)
    }

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("background_monitor", true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("background_monitor", value).apply()
        if (value) schedule(context) else cancel(context)
    }

    fun notificationsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("notifications", NotificationManagerCompat.from(context).areNotificationsEnabled())

    fun setNotificationsEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("notifications", value).apply()
        if (value) schedule(context)
    }
}

class SalyantHealthWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ensureChannel(applicationContext)
        SalyantApi.setBaseUrl(SettingsStore.n8nBase(applicationContext))
        val health = SalyantApi.health()
        recordHealth(applicationContext, health.ok)
        maybeNotify(applicationContext, health.ok, health.message)
        return Result.success()
    }

    private fun recordHealth(context: Context, ok: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString("health_history", "") ?: ""
        val next = (existing + if (ok) "1" else "0").takeLast(48)
        prefs.edit().putString("health_history", next).apply()
    }

    private fun maybeNotify(context: Context, ok: Boolean, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getBoolean(KEY_LAST_HEALTH, ok)
        prefs.edit().putBoolean(KEY_LAST_HEALTH, ok).apply()
        if (ok == previous) return
        if (!SalyantMonitorWorker.notificationsEnabled(context)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return
        val title = if (ok) "SALYANT SDR is back online" else "SALYANT SDR needs attention"
        val body = if (ok) "The n8n control endpoint is reachable again." else "Backend health changed: $message"
        NotificationManagerCompat.from(context).notify(
            7401,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_salyant)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "SALYANT operations", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Important SDR backend and operational status changes"
            }
        )
    }
}
