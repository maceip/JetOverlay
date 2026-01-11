package com.yazan.jetoverlay.data

/**
 * Configuration for how notifications from a specific app should be handled.
 *
 * @param packageName The package name of the app (e.g., "com.whatsapp")
 * @param shouldVeil Whether to process and veil the notification content
 * @param shouldCancel Whether to cancel (hide) the notification from the user after processing
 */
data class NotificationConfig(
    val packageName: String,
    val shouldVeil: Boolean = true,
    val shouldCancel: Boolean = true
)

/**
 * Manager for notification configuration settings.
 * Provides default behavior and allows per-app customization.
 */
object NotificationConfigManager {

    // Default configuration - veil and cancel all notifications
    private val defaultConfig = NotificationConfig(
        packageName = "*",
        shouldVeil = true,
        shouldCancel = true
    )

    // Per-app overrides (package name -> config)
    private val appConfigs = mutableMapOf<String, NotificationConfig>()

    // Apps that should never have notifications cancelled (system critical)
    private val systemApps = setOf(
        "android",
        "com.android.systemui",
        "com.android.providers.downloads"
    )

    /**
     * Get the configuration for a specific app.
     * Returns app-specific config if set, otherwise default config.
     */
    fun getConfig(packageName: String): NotificationConfig {
        // System apps are never cancelled
        if (packageName in systemApps) {
            return NotificationConfig(
                packageName = packageName,
                shouldVeil = false,
                shouldCancel = false
            )
        }

        return appConfigs[packageName] ?: defaultConfig.copy(packageName = packageName)
    }

    /**
     * Set a custom configuration for a specific app.
     */
    fun setConfig(packageName: String, shouldVeil: Boolean, shouldCancel: Boolean) {
        appConfigs[packageName] = NotificationConfig(
            packageName = packageName,
            shouldVeil = shouldVeil,
            shouldCancel = shouldCancel
        )
    }

    /**
     * Remove custom configuration for an app, reverting to defaults.
     */
    fun removeConfig(packageName: String) {
        appConfigs.remove(packageName)
    }

    /**
     * Check if a notification should be veiled (processed for content hiding).
     */
    fun shouldVeil(packageName: String): Boolean {
        return getConfig(packageName).shouldVeil
    }

    /**
     * Check if a notification should be cancelled (hidden from user).
     */
    fun shouldCancel(packageName: String): Boolean {
        return getConfig(packageName).shouldCancel
    }

    /**
     * Get all custom app configurations.
     */
    fun getAllCustomConfigs(): Map<String, NotificationConfig> {
        return appConfigs.toMap()
    }
}
