package com.yazan.jetoverlay.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val senderName: String,
    val originalContent: String,
    val veiledContent: String? = null,
    @ColumnInfo(defaultValue = "[]")
    val generatedResponses: List<String> = emptyList(),
    val selectedResponse: String? = null,
    @ColumnInfo(defaultValue = "RECEIVED")
    val status: String = "RECEIVED",
    @ColumnInfo(defaultValue = "UNKNOWN")
    val bucket: String = "UNKNOWN",
    val timestamp: Long = System.currentTimeMillis(),
    val contextTag: String? = null,  // personal, work, social, email, other
    val threadKey: String? = null,
    val snoozedUntil: Long = 0L,
    val retryCount: Int = 0,
    val userInteracted: Boolean = false
)

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }
}
