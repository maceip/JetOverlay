package com.yazan.jetoverlay

import android.app.Application
import android.content.ComponentCallbacks2
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
import com.yazan.jetoverlay.service.IntegrationSyncWorker
import com.yazan.jetoverlay.ui.FloatingBubble
import com.yazan.jetoverlay.ui.OverlayUiState
import com.yazan.jetoverlay.util.CrashHandler
import com.yazan.jetoverlay.util.Logger
import com.yazan.jetoverlay.util.NetworkMonitor
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
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            // Install crash handler first thing
            CrashHandler.install()

            Logger.lifecycle("Application", "onCreate started")
            instance = this

            // Initialize Data Layer globally
            val db = AppDatabase.getDatabase(applicationContext)
            repository = MessageRepository(db.messageDao())
            Logger.lifecycle("Application", "Repository initialized")

            // Initialize Network Monitor for connectivity awareness
            networkMonitor = NetworkMonitor.getInstance(applicationContext)
            Logger.lifecycle("Application", "NetworkMonitor initialized")

            // Initialize SDK
            OverlaySdk.initialize(
                notificationConfig = OverlayNotificationConfig()
            )
            Logger.lifecycle("Application", "SDK initialized")

            // Register default agent overlay content (overlay_1)
            OverlaySdk.registerContent("overlay_1") { payload ->
                Logger.uiState("Application", "Composing AgentOverlay")
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

            // Start the Data Acquisition Service for real-time notifications
            // Note: The service handles connected integrations that need immediate polling
            // Skip service startup during instrumentation tests to avoid ForegroundServiceStartNotAllowedException
            if (!isRunningInstrumentationTest()) {
                startDataAcquisitionService()
                // Also schedule battery-efficient periodic sync via WorkManager
                scheduleIntegrationSync()
            } else {
                Logger.i("Application", "Skipping services startup during instrumentation tests")
            }

            Logger.lifecycle("Application", "onCreate completed successfully")
        } catch (e: Throwable) {
            Logger.e("Application", "CRITICAL: Application onCreate failed", e)
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
        Logger.lifecycle("Application", "Starting DataAcquisitionService")
        DataAcquisitionService.start(this)
    }

    /**
     * Stops the Data Acquisition Service.
     */
    fun stopDataAcquisitionService() {
        Logger.lifecycle("Application", "Stopping DataAcquisitionService")
        DataAcquisitionService.stop(this)
    }

    /**
     * Schedules periodic integration sync via WorkManager.
     * This is battery-efficient and respects doze mode.
     */
    fun scheduleIntegrationSync() {
        Logger.lifecycle("Application", "Scheduling IntegrationSyncWorker")
        IntegrationSyncWorker.schedule(this)
    }

    /**
     * Cancels the scheduled integration sync.
     */
    fun cancelIntegrationSync() {
        Logger.lifecycle("Application", "Cancelling IntegrationSyncWorker")
        IntegrationSyncWorker.cancel(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.i("Application", "onTrimMemory called with level: $level")

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Logger.d("Application", "Memory moderate - no action needed")
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Logger.w("Application", "Memory running low - clearing caches")
                clearNonEssentialCaches()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Logger.w("Application", "Memory critical - aggressive cache clearing")
                clearNonEssentialCaches()
            }
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Logger.d("Application", "UI hidden - clearing UI caches")
                // Clear any UI-related caches when app goes to background
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Logger.w("Application", "Background memory pressure - clearing all caches")
                clearNonEssentialCaches()
            }
        }
    }

    /**
     * Clears non-essential caches to free up memory.
     */
    private fun clearNonEssentialCaches() {
        Logger.d("Application", "Clearing non-essential caches")
        // Currently no large caches to clear, but this is the extension point
        // In the future: clear image caches, processed message caches, etc.
    }
}
