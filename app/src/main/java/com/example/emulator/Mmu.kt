package com.example.emulator

class Mmu {

    var cartridge: Cartridge? = null
    var isCgb: Boolean = false

    lateinit var ppu: Ppu
    lateinit var apu: Apu
    lateinit var timer: Timer

    // VRAM: 2 x 8KB banks
    val vramBank0 = ByteArray(8192)
    val vramBank1 = ByteArray(8192)
    var vramBankSelect: Int = 0

    // WRAM: 8 x 4KB banks
    val wramBanks = Array(8) { ByteArray(4096) }
    var wramBankSelect: Int = 1 // 1..7 in CGB, fixed 1 in DMG

    // OAM & HRAM
    val oam = ByteArray(160)
    val hram = ByteArray(127)

    // Interrupt registers
    var ie: Int = 0x00
    var ifReg: Int = 0xE1

    // Joypad state: 8 bits (Bit 0: Right/A, 1: Left/B, 2: Up/Select, 3: Down/Start)
    // Bit 4: Direction keys select, Bit 5: Action keys select
    var joypadDirections: Int = 0x0F // Unpressed (1s)
    var joypadActions: Int = 0x0F    // Unpressed (1s)
    var joypadSelect: Int = 0x30

    // CGB Speed Switch
    var key1PrepareSpeedSwitch: Boolean = false
    var isDoubleSpeed: Boolean = false

    // CGB HDMA Registers
    var hdma1SourceHigh: Int = 0
    var hdma2SourceLow: Int = 0
    var hdma3DestHigh: Int = 0
    var hdma4DestLow: Int = 0
    var hdma5ModeLength: Int = 0xFF
    var hdmaActive: Boolean = false

    // Serial output string accumulator for Blargg test validation!
    val serialOutputBuffer = StringBuilder()

    fun attachComponents(ppu: Ppu, apu: Apu, timer: Timer) {
        this.ppu = ppu
        this.apu = apu
        this.timer = timer
    }

    fun loadCartridge(cart: Cartridge) {
        cartridge = cart
        isCgb = cart.isCgbSupported
    }

    fun readByte(address: Int): Int {
        val addr = address and 0xFFFF
        return when (addr) {
            in 0x0000..0x7FFF -> cartridge?.readRom(addr) ?: 0xFF

            in 0x8000..0x9FFF -> readVram(addr, vramBankSelect)

            in 0xA000..0xBFFF -> cartridge?.readRam(addr) ?: 0xFF

            in 0xC000..0xCFFF -> wramBanks[0][addr - 0xC000].toInt() and 0xFF

            in 0xD000..0xDFFF -> wramBanks[wramBankSelect][addr - 0xD000].toInt() and 0xFF

            in 0xE000..0xFDFF -> readByte(addr - 0x2000) // Echo RAM

            in 0xFE00..0xFE9F -> oam[addr - 0xFE00].toInt() and 0xFF

            in 0xFEA0..0xFEFF -> 0xFF // Unusable memory

            in 0xFF00..0xFF7F -> readIoRegister(addr)

            in 0xFF80..0xFFFE -> hram[addr - 0xFF80].toInt() and 0xFF

            0xFFFF -> ie

            else -> 0xFF
        }
    }

    fun writeByte(address: Int, value: Int) {
        val addr = address and 0xFFFF
        val valByte = value and 0xFF

        when (addr) {
            in 0x0000..0x7FFF -> cartridge?.writeRom(addr, valByte)

            in 0x8000..0x9FFF -> writeVram(addr, valByte, vramBankSelect)

            in 0xA000..0xBFFF -> cartridge?.writeRam(addr, valByte)

            in 0xC000..0xCFFF -> wramBanks[0][addr - 0xC000] = valByte.toByte()

            in 0xD000..0xDFFF -> wramBanks[wramBankSelect][addr - 0xD000] = valByte.toByte()

            in 0xE000..0xFDFF -> writeByte(addr - 0x2000, valByte) // Echo RAM

            in 0xFE00..0xFE9F -> oam[addr - 0xFE00] = valByte.toByte()

            in 0xFEA0..0xFEFF -> { /* Unusable memory - ignore or trigger OAM bug */ }

            in 0xFF00..0xFF7F -> writeIoRegister(addr, valByte)

            in 0xFF80..0xFFFE -> hram[addr - 0xFF80] = valByte.toByte()

            0xFFFF -> ie = valByte
        }
    }

    fun readVram(address: Int, bank: Int): Int {
        val offset = address - 0x8000
        if (offset !in 0 until 8192) return 0xFF
        return if (bank == 1) vramBank1[offset].toInt() and 0xFF else vramBank0[offset].toInt() and 0xFF
    }

    fun writeVram(address: Int, value: Int, bank: Int) {
        val offset = address - 0x8000
        if (offset !in 0 until 8192) return
        if (bank == 1) {
            vramBank1[offset] = value.toByte()
        } else {
            vramBank0[offset] = value.toByte()
        }
    }

