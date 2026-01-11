package com.yazan.jetoverlay.data

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for NotificationConfig and NotificationConfigManager.
 */
class NotificationConfigTest {

    @Before
    fun setUp() {
        // Clear any custom configs before each test
        NotificationConfigManager.getAllCustomConfigs().keys.forEach {
            NotificationConfigManager.removeConfig(it)
        }
    }

    @After
    fun tearDown() {
        // Clean up after tests
        NotificationConfigManager.getAllCustomConfigs().keys.forEach {
            NotificationConfigManager.removeConfig(it)
        }
    }

    @Test
    fun `default config should veil and cancel notifications`() {
        val config = NotificationConfigManager.getConfig("com.whatsapp")
        assertTrue(config.shouldVeil)
        assertTrue(config.shouldCancel)
        assertEquals("com.whatsapp", config.packageName)
    }

    @Test
    fun `system apps should not be veiled or cancelled`() {
        val androidConfig = NotificationConfigManager.getConfig("android")
        assertFalse(androidConfig.shouldVeil)
        assertFalse(androidConfig.shouldCancel)

        val systemUiConfig = NotificationConfigManager.getConfig("com.android.systemui")
        assertFalse(systemUiConfig.shouldVeil)
        assertFalse(systemUiConfig.shouldCancel)

        val downloadsConfig = NotificationConfigManager.getConfig("com.android.providers.downloads")
        assertFalse(downloadsConfig.shouldVeil)
        assertFalse(downloadsConfig.shouldCancel)
    }

    @Test
    fun `custom config should override default`() {
        NotificationConfigManager.setConfig("com.example.app", shouldVeil = true, shouldCancel = false)

        val config = NotificationConfigManager.getConfig("com.example.app")
        assertTrue(config.shouldVeil)
        assertFalse(config.shouldCancel)
    }

    @Test
    fun `removing config should revert to default`() {
        NotificationConfigManager.setConfig("com.example.app", shouldVeil = false, shouldCancel = false)
        NotificationConfigManager.removeConfig("com.example.app")

        val config = NotificationConfigManager.getConfig("com.example.app")
        assertTrue(config.shouldVeil)
        assertTrue(config.shouldCancel)
    }

    @Test
    fun `shouldVeil convenience method works correctly`() {
        assertTrue(NotificationConfigManager.shouldVeil("com.whatsapp"))
        assertFalse(NotificationConfigManager.shouldVeil("android"))

        NotificationConfigManager.setConfig("com.test.app", shouldVeil = false, shouldCancel = true)
        assertFalse(NotificationConfigManager.shouldVeil("com.test.app"))
    }

    @Test
    fun `shouldCancel convenience method works correctly`() {
        assertTrue(NotificationConfigManager.shouldCancel("com.whatsapp"))
        assertFalse(NotificationConfigManager.shouldCancel("android"))

        NotificationConfigManager.setConfig("com.test.app", shouldVeil = true, shouldCancel = false)
        assertFalse(NotificationConfigManager.shouldCancel("com.test.app"))
    }

    @Test
    fun `getAllCustomConfigs returns all custom configurations`() {
        NotificationConfigManager.setConfig("com.app1", shouldVeil = true, shouldCancel = false)
        NotificationConfigManager.setConfig("com.app2", shouldVeil = false, shouldCancel = true)

        val configs = NotificationConfigManager.getAllCustomConfigs()
        assertEquals(2, configs.size)
        assertTrue(configs.containsKey("com.app1"))
        assertTrue(configs.containsKey("com.app2"))
    }

    @Test
    fun `data class equals and copy work correctly`() {
        val config1 = NotificationConfig("com.test", true, true)
        val config2 = NotificationConfig("com.test", true, true)
        val config3 = config1.copy(shouldCancel = false)

        assertEquals(config1, config2)
        assertNotEquals(config1, config3)
        assertFalse(config3.shouldCancel)
    }
}
