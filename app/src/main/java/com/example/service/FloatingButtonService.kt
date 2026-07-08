package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.AppRepository
import com.example.util.AssistantTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingButtonService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "omni_floating_button_channel"
        private const val NOTIFICATION_ID = 4851
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var isOverlayShowing = false
    private var params: WindowManager.LayoutParams? = null

    private lateinit var repository: AppRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var currentSettings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)

        // Observe settings changes continuously
        serviceScope.launch {
            repository.settings.collect { settings ->
                currentSettings = settings
                // If coordinates changed (e.g., reset position from main app UI), update layout params
                val layoutParams = params
                val view = composeView
                if (isOverlayShowing && layoutParams != null && view != null) {
                    if (layoutParams.x != settings.lastX || layoutParams.y != settings.lastY) {
                        layoutParams.x = settings.lastX
                        layoutParams.y = settings.lastY
                        try {
                            windowManager?.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            // Stop the service and update the database state
            serviceScope.launch {
                val current = repository.getSettingsDirectly()
                repository.updateSettings(current.copy(isButtonEnabled = false))
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // Standard start
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            val current = repository.getSettingsDirectly()
            currentSettings = current
            if (!isOverlayShowing) {
                showFloatingButton()
            }
        }

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingButton() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            serviceScope.launch {
                val current = repository.getSettingsDirectly()
                repository.updateSettings(current.copy(isButtonEnabled = false))
                stopSelf()
            }
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentSettings.lastX
            y = currentSettings.lastY
        }
        params = layoutParams

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingButtonService)
            setViewTreeViewModelStoreOwner(this@FloatingButtonService)
            setViewTreeSavedStateRegistryOwner(this@FloatingButtonService)

            setContent {
                FloatingButtonUI(repository = repository)
            }
        }

        // Setup smooth drag & click handler on the container view
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        composeView?.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!currentSettings.isButtonFixed) {
                        lp.x = initialX + (event.rawX - initialTouchX).toInt()
                        lp.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager?.updateViewLayout(composeView, lp)
                        } catch (e: Exception) {
                            // View might have been detached
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.rawX - initialTouchX
                    val diffY = event.rawY - initialTouchY
                    // If movement is negligible or it is fixed, consider it a tap
                    if (Math.abs(diffX) < 15 && Math.abs(diffY) < 15) {
                        triggerAction()
                    } else if (!currentSettings.isButtonFixed) {
                        // Drag completed, persist the new coordinate coordinates in the DB
                        serviceScope.launch {
                            val latest = repository.getSettingsDirectly()
                            repository.updateSettings(
                                latest.copy(
                                    lastX = lp.x,
                                    lastY = lp.y
                                )
                            )
                        }
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(composeView, layoutParams)
            isOverlayShowing = true
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            e.printStackTrace()
            // If overlay drawing fails, let the user know and stop
            stopSelf()
        }
    }

    private fun triggerAction() {
        serviceScope.launch {
            val settings = repository.getSettingsDirectly()
            AssistantTrigger.trigger(this@FloatingButtonService, settings.entryPoint)
        }
    }

    private fun removeFloatingButton() {
        if (isOverlayShowing && composeView != null) {
            try {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                windowManager?.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isOverlayShowing = false
            composeView = null
            params = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Omni Button Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Omni floating overlay button active on screen."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingButtonService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Omni Floating Button")
            .setContentText("Overlay active. Tap the button to invoke assistant.")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Turn Off",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        removeFloatingButton()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }
}

@Composable
fun FloatingButtonUI(repository: AppRepository) {
    val settings by repository.settings.collectAsState(initial = AppSettings())

    val iconVector = when (settings.iconType) {
        "omni" -> Icons.Default.Adjust
        "bolt" -> Icons.Default.Bolt
        "star" -> Icons.Default.AutoAwesome
        "assistant" -> Icons.Default.Assistant
        "circle" -> Icons.Default.Lens
        "rocket" -> Icons.Default.RocketLaunch
        else -> Icons.Default.RadioButtonChecked
    }

    val parsedColor = try {
        Color(android.graphics.Color.parseColor(settings.iconColorHex)).copy(alpha = settings.bgOpacity)
    } catch (e: Exception) {
        Color(0xFF6200EE).copy(alpha = settings.bgOpacity) // Fallback default purple
    }

    val parsedTint = try {
        Color(android.graphics.Color.parseColor(settings.iconTintHex)).copy(alpha = settings.symbolOpacity)
    } catch (e: Exception) {
        Color.White.copy(alpha = settings.symbolOpacity) // Fallback default white
    }

    Box(
        modifier = Modifier
            .size(settings.buttonSizeDp.dp)
            .alpha(settings.buttonOpacity)
            .clip(CircleShape)
            .background(parsedColor)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = "Trigger Assistant",
            tint = parsedTint,
            modifier = Modifier.size((settings.buttonSizeDp * 0.55).dp)
        )
    }
}
