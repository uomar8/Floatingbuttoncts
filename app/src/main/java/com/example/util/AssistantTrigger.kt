package com.example.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.data.InvocationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass

object AssistantTrigger {
    private const val TAG = "AssistantTrigger"

    fun trigger(context: Context, entryPoint: Int, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        CoroutineScope(Dispatchers.IO).launch {
            var isSuccessful = false
            var errorMessage: String? = null

            try {
                val bundle = Bundle().apply {
                    putLong("invocation_time_ms", SystemClock.elapsedRealtime())
                    putInt("omni.entry_point", entryPoint)
                }

                // 1. Get the IVoiceInteractionManagerService class
                val iVimsClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")

                // 2. Fetch the raw "voiceinteraction" system service binder
                val vis = Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String::class.java)
                    .invoke(null, "voiceinteraction")

                if (vis == null) {
                    throw IllegalStateException("Failed to retrieve 'voiceinteraction' service binder. It might be unavailable on this ROM.")
                }

                // 3. Cast the binder to the IVoiceInteractionManagerService interface
                val vims = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, vis)

                if (vims == null) {
                    throw IllegalStateException("Failed to cast binder to IVoiceInteractionManagerService interface.")
                }

                // 4. Forcefully invoke the restricted showSessionFromSession method
                val isTriggered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Android 14+ (includes the extra string arg often used by custom OS frameworks like HyperOS)
                    HiddenApiBypass.invoke(
                        iVimsClass,
                        vims,
                        "showSessionFromSession",
                        null,
                        bundle,
                        7,
                        "hyperOS_home"
                    ) as Boolean
                } else {
                    // Android 13 and below
                    HiddenApiBypass.invoke(
                        iVimsClass,
                        vims,
                        "showSessionFromSession",
                        null,
                        bundle,
                        7
                    ) as Boolean
                }

                isSuccessful = isTriggered
                if (!isSuccessful) {
                    errorMessage = "showSessionFromSession returned false"
                }
                Log.d(TAG, "Invocation result: $isSuccessful")
            } catch (e: SecurityException) {
                errorMessage = "SecurityException: This app needs Default Assistant privileges or system-level clearance. " +
                        "Go to Android Settings -> Default apps -> Assistant, and select this app, or grant BIND_VOICE_INTERACTION via ADB."
                Log.e(TAG, "SecurityException during trigger", e)
            } catch (e: ClassNotFoundException) {
                errorMessage = "ClassNotFoundException: IVoiceInteractionManagerService or service stub class not found. ${e.message}"
                Log.e(TAG, "Reflection error", e)
            } catch (e: NoSuchMethodException) {
                errorMessage = "NoSuchMethodException: Method 'showSessionFromSession' or 'getService' not found. ${e.message}"
                Log.e(TAG, "Reflection error", e)
            } catch (e: Exception) {
                errorMessage = "${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Unexpected error during trigger", e)
            }

            // Save log to local database
            val db = AppDatabase.getDatabase(context)
            val logEntry = InvocationLog(
                entryPoint = entryPoint,
                isSuccessful = isSuccessful,
                errorMessage = errorMessage
            )
            db.invocationLogDao().insertLog(logEntry)

            // Trigger callback on main thread
            withContext(Dispatchers.Main) {
                if (!isSuccessful) {
                    val toastMsg = errorMessage ?: "Trigger failed"
                    Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Voice interaction triggered successfully!", Toast.LENGTH_SHORT).show()
                }
                onResult(isSuccessful, errorMessage)
            }
        }
    }
}
