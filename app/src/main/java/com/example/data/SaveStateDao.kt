package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveStateDao {

    @Query("SELECT * FROM save_states WHERE romName = :romName ORDER BY slotIndex ASC")
    fun getSaveStatesForRom(romName: String): Flow<List<SaveStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaveState(state: SaveStateEntity)

    @Query("DELETE FROM save_states WHERE id = :id")
    suspend fun deleteSaveStateById(id: Int)

    @Query("SELECT * FROM cartridge_sram WHERE romName = :romName LIMIT 1")
    suspend fun getSramForRom(romName: String): CartridgeSramEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSram(sram: CartridgeSramEntity)
}