    private fun readIoRegister(address: Int): Int {
        return when (address) {
            0xFF00 -> { // JOYP
                var result = joypadSelect or 0x0F
                if ((joypadSelect and 0x10) == 0) { // Select Direction keys
                    result = result and (joypadDirections or 0xF0)
                }
                if ((joypadSelect and 0x20) == 0) { // Select Action keys
                    result = result and (joypadActions or 0xF0)
                }
                result
            }

            0xFF01 -> 0x00 // SB
            0xFF02 -> 0x7E // SC

            0xFF04 -> timer.div
            0xFF05 -> timer.tima
            0xFF06 -> timer.tma
            0xFF07 -> timer.tac

            0xFF0F -> ifReg or 0xE0

            // APU registers
            0xFF10 -> apu.nr10 or 0x80
            0xFF11 -> apu.nr11 or 0x3F
            0xFF12 -> apu.nr12
            0xFF13 -> 0xFF // Write-only
            0xFF14 -> apu.nr14 or 0xBF

            0xFF16 -> apu.nr21 or 0x3F
            0xFF17 -> apu.nr22
            0xFF18 -> 0xFF // Write-only
            0xFF19 -> apu.nr24 or 0xBF

            0xFF1A -> apu.nr30 or 0x7F
            0xFF1B -> 0xFF
            0xFF1C -> apu.nr32 or 0x9F
            0xFF1D -> 0xFF
            0xFF1E -> apu.nr34 or 0xBF

            0xFF20 -> 0xFF
            0xFF21 -> apu.nr42
            0xFF22 -> apu.nr43
            0xFF23 -> apu.nr44 or 0xBF

            0xFF24 -> apu.nr50
            0xFF25 -> apu.nr51
            0xFF26 -> apu.nr52 or 0x70

            in 0xFF30..0xFF3F -> apu.waveRam[address - 0xFF30].toInt() and 0xFF

            // PPU registers
            0xFF40 -> ppu.lcdc
            0xFF41 -> ppu.stat or 0x80
            0xFF42 -> ppu.scy
            0xFF43 -> ppu.scx
            0xFF44 -> ppu.ly
            0xFF45 -> ppu.lyc
            0xFF46 -> 0xFF
            0xFF47 -> ppu.bgp
            0xFF48 -> ppu.obp0
            0xFF49 -> ppu.obp1
            0xFF4A -> ppu.wy
            0xFF4B -> ppu.wx

            // CGB registers
            0xFF4D -> { // KEY1
                var valRet = 0x7E
                if (isDoubleSpeed) valRet = valRet or 0x80
                if (key1PrepareSpeedSwitch) valRet = valRet or 0x01
                valRet
            }

            0xFF4F -> vramBankSelect or 0xFE

            0xFF51 -> hdma1SourceHigh
            0xFF52 -> hdma2SourceLow
            0xFF53 -> hdma3DestHigh
            0xFF54 -> hdma4DestLow
            0xFF55 -> (if (hdmaActive) 0x00 else 0x80) or (hdma5ModeLength and 0x7F)

            0xFF68 -> ppu.bcps or 0x40
            0xFF69 -> ppu.readCgbBgPalette()
            0xFF6A -> ppu.ocps or 0x40
            0xFF6B -> ppu.readCgbObjPalette()

            0xFF70 -> wramBankSelect or 0xF8

            else -> 0xFF
        }
    }

