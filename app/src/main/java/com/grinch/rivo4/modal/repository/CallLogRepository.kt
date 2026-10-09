package com.grinch.rivo4.modal.repository

import android.content.Context
import android.provider.CallLog
import com.grinch.rivo4.R
import com.grinch.rivo4.modal.`interface`.ICallLogRepository
import com.grinch.rivo4.modal.data.CallLogEntry
import com.grinch.rivo4.modal.`interface`.IContactsRepository
import com.grinch.rivo4.modal.data.Contact
import com.grinch.rivo4.modal.db.VobizCallRecordDao
import com.grinch.rivo4.modal.db.VobizCallRecordEntity
import com.grinch.rivo4.controller.util.normalizePhoneNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallLogRepository(
    private val context: Context,
    private val contactsRepo: IContactsRepository,
    private val dao: VobizCallRecordDao
) : ICallLogRepository {

    private val preferenceManager = com.grinch.rivo4.controller.util.PreferenceManager(context)

    private fun contactMap(): Map<String, Contact> {
        val allContacts = try { contactsRepo.getContacts() } catch (e: Exception) { emptyList() }
        val contactMap = mutableMapOf<String, Contact>()
        allContacts.forEach { contact ->
            contact.phoneNumbers.forEach { number ->
                val normalized = normalizePhoneNumber(number)
                val key = if (normalized.length >= 10) normalized.takeLast(10) else normalized
                contactMap[key] = contact
            }
        }
        return contactMap
    }

    private fun lookupKey(number: String): String {
        val normalized = normalizePhoneNumber(number)
        return if (normalized.length >= 10) normalized.takeLast(10) else normalized
    }

    private fun mapEntities(entities: List<VobizCallRecordEntity>): List<CallLogEntry> {
        val contactMap = contactMap()
        val unknownLabel = context.getString(R.string.label_unknown)
        val tempLogs = mutableListOf<CallLogEntry>()

        for (record in entities) {
            val number = record.number.ifEmpty { unknownLabel }
            if (!preferenceManager.isHiddenContactsVisible() && contactsRepo.isNumberHidden(number)) {
                continue
            }
            val type = when {
                record.direction == "outgoing" -> CallLog.Calls.OUTGOING_TYPE
                record.status == "Completed" || record.status == "Answered elsewhere" -> CallLog.Calls.INCOMING_TYPE
                else -> CallLog.Calls.MISSED_TYPE
            }
            val matchedContact = contactMap[lookupKey(number)]
            val displayName = matchedContact?.name ?: record.displayName.ifEmpty { number }
            val photoUri = matchedContact?.photoUri
            val contactId = matchedContact?.id

            val lastEntry = tempLogs.lastOrNull()
            if (lastEntry != null && lastEntry.number == number) {
                tempLogs[tempLogs.size - 1] = lastEntry.copy(
                    types = lastEntry.types + type,
                    ids = lastEntry.ids + record.id
                )
            } else {
                tempLogs.add(
                    CallLogEntry(
                        id = record.id,
                        number = number,
                        name = displayName,
                        type = type,
                        date = record.startTime,
                        duration = record.durationSeconds,
                        photoUri = photoUri,
                        contactId = contactId,
                        simLabel = null,
                        types = listOf(type),
                        ids = listOf(record.id)
                    )
                )
            }
        }
        return tempLogs
    }

    override fun getCallLogs(): List<CallLogEntry> = mapEntities(dao.listNow())

    override fun observeCallLogs(): Flow<List<CallLogEntry>> = dao.observeAll().map { mapEntities(it) }

    override fun saveCallLog(entry: CallLogEntry) {
        try {
            dao.insertSync(
                VobizCallRecordEntity(
                    callId = entry.id.toString(),
                    direction = if (entry.type == CallLog.Calls.OUTGOING_TYPE) "outgoing" else "incoming",
                    number = entry.number,
                    displayName = entry.name ?: "",
                    startTime = entry.date,
                    endTime = entry.date + entry.duration * 1000,
                    durationSeconds = entry.duration,
                    status = when {
                        entry.type == CallLog.Calls.OUTGOING_TYPE -> "Completed"
                        entry.type == CallLog.Calls.MISSED_TYPE -> "Missed"
                        else -> "Completed"
                    },
                    failureReason = "",
                    did = "",
                    recordingPath = ""
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun deleteCallLog(number: String) {
        try {
            dao.deleteByNumberSync(number)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun deleteCallLogsByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            dao.deleteByIdsSync(ids)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun clearCallLogs() {
        try {
            dao.clearSync()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
