package com.mohnishraj.goldmineledger

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("goldmine_settings")

data class DashboardSections(
    val monthlyPulse: Boolean = true,
    val budgetPulse: Boolean = true,
    val planAndGrow: Boolean = true,
    val recentActivity: Boolean = true,
    val plannedMoney: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val currency = stringPreferencesKey("currency")
        val hideAmounts = booleanPreferencesKey("hide_amounts")
        val appLock = booleanPreferencesKey("app_lock")
        val reminders = booleanPreferencesKey("reminders")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val dashboardMonthlyPulse = booleanPreferencesKey("dashboard_monthly_pulse")
        val dashboardBudgetPulse = booleanPreferencesKey("dashboard_budget_pulse")
        val dashboardPlanAndGrow = booleanPreferencesKey("dashboard_plan_and_grow")
        val dashboardRecentActivity = booleanPreferencesKey("dashboard_recent_activity")
        val dashboardPlannedMoney = booleanPreferencesKey("dashboard_planned_money")
    }

    private val safeData = context.dataStore.data.catch { emit(emptyPreferences()) }
    val onboarded: Flow<Boolean> = safeData.map { it[Keys.onboarded] ?: false }
    val theme: Flow<ThemeMode> = safeData.map {
        runCatching { ThemeMode.valueOf(it[Keys.theme] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }
    val currency: Flow<String> = safeData.map { it[Keys.currency] ?: "INR" }
    val hideAmounts: Flow<Boolean> = safeData.map { it[Keys.hideAmounts] ?: false }
    val appLock: Flow<Boolean> = safeData.map { it[Keys.appLock] ?: false }
    val reminders: Flow<Boolean> = safeData.map { it[Keys.reminders] ?: false }
    val lastBackupAt: Flow<Long> = safeData.map { it[Keys.lastBackupAt] ?: 0L }

    val dashboardSections: Flow<DashboardSections> = safeData.map { preferences ->
        DashboardSections(
            monthlyPulse = preferences[Keys.dashboardMonthlyPulse] ?: true,
            budgetPulse = preferences[Keys.dashboardBudgetPulse] ?: true,
            planAndGrow = preferences[Keys.dashboardPlanAndGrow] ?: true,
            recentActivity = preferences[Keys.dashboardRecentActivity] ?: true,
            plannedMoney = preferences[Keys.dashboardPlannedMoney] ?: true
        )
    }

    suspend fun finishOnboarding(code: String) = context.dataStore.edit {
        it[Keys.onboarded] = true
        it[Keys.currency] = code
    }
    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setCurrency(value: String) = context.dataStore.edit { it[Keys.currency] = value }
    suspend fun setHideAmounts(value: Boolean) = context.dataStore.edit { it[Keys.hideAmounts] = value }
    suspend fun setAppLock(value: Boolean) = context.dataStore.edit { it[Keys.appLock] = value }
    suspend fun setReminders(value: Boolean) = context.dataStore.edit { it[Keys.reminders] = value }
    suspend fun setLastBackupAt(value: Long) = context.dataStore.edit { it[Keys.lastBackupAt] = value }
    suspend fun setDashboardSections(value: DashboardSections) = context.dataStore.edit {
        it[Keys.dashboardMonthlyPulse] = value.monthlyPulse
        it[Keys.dashboardBudgetPulse] = value.budgetPulse
        it[Keys.dashboardPlanAndGrow] = value.planAndGrow
        it[Keys.dashboardRecentActivity] = value.recentActivity
        it[Keys.dashboardPlannedMoney] = value.plannedMoney
    }
    suspend fun setPreferences(theme: ThemeMode, currency: String) = context.dataStore.edit {
        it[Keys.theme] = theme.name
        it[Keys.currency] = currency
    }
}
