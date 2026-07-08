package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(private val database: AppDatabase) {
    private val settingsDao = database.appSettingsDao()
    private val logDao = database.invocationLogDao()

    val settings: Flow<AppSettings> = settingsDao.getSettingsFlow().map { it ?: AppSettings() }
    val logs: Flow<List<InvocationLog>> = logDao.getLogsFlow()

    suspend fun getSettingsDirectly(): AppSettings {
        return settingsDao.getSettings() ?: AppSettings()
    }

    suspend fun updateSettings(settings: AppSettings) {
        settingsDao.insertSettings(settings)
    }

    suspend fun addLog(log: InvocationLog) {
        logDao.insertLog(log)
    }

    suspend fun clearLogs() {
        logDao.clearLogs()
    }
}
