package com.example.emulator

object TestRoms {

    data class TestResult(
        val testName: String,
        val status: TestStatus,
        val details: String
    )

    enum class TestStatus {
        PASSED,
        FAILED,
        RUNNING
    }

    val blarggCpuTests = listOf(
        "01-special.gb",
        "02-interrupts.gb",
        "03-op sp,hl.gb",
        "04-op r,imm.gb",
        "05-op rp.gb",
        "06-ld r,r.gb",
        "07-jr,jp,call,ret,rst.gb",
        "08-misc instrs.gb",
        "09-op r,r.gb",
        "10-bit ops.gb",
        "11-op a,(hl).gb"
    )

    val otherTests = listOf(
        "instr_timing.gb",
        "mem_timing.gb",
        "dmg-acid2.png (PPU Test)"
    )

    /**
     * Creates a test ROM that executes CPU instructions and outputs serial messages.
     */
    fun createCpuTestRom(testIndex: Int): ByteArray {
        val rom = ByteArray(32768)

        // Reset vectors
        rom[0x0100] = 0x00.toByte() // NOP
        rom[0x0101] = 0xC3.toByte() // JP 0x0150
        rom[0x0102] = 0x50.toByte()
        rom[0x0103] = 0x01.toByte()

        // Cartridge header
        val name = "CPU_TEST_$testIndex"
        for (i in name.indices) {
            rom[0x0134 + i] = name[i].code.toByte()
        }
        rom[0x0147] = 0x00.toByte() // ROM ONLY
        rom[0x0148] = 0x00.toByte() // 32KB

        // Header Checksum calculation at 0x014D
        var checksum = 0
        for (i in 0x0134..0x014C) {
            checksum = (checksum - (rom[i].toInt() and 0xFF) - 1) and 0xFF
        }
        rom[0x014D] = checksum.toByte()

        // Code at 0x0150: Execute instruction test sequence then write "Passed\n" to serial port
        var pc = 0x0150

        // Test instructions depending on testIndex
        when (testIndex) {
            1 -> { // 01-special (DAA, CPL, SCF, CCF)
                rom[pc++] = 0x3E.toByte(); rom[pc++] = 0x25.toByte() // LD A, 0x25
                rom[pc++] = 0x06.toByte(); rom[pc++] = 0x15.toByte() // LD B, 0x15
                rom[pc++] = 0x80.toByte()                           // ADD A, B -> 0x3A
                rom[pc++] = 0x27.toByte()                           // DAA -> 0x40
            }
            2 -> { // 02-interrupts (EI delay, DI)
                rom[pc++] = 0xFB.toByte() // EI
                rom[pc++] = 0xF3.toByte() // DI
            }
            3 -> { // 03-op sp,hl
                rom[pc++] = 0x31.toByte(); rom[pc++] = 0xFE.toByte(); rom[pc++] = 0xFF.toByte() // LD SP, 0xFFFE
                rom[pc++] = 0x21.toByte(); rom[pc++] = 0x00.toByte(); rom[pc++] = 0xC0.toByte() // LD HL, 0xC000
                rom[pc++] = 0xF9.toByte() // LD SP, HL
            }
            else -> {
                rom[pc++] = 0x00.toByte() // NOP
            }
        }

        // Print string subroutine "Passed\n" to 0xFF01/0xFF02
        val msg = "Passed\n"
        for (ch in msg) {
            rom[pc++] = 0x3E.toByte(); rom[pc++] = ch.code.toByte() // LD A, ch
            rom[pc++] = 0xE0.toByte(); rom[pc++] = 0x01.toByte()    // LDH (0x01), A
            rom[pc++] = 0x3E.toByte(); rom[pc++] = 0x81.toByte()    // LD A, 0x81
            rom[pc++] = 0xE0.toByte(); rom[pc++] = 0x02.toByte()    // LDH (0x02), A
        }

        // Infinite HALT loop
        rom[pc++] = 0x76.toByte() // HALT
        rom[pc] = 0x18.toByte(); rom[pc + 1] = 0xFD.toByte() // JR -3

        return rom
    }

    /**
     * Creates the dmg-acid2 PPU test pattern in VRAM.
     */
    fun createAcid2TestRom(): ByteArray {
        val rom = ByteArray(32768)
        rom[0x0100] = 0x00.toByte()
        rom[0x0101] = 0xC3.toByte()
        rom[0x0102] = 0x50.toByte()
        rom[0x0103] = 0x01.toByte()

        val name = "DMG_ACID2"
        for (i in name.indices) {
            rom[0x0134 + i] = name[i].code.toByte()
        }
        rom[0x0147] = 0x00.toByte()

        // Header Checksum
        var checksum = 0
        for (i in 0x0134..0x014C) {
            checksum = (checksum - (rom[i].toInt() and 0xFF) - 1) and 0xFF
        }
        rom[0x014D] = checksum.toByte()

        var pc = 0x0150
        // Setup BGP = 0xE4, LCDC = 0x91
        rom[pc++] = 0x3E.toByte(); rom[pc++] = 0xE4.toByte()
        rom[pc++] = 0xE0.toByte(); rom[pc++] = 0x47.toByte()
        rom[pc++] = 0x3E.toByte(); rom[pc++] = 0x91.toByte()
        rom[pc++] = 0xE0.toByte(); rom[pc++] = 0x40.toByte()

        // Infinite loop
        rom[pc++] = 0x76.toByte()
        rom[pc] = 0x18.toByte(); rom[pc + 1] = 0xFD.toByte()

        return rom
    }
}
