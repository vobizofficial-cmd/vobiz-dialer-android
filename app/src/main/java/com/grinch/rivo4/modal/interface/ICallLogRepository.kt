package com.grinch.rivo4.modal.`interface`

import com.grinch.rivo4.modal.data.CallLogEntry
import kotlinx.coroutines.flow.Flow

interface ICallLogRepository {
    fun getCallLogs(): List<CallLogEntry>
    fun observeCallLogs(): Flow<List<CallLogEntry>>
    fun saveCallLog(entry: CallLogEntry)
    fun deleteCallLog(number: String)
    fun deleteCallLogsByIds(ids: List<Long>)
    fun clearCallLogs()
}
