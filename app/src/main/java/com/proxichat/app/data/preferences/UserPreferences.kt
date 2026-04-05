package com.proxichat.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "proxichat_prefs")

class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_AVATAR_INDEX = intPreferencesKey("avatar_index")
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // "system", "on", "off"
        private val KEY_DISCOVERABLE = booleanPreferencesKey("discoverable")
        private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        private val KEY_ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        private val KEY_ACCENT_COLOR = intPreferencesKey("accent_color")
    }

    val displayName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISPLAY_NAME] ?: "User"
    }

    val avatarIndex: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_AVATAR_INDEX] ?: 0
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETE] ?: false
    }

    val darkMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: "system"
    }

    val isDiscoverable: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISCOVERABLE] ?: true
    }

    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_RECONNECT] ?: true
    }

    val encryptionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENCRYPTION_ENABLED] ?: false
    }

    val accentColor: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCENT_COLOR] ?: 0
    }

    suspend fun setDisplayName(name: String) {
        context.dataStore.edit { it[KEY_DISPLAY_NAME] = name }
    }

    suspend fun setAvatarIndex(index: Int) {
        context.dataStore.edit { it[KEY_AVATAR_INDEX] = index }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setDiscoverable(discoverable: Boolean) {
        context.dataStore.edit { it[KEY_DISCOVERABLE] = discoverable }
    }

    suspend fun setAutoReconnect(autoReconnect: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RECONNECT] = autoReconnect }
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setAccentColor(colorIndex: Int) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = colorIndex }
    }
}
