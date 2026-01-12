package com.yazan.jetoverlay.domain

import java.io.File

/**
 * Manages the lifecycle and location of on-device LLM model files.
 * Handles path resolution for development and production environments.
 */
object ModelManager {
    private const val MODEL_FILENAME = "gemma-3n-E4B-it-int4.litertlm"
    
    // Default development path on Android emulator
    private const val DEV_PATH = "/data/local/tmp/$MODEL_FILENAME"

    /**
     * Returns the absolute path to the model file if it exists, null otherwise.
     */
    fun getModelPath(): String? {
        val devFile = File(DEV_PATH)
        if (devFile.exists()) return devFile.absolutePath
        
        // Future: Check internal app storage or shared storage
        return null
    }

    /**
     * Returns true if a valid model file is found on the device.
     */
    fun isModelAvailable(): Boolean {
        return getModelPath() != null
    }

    /**
     * Returns the target filename for the model.
     */
    fun getTargetFilename(): String = MODEL_FILENAME
}
