package com.sdtech.studio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdtech.studio.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sdStudioDataStore: DataStore<Preferences> by preferencesDataStore(name = "sd_studio_settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.sdStudioDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[THEME_MODE] ?: AppSettings.THEME_DARK,
            onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
            lastUsedRatio = preferences[LAST_USED_RATIO] ?: "1:1",
            defaultLogoMode = preferences[DEFAULT_LOGO_MODE] ?: "SAFE"
        )
    }

    val isInitialSeedComplete: Flow<Boolean> = dataStore.data.map { it[INITIAL_SEED_COMPLETE] ?: false }

    suspend fun setThemeMode(value: String) {
        require(value in setOf(AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK))
        dataStore.edit { it[THEME_MODE] = value }
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = value }
    }

    suspend fun setLastUsedRatio(value: String) {
        require(value.isNotBlank())
        dataStore.edit { it[LAST_USED_RATIO] = value }
    }

    suspend fun setDefaultLogoMode(value: String) {
        require(value in setOf("SAFE", "TIGHT"))
        dataStore.edit { it[DEFAULT_LOGO_MODE] = value }
    }

    suspend fun markInitialSeedComplete() {
        dataStore.edit { it[INITIAL_SEED_COMPLETE] = true }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_USED_RATIO = stringPreferencesKey("last_used_ratio")
        val DEFAULT_LOGO_MODE = stringPreferencesKey("default_logo_mode")
        val INITIAL_SEED_COMPLETE = booleanPreferencesKey("initial_seed_complete")
    }
}
