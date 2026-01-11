package com.yazan.jetoverlay

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.yazan.jetoverlay.api.OverlayNotificationConfig
import com.yazan.jetoverlay.api.OverlaySdk
import com.yazan.jetoverlay.data.AppDatabase
import com.yazan.jetoverlay.data.Message
import com.yazan.jetoverlay.data.MessageRepository
import com.yazan.jetoverlay.service.DataAcquisitionService
import com.yazan.jetoverlay.ui.FloatingBubble
import com.yazan.jetoverlay.ui.OverlayUiState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class JetOverlayApplication : Application() {

    companion object {
        lateinit var instance: JetOverlayApplication
            private set
    }

    private val applicationScope = MainScope()
    lateinit var repository: MessageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: onCreate started")
            instance = this

            // Initialize Data Layer globally
            val db = AppDatabase.getDatabase(applicationContext)
            repository = MessageRepository(db.messageDao())
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: Repository initialized")

            // Initialize SDK
            OverlaySdk.initialize(
                notificationConfig = OverlayNotificationConfig()
            )
            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: SDK initialized")

            // Register default agent overlay content (overlay_1)
            OverlaySdk.registerContent("overlay_1") { payload ->
                android.util.Log.d("JetOverlayDebug", "OverlayContent: Composing AgentOverlay")
                com.yazan.jetoverlay.ui.AgentOverlay(repository = repository)
            }
            
            // Also register the demo options if needed, or keep them for the UI control panel only.
            // For safety, registering them here ensures they work if triggered from background too.
            com.yazan.jetoverlay.options.forEach { option ->
                OverlaySdk.registerContent(option.id) {
                // ... redundant for now unless we want to support demo shapes from background
                // keeping it simple for now, focusing on the agent bubble.
                }
            }

            // Start the Data Acquisition Service for polling integrations
            // Note: The service will only start polling for integrations that have valid tokens
            // Skip service startup during instrumentation tests to avoid ForegroundServiceStartNotAllowedException
            if (!isRunningInstrumentationTest()) {
                startDataAcquisitionService()
            } else {
                android.util.Log.d("JetOverlayDebug", "Skipping DataAcquisitionService startup during instrumentation tests")
            }

            android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: onCreate completed successfully")
        } catch (e: Throwable) {
            android.util.Log.e("JetOverlayCrash", "CRITICAL: Application onCreate failed", e)
            throw e
        }
    }

    /**
     * Checks if the application is running in an instrumentation test environment.
     * Returns true if we detect AndroidJUnitRunner or similar test instrumentation.
     */
    private fun isRunningInstrumentationTest(): Boolean {
        return try {
            // Check if we're running under instrumentation by looking for test classes
            val registryClass = Class.forName("androidx.test.InstrumentationRegistry")
            // If we can load the test registry, try to get instrumentation via reflection
            val getInstrumentationMethod = registryClass.getMethod("getInstrumentation")
            val instrumentation = getInstrumentationMethod.invoke(null)
            instrumentation != null
        } catch (e: ClassNotFoundException) {
            // Test classes not available - not in test environment
            false
        } catch (e: Exception) {
            // Any other error - likely not in test context
            false
        }
    }

    /**
     * Starts the Data Acquisition Service to coordinate polling for all integrations.
     * Safe to call multiple times - the service will only start once.
     */
    fun startDataAcquisitionService() {
        android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: Starting DataAcquisitionService")
        DataAcquisitionService.start(this)
    }

    /**
     * Stops the Data Acquisition Service.
     */
    fun stopDataAcquisitionService() {
        android.util.Log.d("JetOverlayDebug", "JetOverlayApplication: Stopping DataAcquisitionService")
        DataAcquisitionService.stop(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
