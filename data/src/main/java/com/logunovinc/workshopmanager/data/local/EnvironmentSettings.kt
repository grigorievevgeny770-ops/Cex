package com.logunovinc.workshopmanager.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.environmentDataStore by preferencesDataStore(name = "environment")
interface EnvironmentSettings {
    suspend fun read(): String
    suspend fun write(baseUrl: String)
}
class DataStoreEnvironmentSettings(private val context: Context, private val defaultUrl: String) : EnvironmentSettings {
    private val key = stringPreferencesKey("base_url")
    override suspend fun read(): String = context.environmentDataStore.data.first()[key] ?: defaultUrl
    override suspend fun write(baseUrl: String) { context.environmentDataStore.edit { it[key] = baseUrl } }
}
