package com.esalinify.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "esalinify_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private val HAS_ONBOARDED = booleanPreferencesKey("has_onboarded")
    }

    val hasOnboarded: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_ONBOARDED] ?: false
        }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_ONBOARDED] = value
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
