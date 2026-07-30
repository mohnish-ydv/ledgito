package com.mohnishraj.goldmineledger

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class GoldmineApp : Application() {
    lateinit var container: AppContainer
        private set
    var sessionUnlocked: Boolean = false

    override fun onCreate() {
        super.onCreate()
        RestoreCoordinator.applyPending(this)
        container = AppContainer(this)
        val mode = runCatching { runBlocking { container.settings.theme.first() } }.getOrDefault(ThemeMode.SYSTEM)
        val remindersEnabled = runCatching { runBlocking { container.settings.reminders.first() } }.getOrDefault(false)
        ReminderWorker.ensureChannel(this)
        ReminderWorker.configure(this, remindersEnabled)
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
