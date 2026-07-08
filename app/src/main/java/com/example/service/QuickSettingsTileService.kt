package com.example.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class QuickSettingsTileService : TileService() {

    private lateinit var repository: AppRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        serviceScope.launch {
            val settings = repository.settings.first()
            val tile = qsTile ?: return@launch

            if (settings.isButtonEnabled) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Omni Button: ON"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Omni Button: OFF"
            }

            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val settings = repository.settings.first()
            val newState = !settings.isButtonEnabled

            if (newState) {
                // If turning ON, check overlay permission
                if (!Settings.canDrawOverlays(this@QuickSettingsTileService)) {
                    // Open app to guide the user to grant permission
                    val intent = Intent(this@QuickSettingsTileService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("REQUEST_OVERLAY_PERMISSION", true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Android 14 requires standard startActivityAndCollapse mechanism with pending intent or direct call
                        val pendingIntent = android.app.PendingIntent.getActivity(
                            this@QuickSettingsTileService,
                            0,
                            intent,
                            android.app.PendingIntent.FLAG_IMMUTABLE
                        )
                        startActivityAndCollapse(pendingIntent)
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(intent)
                    }
                    return@launch
                }

                // If permission is OK, start service and update state
                repository.updateSettings(settings.copy(isButtonEnabled = true))
                val serviceIntent = Intent(this@QuickSettingsTileService, FloatingButtonService::class.java).apply {
                    action = FloatingButtonService.ACTION_START_SERVICE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                // Turning OFF, stop service and update state
                repository.updateSettings(settings.copy(isButtonEnabled = false))
                val serviceIntent = Intent(this@QuickSettingsTileService, FloatingButtonService::class.java).apply {
                    action = FloatingButtonService.ACTION_STOP_SERVICE
                }
                startService(serviceIntent)
            }

            updateTileState()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
