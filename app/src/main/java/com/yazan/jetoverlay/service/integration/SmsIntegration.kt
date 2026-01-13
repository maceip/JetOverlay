package com.yazan.jetoverlay.service.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.yazan.jetoverlay.JetOverlayApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for SMS_RECEIVED intents.
 * Extracts sender number and message body from incoming SMS
 * and ingests them via MessageRepository.
 */
class SmsIntegration : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsIntegration"
        const val SMS_PACKAGE_NAME = "sms"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Ignoring non-SMS intent: ${intent.action}")
            return
        }

        Log.d(TAG, "SMS_RECEIVED broadcast received")

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "No messages found in intent")
                return
            }

            // Group message parts by sender (multi-part SMS handling)
            val messagesBySender = mutableMapOf<String, StringBuilder>()

            for (smsMessage in messages) {
                val sender = smsMessage.displayOriginatingAddress ?: smsMessage.originatingAddress ?: "Unknown"
                val body = smsMessage.displayMessageBody ?: smsMessage.messageBody ?: ""

                messagesBySender.getOrPut(sender) { StringBuilder() }.append(body)
            }

            // Ingest each complete message
            for ((sender, bodyBuilder) in messagesBySender) {
                val body = bodyBuilder.toString()

                if (body.isBlank()) {
                    Log.d(TAG, "Skipping blank message from $sender")
                    continue
                }

                Log.d(TAG, "Processing SMS from $sender: ${body.take(50)}...")

                ingestSmsMessage(context, sender, body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    private fun ingestSmsMessage(context: Context, sender: String, content: String) {
        scope.launch {
            try {
                val repository = JetOverlayApplication.instance.repository

                val id = repository.ingestNotification(
                    packageName = SMS_PACKAGE_NAME,
                    sender = sender,
                    content = content
                )

                Log.d(TAG, "SMS ingested with ID=$id from sender=$sender")

                // Trigger overlay if not active
                launch(Dispatchers.Main) {
                    if (!com.yazan.jetoverlay.api.OverlaySdk.isOverlayActive("agent_bubble")) {
                        Log.d(TAG, "Triggering overlay for SMS message")
                        com.yazan.jetoverlay.api.OverlaySdk.show(
                            context = context,
                            config = com.yazan.jetoverlay.api.OverlayConfig(
                                id = "agent_bubble",
                                type = "overlay_1",
                                initialX = 0,
                                initialY = 120
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ingest SMS message", e)
            }
        }
    }
}
