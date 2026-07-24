package com.example.data

import kotlinx.coroutines.flow.Flow

class SaveStateRepository(private val dao: SaveStateDao) {

    fun getSaveStates(romName: String): Flow<List<SaveStateEntity>> = dao.getSaveStatesForRom(romName)

    suspend fun saveState(state: SaveStateEntity) = dao.insertSaveState(state)

    suspend fun deleteState(id: Int) = dao.deleteSaveStateById(id)

    suspend fun getSram(romName: String): CartridgeSramEntity? = dao.getSramForRom(romName)

    suspend fun saveSram(sram: CartridgeSramEntity) = dao.saveSram(sram)
}
