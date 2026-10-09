package com.grinch.rivo4.modal.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * App-owned history of Vobiz SIP calls. The system call log (CallLog.Calls) is
 * still written in parallel so Rivo's Recents UI keeps working unchanged; this
 * table carries the richer Vobiz-specific metadata (status, failure reason,
 * DID, recording reference) needed for reporting and future backend sync.
 */
@Entity(tableName = "vobiz_call_log")
data class VobizCallRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callId: String,
    val direction: String,
    val number: String,
    val displayName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val status: String,
    val failureReason: String,
    val did: String,
    val recordingPath: String,
)
