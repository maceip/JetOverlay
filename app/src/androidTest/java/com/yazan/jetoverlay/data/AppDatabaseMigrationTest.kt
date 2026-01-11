package com.yazan.jetoverlay.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Tests for Room database migrations.
 * These tests verify that database schema changes are handled correctly
 * and existing data is preserved during migrations.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Test Migration 1->2: Adding the 'bucket' column.
     * Verifies that:
     * 1. The migration runs without errors
     * 2. Existing data is preserved
     * 3. New 'bucket' column has default value "UNKNOWN"
     */
    @Test
    @Throws(IOException::class)
    fun migration1To2_addsDefaultBucketColumn() {
        // Create version 1 database with test data
        helper.createDatabase(testDbName, 1).apply {
            // Insert test data using SQL (version 1 schema without bucket column)
            execSQL(
                """
                INSERT INTO messages (
                    id, packageName, senderName, originalContent,
                    veiledContent, generatedResponses, selectedResponse,
                    status, timestamp
                ) VALUES (
                    1, 'com.test.app', 'Test Sender', 'Original message content',
                    NULL, '[]', NULL, 'RECEIVED', 1234567890
                )
                """.trimIndent()
            )
            close()
        }

        // Run migration 1->2
        helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        // Verify migration by checking the data
        val db = helper.openMigrationDatabase(testDbName, 2)
        val cursor = db.query("SELECT * FROM messages WHERE id = 1")

        cursor.moveToFirst()

        // Verify existing data is preserved
        val packageNameIndex = cursor.getColumnIndex("packageName")
        val senderNameIndex = cursor.getColumnIndex("senderName")
        val originalContentIndex = cursor.getColumnIndex("originalContent")
        val statusIndex = cursor.getColumnIndex("status")
        val bucketIndex = cursor.getColumnIndex("bucket")

        assertEquals("com.test.app", cursor.getString(packageNameIndex))
        assertEquals("Test Sender", cursor.getString(senderNameIndex))
        assertEquals("Original message content", cursor.getString(originalContentIndex))
        assertEquals("RECEIVED", cursor.getString(statusIndex))

        // Verify the new bucket column has default value
        assertEquals("UNKNOWN", cursor.getString(bucketIndex))

        cursor.close()
        db.close()
    }

    /**
     * Test that multiple existing records are migrated correctly.
     */
    @Test
    @Throws(IOException::class)
    fun migration1To2_preservesMultipleRecords() {
        helper.createDatabase(testDbName, 1).apply {
            // Insert multiple test records
            for (i in 1..3) {
                execSQL(
                    """
                    INSERT INTO messages (
                        id, packageName, senderName, originalContent,
                        veiledContent, generatedResponses, selectedResponse,
                        status, timestamp
                    ) VALUES (
                        $i, 'com.test.app$i', 'Sender $i', 'Message $i',
                        NULL, '[]', NULL, 'RECEIVED', ${1000L + i}
                    )
                    """.trimIndent()
                )
            }
            close()
        }

        // Run migration
        helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        // Verify all records are migrated
        val db = helper.openMigrationDatabase(testDbName, 2)
        val cursor = db.query("SELECT COUNT(*) FROM messages")
        cursor.moveToFirst()
        assertEquals(3, cursor.getInt(0))
        cursor.close()

        // Verify each record has UNKNOWN bucket
        val bucketCursor = db.query("SELECT bucket FROM messages WHERE bucket = 'UNKNOWN'")
        assertEquals(3, bucketCursor.count)
        bucketCursor.close()

        db.close()
    }

    /**
     * Test that database can be opened after migration with Room.
     * This validates the schema is correctly aligned with the Entity class.
     */
    @Test
    @Throws(IOException::class)
    fun migration1To2_roomCanOpenDatabaseAfterMigration() = runTest {
        // Create version 1 database
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO messages (
                    id, packageName, senderName, originalContent,
                    veiledContent, generatedResponses, selectedResponse,
                    status, timestamp
                ) VALUES (
                    1, 'com.whatsapp', 'Alice', 'Hello from WhatsApp!',
                    'Veiled message', '["Hi!", "Hello"]', NULL, 'PROCESSED', 1234567890
                )
                """.trimIndent()
            )
            close()
        }

        // Run migration
        helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        // Open database with Room and verify data integrity
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            testDbName
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            val message = database.messageDao().getMessageById(1)

            assertNotNull("Message should be retrievable after migration", message)
            assertEquals("com.whatsapp", message?.packageName)
            assertEquals("Alice", message?.senderName)
            assertEquals("Hello from WhatsApp!", message?.originalContent)
            assertEquals("Veiled message", message?.veiledContent)
            assertEquals("PROCESSED", message?.status)
            assertEquals("UNKNOWN", message?.bucket)  // Default value from migration
            assertEquals(listOf("Hi!", "Hello"), message?.generatedResponses)
        } finally {
            database.close()
        }
    }

    /**
     * Test migration with empty database (no existing records).
     */
    @Test
    @Throws(IOException::class)
    fun migration1To2_worksWithEmptyDatabase() {
        // Create empty version 1 database
        helper.createDatabase(testDbName, 1).apply {
            close()
        }

        // Run migration - should complete without errors
        helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        // Verify database is accessible
        val db = helper.openMigrationDatabase(testDbName, 2)
        val cursor = db.query("SELECT COUNT(*) FROM messages")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()
        db.close()
    }

    /**
     * Test that new messages inserted after migration can have custom bucket values.
     */
    @Test
    @Throws(IOException::class)
    fun migration1To2_newMessagesCanHaveCustomBucket() = runTest {
        // Create and migrate database
        helper.createDatabase(testDbName, 1).apply {
            close()
        }
        helper.runMigrationsAndValidate(testDbName, 2, true, AppDatabase.MIGRATION_1_2)

        // Open database with Room
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            testDbName
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        try {
            // Insert a new message with a specific bucket
            val newMessage = Message(
                packageName = "com.slack",
                senderName = "Work Channel",
                originalContent = "New project update",
                bucket = "WORK"
            )
            val id = database.messageDao().insert(newMessage)

            // Verify the bucket was saved correctly
            val retrieved = database.messageDao().getMessageById(id)
            assertNotNull(retrieved)
            assertEquals("WORK", retrieved?.bucket)
        } finally {
            database.close()
        }
    }

    /**
     * Helper method to open the migrated database for verification.
     */
    private fun MigrationTestHelper.openMigrationDatabase(name: String, version: Int) =
        FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
                .name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {}
                })
                .build()
        ).writableDatabase
}
