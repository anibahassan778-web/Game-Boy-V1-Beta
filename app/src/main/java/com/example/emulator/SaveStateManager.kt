package com.example.emulator

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Manages serialization and deserialization of the GameBoy state
 * (CPU registers, MMU & MBC memory banks, PPU registers, Timer state)
 * into raw binary byte arrays and handles persistent local app-specific file storage.
 */
class SaveStateManager {

    companion object {
        private const val MAGIC_HEADER = 0x47425353 // "GBSS"
        private const val FORMAT_VERSION = 1

        /**
         * Serializes the complete current state of a GameBoy instance into a binary ByteArray.
         */
        fun serialize(gameBoy: GameBoy): ByteArray {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            // Header
            dos.writeInt(MAGIC_HEADER)
            dos.writeInt(FORMAT_VERSION)

            // 1. CPU Registers & State
            val cpu = gameBoy.cpu
            dos.writeInt(cpu.a)
            dos.writeInt(cpu.f)
            dos.writeInt(cpu.b)
            dos.writeInt(cpu.c)
            dos.writeInt(cpu.d)
            dos.writeInt(cpu.e)
            dos.writeInt(cpu.h)
            dos.writeInt(cpu.l)
            dos.writeInt(cpu.sp)
            dos.writeInt(cpu.pc)
            dos.writeBoolean(cpu.ime)
            dos.writeInt(cpu.eiPending)
            dos.writeBoolean(cpu.halted)
            dos.writeBoolean(cpu.haltBug)
            dos.writeBoolean(cpu.stopped)

            // 2. PPU State
            val ppu = gameBoy.ppu
            dos.writeInt(ppu.ly)
            dos.writeInt(ppu.lyc)
            dos.writeInt(ppu.lcdc)
            dos.writeInt(ppu.stat)
            dos.writeInt(ppu.scy)
            dos.writeInt(ppu.scx)
            dos.writeInt(ppu.wy)
            dos.writeInt(ppu.wx)
            dos.writeInt(ppu.bgp)
            dos.writeInt(ppu.obp0)
            dos.writeInt(ppu.obp1)
            dos.writeInt(ppu.bcps)
            dos.writeInt(ppu.ocps)
            dos.writeInt(ppu.currentMode)
            dos.writeInt(ppu.modeClock)
            dos.write(ppu.cgbBgPalettes)
            dos.write(ppu.cgbObjPalettes)

            // 3. Timer State
            val timer = gameBoy.timer
            dos.writeInt(timer.div)
            dos.writeInt(timer.tima)
            dos.writeInt(timer.tma)
            dos.writeInt(timer.tac)
            dos.writeInt(timer.divCounter)
            dos.writeInt(timer.timaCounter)

            // 4. MMU State & Memory Banks
            val mmu = gameBoy.mmu
            dos.writeInt(mmu.ie)
            dos.writeInt(mmu.ifReg)
            dos.writeBoolean(mmu.isCgb)
            dos.writeBoolean(mmu.isDoubleSpeed)
            dos.writeBoolean(mmu.key1PrepareSpeedSwitch)
            dos.writeInt(mmu.vramBankSelect)
            dos.writeInt(mmu.wramBankSelect)

            dos.writeInt(mmu.hdma1SourceHigh)
            dos.writeInt(mmu.hdma2SourceLow)
            dos.writeInt(mmu.hdma3DestHigh)
            dos.writeInt(mmu.hdma4DestLow)
            dos.writeInt(mmu.hdma5ModeLength)
            dos.writeBoolean(mmu.hdmaActive)

            // HRAM, OAM, VRAM0, VRAM1
            dos.write(mmu.hram)
            dos.write(mmu.oam)
            dos.write(mmu.vramBank0)
            dos.write(mmu.vramBank1)

            // WRAM Banks (8 x 4096)
            for (i in 0 until 8) {
                dos.write(mmu.wramBanks[i])
            }

            // 5. MBC / Cartridge Bank State & SRAM
            val cartridge = mmu.cartridge
            if (cartridge != null) {
                val sram = cartridge.getSramData()
                dos.writeInt(sram.size)
                if (sram.isNotEmpty()) {
                    dos.write(sram)
                }
                val mbcState = cartridge.mbc.getMbcState()
                dos.writeInt(mbcState.size)
                if (mbcState.isNotEmpty()) {
                    dos.write(mbcState)
                }
            } else {
                dos.writeInt(0)
                dos.writeInt(0)
            }

            dos.flush()
            return baos.toByteArray()
        }

        /**
         * Deserializes a binary ByteArray into a GameBoy instance.
         */
        fun deserialize(gameBoy: GameBoy, data: ByteArray): Boolean {
            if (data.size < 16) return false

            try {
                val bais = ByteArrayInputStream(data)
                val dis = DataInputStream(bais)

                val magic = dis.readInt()
                if (magic != MAGIC_HEADER) return false
                val version = dis.readInt()
                if (version > FORMAT_VERSION) return false

                // 1. CPU Registers
                val cpu = gameBoy.cpu
                cpu.a = dis.readInt()
                cpu.f = dis.readInt()
                cpu.b = dis.readInt()
                cpu.c = dis.readInt()
                cpu.d = dis.readInt()
                cpu.e = dis.readInt()
                cpu.h = dis.readInt()
                cpu.l = dis.readInt()
                cpu.sp = dis.readInt()
                cpu.pc = dis.readInt()
                cpu.ime = dis.readBoolean()
                cpu.eiPending = dis.readInt()
                cpu.halted = dis.readBoolean()
                cpu.haltBug = dis.readBoolean()
                cpu.stopped = dis.readBoolean()

                // 2. PPU State
                val ppu = gameBoy.ppu
                ppu.ly = dis.readInt()
                ppu.lyc = dis.readInt()
                ppu.lcdc = dis.readInt()
                ppu.stat = dis.readInt()
                ppu.scy = dis.readInt()
                ppu.scx = dis.readInt()
                ppu.wy = dis.readInt()
                ppu.wx = dis.readInt()
                ppu.bgp = dis.readInt()
                ppu.obp0 = dis.readInt()
                ppu.obp1 = dis.readInt()
                ppu.bcps = dis.readInt()
                ppu.ocps = dis.readInt()
                ppu.currentMode = dis.readInt()
                ppu.modeClock = dis.readInt()
                dis.readFully(ppu.cgbBgPalettes)
                dis.readFully(ppu.cgbObjPalettes)

                // 3. Timer State
                val timer = gameBoy.timer
                timer.div = dis.readInt()
                timer.tima = dis.readInt()
                timer.tma = dis.readInt()
                timer.tac = dis.readInt()
                timer.divCounter = dis.readInt()
                timer.timaCounter = dis.readInt()

                // 4. MMU State & Memory Banks
                val mmu = gameBoy.mmu
                mmu.ie = dis.readInt()
                mmu.ifReg = dis.readInt()
                mmu.isCgb = dis.readBoolean()
                mmu.isDoubleSpeed = dis.readBoolean()
                mmu.key1PrepareSpeedSwitch = dis.readBoolean()
                mmu.vramBankSelect = dis.readInt()
                mmu.wramBankSelect = dis.readInt()

                mmu.hdma1SourceHigh = dis.readInt()
                mmu.hdma2SourceLow = dis.readInt()
                mmu.hdma3DestHigh = dis.readInt()
                mmu.hdma4DestLow = dis.readInt()
                mmu.hdma5ModeLength = dis.readInt()
                mmu.hdmaActive = dis.readBoolean()

                dis.readFully(mmu.hram)
                dis.readFully(mmu.oam)
                dis.readFully(mmu.vramBank0)
                dis.readFully(mmu.vramBank1)

                for (i in 0 until 8) {
                    dis.readFully(mmu.wramBanks[i])
                }

                // 5. Cartridge / MBC SRAM & Bank State
                val cartridge = mmu.cartridge
                val sramSize = dis.readInt()
                if (sramSize > 0) {
                    val sramBytes = ByteArray(sramSize)
                    dis.readFully(sramBytes)
                    cartridge?.loadSramData(sramBytes)
                }

                val mbcStateSize = dis.readInt()
                if (mbcStateSize > 0) {
                    val mbcBytes = ByteArray(mbcStateSize)
                    dis.readFully(mbcBytes)
                    cartridge?.mbc?.loadMbcState(mbcBytes)
                }

                return true
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        /**
         * Saves progress to local app-specific storage directory (e.g., context.filesDir/save_states/filename).
         */
        fun saveToStorage(context: Context, gameBoy: GameBoy, filename: String): File {
            val saveDir = File(context.filesDir, "save_states")
            if (!saveDir.exists()) {
                saveDir.mkdirs()
            }
            val saveFile = File(saveDir, filename)
            val stateBytes = serialize(gameBoy)
            saveFile.writeBytes(stateBytes)
            return saveFile
        }

        /**
         * Loads progress from local app-specific storage directory.
         */
        fun loadFromStorage(context: Context, gameBoy: GameBoy, filename: String): Boolean {
            val saveDir = File(context.filesDir, "save_states")
            val saveFile = File(saveDir, filename)
            if (!saveFile.exists()) return false

            val stateBytes = saveFile.readBytes()
            return deserialize(gameBoy, stateBytes)
        }

        /**
         * Returns a list of all save state files stored in local app-specific storage.
         */
        fun getSaveStateFiles(context: Context, romName: String? = null): List<File> {
            val saveDir = File(context.filesDir, "save_states")
            if (!saveDir.exists()) return emptyList()

            val files = saveDir.listFiles() ?: return emptyList()
            return if (romName.isNullOrBlank()) {
                files.toList()
            } else {
                files.filter { it.name.startsWith(romName) }.toList()
            }
        }

        /**
         * Deletes a save state file from local app-specific storage.
         */
        fun deleteSaveStateFile(context: Context, filename: String): Boolean {
            val saveDir = File(context.filesDir, "save_states")
            val saveFile = File(saveDir, filename)
            return if (saveFile.exists()) saveFile.delete() else false
        }
    }
}
