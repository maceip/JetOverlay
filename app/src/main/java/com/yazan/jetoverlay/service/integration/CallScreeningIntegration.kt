package com.yazan.jetoverlay.service.integration

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Environment
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.yazan.jetoverlay.data.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

class CallScreeningIntegration : CallScreeningService() {

    private val TAG = "CallScreening"
    // Stub High Priority List
    private val highPriorityNumbers = setOf("+15550101", "+15550102") 

    override fun onScreenCall(details: Call.Details) {
        val handle = details.handle
        val phoneNumber = handle?.schemeSpecificPart ?: "Unknown"
        
        Log.d(TAG, "Screening call from: $phoneNumber")

        if (highPriorityNumbers.contains(phoneNumber)) {
            Log.d(TAG, "High Priority Number. Allowing call through.")
            respondToCall(details, CallResponse.Builder().build())
        } else {
            Log.d(TAG, "Not High Priority. Intercepting.")
            // Allow the call to proceed so we can answer it (if we block, we can't answer)
            respondToCall(details, CallResponse.Builder().build())
            
            // Answer Immediately
            answerCall()
            
            // Start Recording
            startRecording(phoneNumber)
        }
    }

    private fun answerCall() {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Deprecated in API 29, but typically works if permission granted and no other Dialer replacement
                telecomManager.acceptRingingCall()
                Log.d(TAG, "Attempted to answer call.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer call: ${e.message}")
            }
        } else {
            Log.e(TAG, "ANSWER_PHONE_CALLS permission not granted.")
        }
    }

    private fun startRecording(phoneNumber: String) {
        // In a real implementation, we'd start a Foreground Service for recording.
        // For this immediate logic, we'll launch a coroutine or just start the service.
        val intent = Intent(this, CallRecordingService::class.java).apply {
            putExtra("PHONE_NUMBER", phoneNumber)
        }
        startForegroundService(intent)
    }
}
