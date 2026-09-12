package it.scudochiamate.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BlockedCallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: BlockedCall)

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllLive(): LiveData<List<BlockedCall>>

    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun countLive(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE reason = 'FOREIGN_PREFIX'")
    fun countForeignLive(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE reason = 'KNOWN_SPAM'")
    fun countSpamLive(): LiveData<Int>

    @Query("SELECT phoneNumber FROM blocked_calls")
    suspend fun getAllNumbers(): List<String>

    @Query("SELECT * FROM blocked_calls WHERE reason = 'SYSTEM_IMPORT' ORDER BY timestamp DESC")
    fun getSystemImportedLive(): LiveData<List<BlockedCall>>

    @Query("DELETE FROM blocked_calls")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(call: BlockedCall)
}
