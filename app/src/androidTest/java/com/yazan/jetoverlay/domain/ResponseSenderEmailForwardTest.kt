package com.yazan.jetoverlay.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ResponseSenderEmailForwardTest {

    private lateinit var context: Context
    private lateinit var forwardLog: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        forwardLog = File(context.filesDir, "test_email_forward.log")
        if (forwardLog.exists()) {
            forwardLog.delete()
        }
    }

    @Test
    fun forwardWritesToTestMailboxLog() {
        val sender = ResponseSender(context)
        val message = com.yazan.jetoverlay.data.Message(
            id = 42L,
            packageName = "email",
            senderName = "tester",
            originalContent = "body",
            veiledContent = null
        )

        val result = sender.sendResponse(message = message, responseText = "hello world", markAsRead = false)

        // Forwarding mode should always succeed (even if mail client is missing)
        assertTrue(result is ResponseSender.SendResult.Success)

        // Verify the local audit log was written with the expected address
        assertTrue("forward log should be created", forwardLog.exists())
        val contents = forwardLog.readText()
        assertTrue("log should contain message id", contents.contains("message=42"))
        assertTrue("log should contain test mailbox", contents.contains("730011799396-0001@t-online.de"))
    }
}