    private fun writeIoRegister(address: Int, value: Int) {
        when (address) {
            0xFF00 -> joypadSelect = value and 0x30 // P1/JOYP

            0xFF01 -> { /* SB serial data */ }
            0xFF02 -> { // SC serial control
                if (value == 0x81) {
                    val charOut = readByte(0xFF01).toChar()
                    serialOutputBuffer.append(charOut)
                    ifReg = ifReg or 0x08 // Trigger serial interrupt
                }
            }

            0xFF04 -> timer.resetDiv()
            0xFF05 -> timer.tima = value
            0xFF06 -> timer.tma = value
            0xFF07 -> timer.tac = value

            0xFF0F -> ifReg = value and 0x1F

            // APU registers
            0xFF10 -> apu.nr10 = value
            0xFF11 -> apu.nr11 = value
            0xFF12 -> apu.nr12 = value
            0xFF13 -> apu.nr13 = value
            0xFF14 -> {
                apu.nr14 = value
                if ((value and 0x80) != 0) apu.triggerChannel1()
            }

            0xFF21 -> apu.nr21 = value
            0xFF22 -> apu.nr22 = value
            0xFF23 -> apu.nr23 = value
            0xFF24 -> {
                apu.nr24 = value
                if ((value and 0x80) != 0) apu.triggerChannel2()
            }

            0xFF30 -> apu.nr30 = value
            0xFF32 -> apu.nr32 = value
            0xFF33 -> apu.nr33 = value
            0xFF34 -> {
                apu.nr34 = value
                if ((value and 0x80) != 0) apu.triggerChannel3()
            }

            0xFF41 -> apu.nr41 = value
            0xFF42 -> apu.nr42 = value
            0xFF43 -> apu.nr43 = value
            0xFF44 -> {
                apu.nr44 = value
                if ((value and 0x80) != 0) apu.triggerChannel4()
            }

            0xFF24 -> apu.nr50 = value
            0xFF25 -> apu.nr51 = value
            0xFF26 -> apu.nr52 = value

            in 0xFF30..0xFF3F -> apu.waveRam[address - 0xFF30] = value.toByte()

            // PPU registers
            0xFF40 -> {
                val oldLcdc = ppu.lcdc
                ppu.lcdc = value
                if ((oldLcdc and 0x80) == 0 && (value and 0x80) != 0) {
                    ppu.resetClock()
                } else if ((oldLcdc and 0x80) != 0 && (value and 0x80) == 0) {
                    ppu.turnOffLcd()
                }
            }
            0xFF41 -> ppu.stat = (ppu.stat and 0x07) or (value and 0xF8)
            0xFF42 -> ppu.scy = value
            0xFF43 -> ppu.scx = value
            0xFF44 -> ppu.ly = 0 // Write resets LY to 0
            0xFF45 -> {
                ppu.lyc = value
                if ((ppu.lcdc and 0x80) != 0) {
                    ppu.checkLycInterrupt()
                }
            }
            0xFF46 -> triggerOamDma(value)
            0xFF47 -> ppu.bgp = value
            0xFF48 -> ppu.obp0 = value
            0xFF49 -> ppu.obp1 = value
            0xFF4A -> ppu.wy = value
            0xFF4B -> ppu.wx = value

            // CGB registers
            0xFF4D -> key1PrepareSpeedSwitch = (value and 0x01) != 0

            0xFF4F -> if (isCgb) vramBankSelect = value and 0x01

            0xFF51 -> hdma1SourceHigh = value
            0xFF52 -> hdma2SourceLow = value and 0xF0
            0xFF53 -> hdma3DestHigh = value and 0x1F
            0xFF54 -> hdma4DestLow = value and 0xF0
            0xFF55 -> triggerCgbHdma(value)

            0xFF68 -> ppu.bcps = value
            0xFF69 -> ppu.writeCgbBgPalette(value)
            0xFF6A -> ppu.ocps = value
            0xFF6B -> ppu.writeCgbObjPalette(value)

            0xFF70 -> {
                if (isCgb) {
                    var bank = value and 0x07
                    if (bank == 0) bank = 1
                    wramBankSelect = bank
                }
            }
        }
    }

    private fun triggerOamDma(sourceHigh: Int) {
        val baseAddr = sourceHigh shl 8
        for (i in 0 until 160) {
            oam[i] = readByte(baseAddr + i).toByte()
        }
    }

    private fun triggerCgbHdma(value: Int) {
        if (!isCgb) return

        val isHblankDma = (value and 0x80) != 0
        val blocks = (value and 0x7F) + 1
        hdma5ModeLength = value

        if (!isHblankDma) {
            // General DMA: copy all blocks immediately
            val src = (hdma1SourceHigh shl 8) or hdma2SourceLow
            val dest = 0x8000 or ((hdma3DestHigh shl 8) or hdma4DestLow)
            val length = blocks * 16

            for (i in 0 until length) {
                val byteVal = readByte((src + i) and 0xFFFF)
                writeVram((dest + i) and 0xFFFF, byteVal, vramBankSelect)
            }
            hdma5ModeLength = 0xFF
            hdmaActive = false
        } else {
            hdmaActive = true
        }
    }

    fun checkHblankHdma() {
        if (!hdmaActive) return

        val src = (hdma1SourceHigh shl 8) or hdma2SourceLow
        val dest = 0x8000 or ((hdma3DestHigh shl 8) or hdma4DestLow)

        // Copy 16 bytes per HBlank
        for (i in 0 until 16) {
            val byteVal = readByte((src + i) and 0xFFFF)
            writeVram((dest + i) and 0xFFFF, byteVal, vramBankSelect)
        }

        // Advance pointers
        val newSrc = (src + 16) and 0xFFFF
        hdma1SourceHigh = (newSrc ushr 8) and 0xFF
        hdma2SourceLow = newSrc and 0xF0

        val newDest = (dest + 16) and 0x1FFF
        hdma3DestHigh = (newDest ushr 8) and 0x1F
        hdma4DestLow = newDest and 0xF0

        val remainingBlocks = (hdma5ModeLength and 0x7F) - 1
        if (remainingBlocks < 0) {
            hdma5ModeLength = 0xFF
            hdmaActive = false
        } else {
            hdma5ModeLength = 0x80 or remainingBlocks
        }
    }

    fun handleStop() {
        if (key1PrepareSpeedSwitch) {
            isDoubleSpeed = !isDoubleSpeed
            key1PrepareSpeedSwitch = false
        }
    }
}
