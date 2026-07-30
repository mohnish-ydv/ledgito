package com.mohnishraj.goldmineledger

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val app = applicationContext as? GoldmineApp ?: return Result.success()
        if (!app.container.settings.reminders.first()) return Result.success()
        val profile = app.container.db.profiles().get() ?: return Result.success()
        val today = LocalDate.now().toString()
        val recurring = app.container.db.recurring().dueCount(profile.id, today)
        val workspace = app.container.db.workspace().dueReminderCount(profile.id, today)
        val total = recurring + workspace
        if (total == 0 || !notificationsAllowed(applicationContext)) return Result.success()

        ensureChannel(applicationContext)
        val detail = buildList {
            if (recurring > 0) add("$recurring recurring item${if (recurring == 1) "" else "s"}")
            if (workspace > 0) add("$workspace planned reminder${if (workspace == 1) "" else "s"}")
        }.joinToString(" and ")
        val openApp = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_repeat)
            .setContentTitle("Ledgito money check-in")
            .setContentText("$detail due for review")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail due for review. Open Ledgito to post, reschedule or update them."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "ledgerly_reminders"
        private const val NOTIFICATION_ID = 2401
        private const val WORK_NAME = "ledgerly_daily_reminders"

        fun configure(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val now = ZonedDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun notificationsAllowed(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Money reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Due recurring rules and planned Ledgito items"
                    }
                )
            }
        }
    }
}
