package com.grinch.rivo4.modal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VobizCallRecordDao {
    @Insert
    suspend fun insert(record: VobizCallRecordEntity): Long

    @Insert
    fun insertSync(record: VobizCallRecordEntity): Long

    @Query("SELECT * FROM vobiz_call_log ORDER BY startTime DESC")
    fun observeAll(): Flow<List<VobizCallRecordEntity>>

    @Query("SELECT * FROM vobiz_call_log ORDER BY startTime DESC")
    suspend fun getAll(): List<VobizCallRecordEntity>

    @Query("SELECT * FROM vobiz_call_log ORDER BY startTime DESC")
    fun listNow(): List<VobizCallRecordEntity>

    @Query("DELETE FROM vobiz_call_log WHERE number = :number")
    fun deleteByNumberSync(number: String)

    @Query("DELETE FROM vobiz_call_log WHERE id IN (:ids)")
    fun deleteByIdsSync(ids: List<Long>)

    @Query("DELETE FROM vobiz_call_log")
    suspend fun clear()

    @Query("DELETE FROM vobiz_call_log")
    fun clearSync()
}
