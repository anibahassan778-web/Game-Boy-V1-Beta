package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "save_states")
data class SaveStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val romName: String,
    val slotIndex: Int,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val stateJson: String
)

@Entity(tableName = "cartridge_sram")
data class CartridgeSramEntity(
    @PrimaryKey val romName: String,
    val sramBytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)
