package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AppRepository
import com.example.data.InvocationLog
import com.example.service.FloatingButtonService
import com.example.util.AssistantTrigger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    val settingsState: StateFlow<AppSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val logsState: StateFlow<List<InvocationLog>> = repository.logs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleFloatingButton(context: Context, isEnabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = settingsState.value
            repository.updateSettings(currentSettings.copy(isButtonEnabled = isEnabled))

            val serviceIntent = Intent(context, FloatingButtonService::class.java)
            if (isEnabled) {
                serviceIntent.action = FloatingButtonService.ACTION_START_SERVICE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                serviceIntent.action = FloatingButtonService.ACTION_STOP_SERVICE
                context.startService(serviceIntent)
            }
        }
    }

    fun updateIconType(iconType: String) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(iconType = iconType))
        }
    }

    fun updateIconColor(colorHex: String) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(iconColorHex = colorHex))
        }
    }

    fun updateIconTint(tintHex: String) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(iconTintHex = tintHex))
        }
    }

    fun updateButtonFixed(isFixed: Boolean) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(isButtonFixed = isFixed))
        }
    }

    fun resetPosition() {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(lastX = 100, lastY = 300))
        }
    }

    fun updateButtonSize(sizeDp: Int) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(buttonSizeDp = sizeDp))
        }
    }

    fun updateButtonOpacity(opacity: Float) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(buttonOpacity = opacity))
        }
    }

    fun updateBgOpacity(opacity: Float) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(bgOpacity = opacity))
        }
    }

    fun updateSymbolOpacity(opacity: Float) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(symbolOpacity = opacity))
        }
    }

    fun updateEntryPoint(entryPoint: Int) {
        viewModelScope.launch {
            val current = settingsState.value
            repository.updateSettings(current.copy(entryPoint = entryPoint))
        }
    }

    fun triggerTestInvocation(context: Context) {
        val entryPoint = settingsState.value.entryPoint
        AssistantTrigger.trigger(context, entryPoint)
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    class Factory(
        private val application: Application,
        private val repository: AppRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
