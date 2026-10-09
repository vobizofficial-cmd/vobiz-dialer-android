package com.grinch.rivo4.modal.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Writes finished Vobiz calls into the app-owned [vobiz_call_log] table.
 * Fire-and-forget by design: history must never break the call teardown path.
 */
object VobizCallHistory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dao: VobizCallRecordDao?
        get() = runCatching { GlobalContext.get().get<VobizCallRecordDao>() }.getOrNull()

    fun record(
        callId: String,
        direction: String,
        number: String,
        displayName: String,
        startTime: Long,
        endTime: Long,
        durationSeconds: Long,
        status: String,
        failureReason: String,
        did: String,
        recordingPath: String,
    ) {
        scope.launch {
            dao?.insert(
                VobizCallRecordEntity(
                    callId = callId,
                    direction = direction,
                    number = number,
                    displayName = displayName,
                    startTime = startTime,
                    endTime = endTime,
                    durationSeconds = durationSeconds,
                    status = status,
                    failureReason = failureReason,
                    did = did,
                    recordingPath = recordingPath,
                )
            )
        }
    }
}
