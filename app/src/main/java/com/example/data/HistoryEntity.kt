package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val fileType: String, // APP, IMAGE, VIDEO, MUSIC, DOCUMENT, FILE
    val transferType: String, // SENT, RECEIVED
    val peerName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String // SUCCESS, FAILED, IN_PROGRESS
)
