package com.elsco.mindwaymaths.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferences by preferencesDataStore("mindway_settings")
data class Preferences(val theme: String = "system", val notifications: Boolean = false, val goal: Int = 20,
    val exam: String = "", val language: String = "en", val pdfCacheDays: Int = 7, val crashReporting: Boolean = false)
@Singleton class PreferencesStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val theme = stringPreferencesKey("theme")
    private val notifications = booleanPreferencesKey("notifications")
    private val goal = intPreferencesKey("goal")
    private val exam = stringPreferencesKey("exam")
    private val language = stringPreferencesKey("language")
    private val cache = intPreferencesKey("pdf_cache_days")
    private val crash = booleanPreferencesKey("crash_reporting")
    private val lastNotification = longPreferencesKey("last_notification")
    val flow = context.preferences.data.map { Preferences(it[theme] ?: "system", it[notifications] ?: false,
        it[goal] ?: 20, it[exam] ?: "", it[language] ?: "en", it[cache] ?: 7, it[crash] ?: false) }
    suspend fun update(value: Preferences) { context.preferences.edit {
        it[theme] = value.theme; it[notifications] = value.notifications; it[goal] = value.goal.coerceIn(5, 200)
        it[exam] = value.exam; it[language] = value.language; it[cache] = value.pdfCacheDays.coerceIn(1, 30)
        it[crash] = value.crashReporting
    } }
    suspend fun clear() { context.preferences.edit { it.clear() } }
    suspend fun claimNotificationSlot(now: Long): Boolean {
        var allowed = false
        context.preferences.edit {
            if (it[notifications] == true && now - (it[lastNotification] ?: 0) >= 20 * 60 * 60 * 1000) {
                it[lastNotification] = now; allowed = true
            }
        }
        return allowed
    }
}
