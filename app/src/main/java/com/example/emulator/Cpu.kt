package com.example.emulator

import java.io.Serializable

/**
 * Cycle-accurate Sharp SM83 / LR35902 CPU implementation for Game Boy / Game Boy Color.
 * Operates at 4.194304 MHz (DMG) / 8.388608 MHz (CGB Double Speed).
 */
class Cpu(private val mmu: Mmu) {

    // 8-bit Registers
    var a: Int = 0x01
    var f: Int = 0xB0 // Z=1, N=0, H=1, C=1 for DMG boot
    var b: Int = 0x00
    var c: Int = 0x13
    var d: Int = 0x00
    var e: Int = 0xD8
    var h: Int = 0x01
    var l: Int = 0x4D

    // 16-bit Registers
    var sp: Int = 0xFFFE
    var pc: Int = 0x0100

    // Interrupt Control
    var ime: Boolean = false
    var eiPending: Int = 0
    var halted: Boolean = false
    var haltBug: Boolean = false
    var stopped: Boolean = false

    // Flags helper getters/setters
    var flagZ: Boolean
        get() = (f and 0x80) != 0
        set(value) { f = if (value) f or 0x80 else f and 0x7F }

    var flagN: Boolean
        get() = (f and 0x40) != 0
        set(value) { f = if (value) f or 0x40 else f and 0xBF }

    var flagH: Boolean
        get() = (f and 0x20) != 0
        set(value) { f = if (value) f or 0x20 else f and 0xDF }

    var flagC: Boolean
        get() = (f and 0x10) != 0
        set(value) { f = if (value) f or 0x10 else f and 0xEF }

    // 16-bit register pair accessors
    var af: Int
        get() = (a shl 8) or (f and 0xF0)
        set(value) {
            a = (value ushr 8) and 0xFF
            f = value and 0xF0
        }

    var bc: Int
        get() = (b shl 8) or c
        set(value) {
            b = (value ushr 8) and 0xFF
            c = value and 0xFF
        }

    var de: Int
        get() = (d shl 8) or e
        set(value) {
            d = (value ushr 8) and 0xFF
            e = value and 0xFF
        }

    var hl: Int
        get() = (h shl 8) or l
        set(value) {
            h = (value ushr 8) and 0xFF
            l = value and 0xFF
        }

    // Reset CPU registers to standard post-boot values
    fun reset(isCgb: Boolean = false) {
        if (isCgb) {
            a = 0x11
            f = 0x80
            b = 0x00
            c = 0x00
            d = 0xFF
            e = 0x56
            h = 0x00
            l = 0x0D
        } else {
            a = 0x01
            f = 0xB0
            b = 0x00
            c = 0x13
            d = 0x00
            e = 0xD8
            h = 0x01
            l = 0x4D
        }
        sp = 0xFFFE
        pc = 0x0100
        ime = false
        eiPending = 0
        halted = false
        haltBug = false
        stopped = false
    }

    /**
     * Executes one instruction and returns the T-cycles consumed.
     */
    fun step(): Int {
        // Handle pending EI delay
        if (eiPending > 0) {
            eiPending--
            if (eiPending == 0) {
                ime = true
            }
        }

        // Handle interrupts
        val interruptCycles = handleInterrupts()
        if (interruptCycles > 0) {
            return interruptCycles
        }

        if (halted) {
            return 4 // 1 M-cycle while halted
        }

        if (stopped) {
            return 4
        }

        // Fetch opcode
        val opcode = fetchByte()

        // Execute opcode
        return executeOpcode(opcode)
    }

    private fun fetchByte(): Int {
        val byte = mmu.readByte(pc)
        if (haltBug) {
            haltBug = false
            // PC is not incremented on halt bug opcode fetch
        } else {
            pc = (pc + 1) and 0xFFFF
        }
        return byte
    }

    private fun fetchWord(): Int {
        val low = fetchByte()
        val high = fetchByte()
        return (high shl 8) or low
    }

    private fun pushWord(value: Int) {
        sp = (sp - 1) and 0xFFFF
        mmu.writeByte(sp, (value ushr 8) and 0xFF)
        sp = (sp - 1) and 0xFFFF
        mmu.writeByte(sp, value and 0xFF)
    }

    private fun popWord(): Int {
        val low = mmu.readByte(sp)
        sp = (sp + 1) and 0xFFFF
        val high = mmu.readByte(sp)
        sp = (sp + 1) and 0xFFFF
        return (high shl 8) or low
    }

    private fun handleInterrupts(): Int {
        val ie = mmu.ie
        val ifReg = mmu.ifReg
        val pending = ie and ifReg and 0x1F

        if (pending != 0) {
            if (halted) {
                halted = false
            }
            if (stopped) {
                stopped = false
            }

            if (ime) {
                ime = false
                pushWord(pc)

                // Service highest priority interrupt
                val interruptBit = when {
                    (pending and 0x01) != 0 -> 0x01 // VBlank
                    (pending and 0x02) != 0 -> 0x02 // STAT
                    (pending and 0x04) != 0 -> 0x04 // Timer
                    (pending and 0x08) != 0 -> 0x08 // Serial
                    else -> 0x10                    // Joypad
                }

                mmu.ifReg = mmu.ifReg and interruptBit.inv()

                pc = when (interruptBit) {
                    0x01 -> 0x0040
                    0x02 -> 0x0048
                    0x04 -> 0x0050
                    0x08 -> 0x0058
                    else -> 0x0060
                }

                return 20 // 5 M-cycles (20 T-cycles) for interrupt dispatch
            }
        }
        return 0
    }

    private fun executeOpcode(opcode: Int): Int {
        return when (opcode) {
            // NOP
            0x00 -> 4

            // LD rr, d16
            0x01 -> { bc = fetchWord(); 12 }
            0x11 -> { de = fetchWord(); 12 }
            0x21 -> { hl = fetchWord(); 12 }
            0x31 -> { sp = fetchWord(); 12 }

            // LD (rr), A
            0x02 -> { mmu.writeByte(bc, a); 8 }
            0x12 -> { mmu.writeByte(de, a); 8 }

            // INC rr
            0x03 -> { bc = (bc + 1) and 0xFFFF; 8 }
            0x13 -> { de = (de + 1) and 0xFFFF; 8 }
            0x23 -> { hl = (hl + 1) and 0xFFFF; 8 }
            0x33 -> { sp = (sp + 1) and 0xFFFF; 8 }

            // INC r
            0x04 -> { b = inc8(b); 4 }
            0x0C -> { c = inc8(c); 4 }
            0x14 -> { d = inc8(d); 4 }
            0x1C -> { e = inc8(e); 4 }
            0x24 -> { h = inc8(h); 4 }
            0x2C -> { l = inc8(l); 4 }
            0x34 -> { mmu.writeByte(hl, inc8(mmu.readByte(hl))); 12 }
            0x3C -> { a = inc8(a); 4 }

            // DEC r
            0x05 -> { b = dec8(b); 4 }
            0x0D -> { c = dec8(c); 4 }
            0x15 -> { d = dec8(d); 4 }
            0x1D -> { e = dec8(e); 4 }
            0x25 -> { h = dec8(h); 4 }
            0x2D -> { l = dec8(l); 4 }
            0x35 -> { mmu.writeByte(hl, dec8(mmu.readByte(hl))); 12 }
            0x3D -> { a = dec8(a); 4 }

            // LD r, d8
            0x06 -> { b = fetchByte(); 8 }
            0x0E -> { c = fetchByte(); 8 }
            0x16 -> { d = fetchByte(); 8 }
            0x1E -> { e = fetchByte(); 8 }
            0x26 -> { h = fetchByte(); 8 }
            0x2E -> { l = fetchByte(); 8 }
            0x36 -> { mmu.writeByte(hl, fetchByte()); 12 }
            0x3E -> { a = fetchByte(); 8 }

            // RLCA / RRLA / RRRCA / RRA
            0x07 -> { rlca(); 4 }
            0x0F -> { rrca(); 4 }
            0x17 -> { rla(); 4 }
            0x1F -> { rra(); 4 }

            // LD (a16), SP
            0x08 -> {
                val addr = fetchWord()
                mmu.writeByte(addr, sp and 0xFF)
                mmu.writeByte((addr + 1) and 0xFFFF, (sp ushr 8) and 0xFF)
                20
            }

            // ADD HL, rr
            0x09 -> { addHl(bc); 8 }
            0x19 -> { addHl(de); 8 }
            0x29 -> { addHl(hl); 8 }
            0x39 -> { addHl(sp); 8 }

            // LD A, (rr)
            0x0A -> { a = mmu.readByte(bc); 8 }
            0x1A -> { a = mmu.readByte(de); 8 }

            // DEC rr
            0x0B -> { bc = (bc - 1) and 0xFFFF; 8 }
            0x1B -> { de = (de - 1) and 0xFFFF; 8 }
            0x2B -> { hl = (hl - 1) and 0xFFFF; 8 }
            0x3B -> { sp = (sp - 1) and 0xFFFF; 8 }

            // STOP
            0x10 -> {
                fetchByte() // Dummy byte
                mmu.handleStop()
                stopped = true
                4
            }

            // JR r8
            0x18 -> { jr(true); 12 }
            0x20 -> jr(!flagZ)
            0x28 -> jr(flagZ)
            0x30 -> jr(!flagC)
            0x38 -> jr(flagC)

            // LDI / LDD
            0x22 -> { mmu.writeByte(hl, a); hl = (hl + 1) and 0xFFFF; 8 }
            0x2A -> { a = mmu.readByte(hl); hl = (hl + 1) and 0xFFFF; 8 }
            0x32 -> { mmu.writeByte(hl, a); hl = (hl - 1) and 0xFFFF; 8 }
            0x3A -> { a = mmu.readByte(hl); hl = (hl - 1) and 0xFFFF; 8 }

            // DAA / CPL / SCF / CCF
            0x27 -> { daa(); 4 }
            0x2F -> { a = a.xor(0xFF); flagN = true; flagH = true; 4 }
            0x37 -> { flagN = false; flagH = false; flagC = true; 4 }
            0x3F -> { flagN = false; flagH = false; flagC = !flagC; 4 }

            // LD r, r instructions (0x40 to 0x7F)
            0x40 -> { 4 } // B, B
            0x41 -> { b = c; 4 }
            0x42 -> { b = d; 4 }
            0x43 -> { b = e; 4 }
            0x44 -> { b = h; 4 }
            0x45 -> { b = l; 4 }
            0x46 -> { b = mmu.readByte(hl); 8 }
            0x47 -> { b = a; 4 }

            0x48 -> { c = b; 4 }
            0x49 -> { 4 } // C, C
            0x4A -> { c = d; 4 }
            0x4B -> { c = e; 4 }
            0x4C -> { c = h; 4 }
            0x4D -> { c = l; 4 }
            0x4E -> { c = mmu.readByte(hl); 8 }
            0x4F -> { c = a; 4 }

            0x50 -> { d = b; 4 }
            0x51 -> { d = c; 4 }
            0x52 -> { 4 } // D, D
            0x53 -> { d = e; 4 }
            0x54 -> { d = h; 4 }
            0x55 -> { d = l; 4 }
            0x56 -> { d = mmu.readByte(hl); 8 }
            0x57 -> { d = a; 4 }

            0x58 -> { e = b; 4 }
            0x59 -> { e = c; 4 }
            0x5A -> { e = d; 4 }
            0x5B -> { 4 } // E, E
            0x5C -> { e = h; 4 }
            0x5D -> { e = l; 4 }
            0x5E -> { e = mmu.readByte(hl); 8 }
            0x5F -> { e = a; 4 }

            0x60 -> { h = b; 4 }
            0x61 -> { h = c; 4 }
            0x62 -> { h = d; 4 }
            0x63 -> { h = e; 4 }
            0x64 -> { 4 } // H, H
            0x65 -> { h = l; 4 }
            0x66 -> { h = mmu.readByte(hl); 8 }
            0x67 -> { h = a; 4 }

            0x68 -> { l = b; 4 }
            0x69 -> { l = c; 4 }
            0x6A -> { l = d; 4 }
            0x6B -> { l = e; 4 }
            0x6C -> { l = h; 4 }
            0x6D -> { 4 } // L, L
            0x6E -> { l = mmu.readByte(hl); 8 }
            0x6F -> { l = a; 4 }

            0x70 -> { mmu.writeByte(hl, b); 8 }
            0x71 -> { mmu.writeByte(hl, c); 8 }
            0x72 -> { mmu.writeByte(hl, d); 8 }
            0x73 -> { mmu.writeByte(hl, e); 8 }
            0x74 -> { mmu.writeByte(hl, h); 8 }
            0x75 -> { mmu.writeByte(hl, l); 8 }
            0x76 -> {
                // HALT instruction
                val pending = mmu.ie and mmu.ifReg and 0x1F
                if (!ime && pending != 0) {
                    haltBug = true
                } else {
                    halted = true
                }
                4
            }
            0x77 -> { mmu.writeByte(hl, a); 8 }

            0x78 -> { a = b; 4 }
            0x79 -> { a = c; 4 }
            0x7A -> { a = d; 4 }
            0x7B -> { a = e; 4 }
            0x7C -> { a = h; 4 }
            0x7D -> { a = l; 4 }
            0x7E -> { a = mmu.readByte(hl); 8 }
            0x7F -> { 4 } // A, A

            // ALU Operations (ADD, ADC, SUB, SBC, AND, XOR, OR, CP)
            0x80 -> { add8(b); 4 }
            0x81 -> { add8(c); 4 }
            0x82 -> { add8(d); 4 }
            0x83 -> { add8(e); 4 }
            0x84 -> { add8(h); 4 }
            0x85 -> { add8(l); 4 }
            0x86 -> { add8(mmu.readByte(hl)); 8 }
            0x87 -> { add8(a); 4 }

            0x88 -> { adc8(b); 4 }
            0x89 -> { adc8(c); 4 }
            0x8A -> { adc8(d); 4 }
            0x8B -> { adc8(e); 4 }
            0x8C -> { adc8(h); 4 }
            0x8D -> { adc8(l); 4 }
            0x8E -> { adc8(mmu.readByte(hl)); 8 }
            0x8F -> { adc8(a); 4 }

            0x90 -> { sub8(b); 4 }
            0x91 -> { sub8(c); 4 }
            0x92 -> { sub8(d); 4 }
            0x93 -> { sub8(e); 4 }
            0x94 -> { sub8(h); 4 }
            0x95 -> { sub8(l); 4 }
            0x96 -> { sub8(mmu.readByte(hl)); 8 }
            0x97 -> { sub8(a); 4 }

            0x98 -> { sbc8(b); 4 }
            0x99 -> { sbc8(c); 4 }
            0x9A -> { sbc8(d); 4 }
            0x9B -> { sbc8(e); 4 }
            0x9C -> { sbc8(h); 4 }
            0x9D -> { sbc8(l); 4 }
            0x9E -> { sbc8(mmu.readByte(hl)); 8 }
            0x9F -> { sbc8(a); 4 }

            0xA0 -> { and8(b); 4 }
            0xA1 -> { and8(c); 4 }
            0xA2 -> { and8(d); 4 }
            0xA3 -> { and8(e); 4 }
            0xA4 -> { and8(h); 4 }
            0xA5 -> { and8(l); 4 }
            0xA6 -> { and8(mmu.readByte(hl)); 8 }
            0xA7 -> { and8(a); 4 }

            0xA8 -> { xor8(b); 4 }
            0xA9 -> { xor8(c); 4 }
            0xAA -> { xor8(d); 4 }
            0xAB -> { xor8(e); 4 }
            0xAC -> { xor8(h); 4 }
            0xAD -> { xor8(l); 4 }
            0xAE -> { xor8(mmu.readByte(hl)); 8 }
            0xAF -> { xor8(a); 4 }

            0xB0 -> { or8(b); 4 }
            0xB1 -> { or8(c); 4 }
            0xB2 -> { or8(d); 4 }
            0xB3 -> { or8(e); 4 }
            0xB4 -> { or8(h); 4 }
            0xB5 -> { or8(l); 4 }
            0xB6 -> { or8(mmu.readByte(hl)); 8 }
            0xB7 -> { or8(a); 4 }

            0xB8 -> { cp8(b); 4 }
            0xB9 -> { cp8(c); 4 }
            0xBA -> { cp8(d); 4 }
            0xBB -> { cp8(e); 4 }
            0xBC -> { cp8(h); 4 }
            0xBD -> { cp8(l); 4 }
            0xBE -> { cp8(mmu.readByte(hl)); 8 }
            0xBF -> { cp8(a); 4 }

            // RET cc
            0xC0 -> ret(!flagZ)
            0xC8 -> ret(flagZ)
            0xD0 -> ret(!flagC)
            0xD8 -> ret(flagC)

            // POP rr
            0xC1 -> { bc = popWord(); 12 }
            0xD1 -> { de = popWord(); 12 }
            0xE1 -> { hl = popWord(); 12 }
            0xF1 -> { af = popWord(); 12 }

            // JP cc, a16
            0xC2 -> jp(!flagZ)
            0xCA -> jp(flagZ)
            0xD2 -> jp(!flagC)
            0xDA -> jp(flagC)

            // JP a16
            0xC3 -> { pc = fetchWord(); 16 }

            // CALL cc, a16
            0xC4 -> call(!flagZ)
            0xCC -> call(flagZ)
            0xD4 -> call(!flagC)
            0xDC -> call(flagC)

            // PUSH rr
            0xC5 -> { pushWord(bc); 16 }
            0xD5 -> { pushWord(de); 16 }
            0xE5 -> { pushWord(hl); 16 }
            0xF5 -> { pushWord(af); 16 }

            // ALU immediate
            0xC6 -> { add8(fetchByte()); 8 }
            0xCE -> { adc8(fetchByte()); 8 }
            0xD6 -> { sub8(fetchByte()); 8 }
            0xDE -> { sbc8(fetchByte()); 8 }
            0xE6 -> { and8(fetchByte()); 8 }
            0xEE -> { xor8(fetchByte()); 8 }
            0xF6 -> { or8(fetchByte()); 8 }
            0xFE -> { cp8(fetchByte()); 8 }

            // RST vectors
            0xC7 -> { rst(0x0000); 16 }
            0xCF -> { rst(0x0008); 16 }
            0xD7 -> { rst(0x0010); 16 }
            0xDF -> { rst(0x0018); 16 }
            0xE7 -> { rst(0x0020); 16 }
            0xEF -> { rst(0x0028); 16 }
            0xF7 -> { rst(0x0030); 16 }
            0xFF -> { rst(0x0038); 16 }

            // RET / RETI
            0xC9 -> { pc = popWord(); 16 }
            0xD9 -> { pc = popWord(); ime = true; 16 }

            // CB prefix
            0xCB -> executeCbOpcode(fetchByte())

            // CALL a16
            0xCD -> {
                val target = fetchWord()
                pushWord(pc)
                pc = target
                24
            }

            // LDH / LD (C)
            0xE0 -> { mmu.writeByte(0xFF00 or fetchByte(), a); 12 }
            0xE2 -> { mmu.writeByte(0xFF00 or c, a); 8 }
            0xF0 -> { a = mmu.readByte(0xFF00 or fetchByte()); 12 }
            0xF2 -> { a = mmu.readByte(0xFF00 or c); 8 }

            // ADD SP, r8
            0xE8 -> {
                val offset = fetchByte().toByte().toInt()
                val result = (sp + offset) and 0xFFFF
                flagZ = false
                flagN = false
                flagH = ((sp and 0x0F) + (offset and 0x0F)) > 0x0F
                flagC = ((sp and 0xFF) + (offset and 0xFF)) > 0xFF
                sp = result
                16
            }

            // JP (HL)
            0xE9 -> { pc = hl; 4 }

            // LD (a16), A / LD A, (a16)
            0xEA -> { mmu.writeByte(fetchWord(), a); 16 }
            0xFA -> { a = mmu.readByte(fetchWord()); 16 }

            // DI / EI
            0xF3 -> { ime = false; eiPending = 0; 4 }
            0xFB -> { eiPending = 2; 4 } // Takes effect after the next instruction

            // LD HL, SP+r8
            0xF8 -> {
                val offset = fetchByte().toByte().toInt()
                val result = (sp + offset) and 0xFFFF
                flagZ = false
                flagN = false
                flagH = ((sp and 0x0F) + (offset and 0x0F)) > 0x0F
                flagC = ((sp and 0xFF) + (offset and 0xFF)) > 0xFF
                hl = result
                12
            }

            // LD SP, HL
            0xF9 -> { sp = hl; 8 }

            else -> 4 // Invalid opcode
        }
    }

    private fun executeCbOpcode(cbOpcode: Int): Int {
        val regIndex = cbOpcode and 0x07
        val bitIndex = (cbOpcode ushr 3) and 0x07
        val group = (cbOpcode ushr 6) and 0x03

        fun getVal(): Int {
            return when (regIndex) {
                0 -> b
                1 -> c
                2 -> d
                3 -> e
                4 -> h
                5 -> l
                6 -> mmu.readByte(hl)
                else -> a
            }
        }

        fun setVal(v: Int) {
            val res = v and 0xFF
            when (regIndex) {
                0 -> b = res
                1 -> c = res
                2 -> d = res
                3 -> e = res
                4 -> h = res
                5 -> l = res
                6 -> mmu.writeByte(hl, res)
                else -> a = res
            }
        }

        val extraCycles = if (regIndex == 6) 8 else 0

        when (group) {
            0 -> {
                // Rotates and Shifts
                val valIn = getVal()
                val valOut = when (bitIndex) {
                    0 -> rlc(valIn)
                    1 -> rrc(valIn)
                    2 -> rl(valIn)
                    3 -> rr(valIn)
                    4 -> sla(valIn)
                    5 -> sra(valIn)
                    6 -> swap(valIn)
                    else -> srl(valIn)
                }
                setVal(valOut)
                return 8 + extraCycles
            }
            1 -> {
                // BIT b, r
                val valIn = getVal()
                val bitVal = (valIn ushr bitIndex) and 1
                flagZ = (bitVal == 0)
                flagN = false
                flagH = true
                return if (regIndex == 6) 12 else 8
            }
            2 -> {
                // RES b, r
                val valIn = getVal()
                val valOut = valIn and (1 shl bitIndex).inv()
                setVal(valOut)
                return 8 + extraCycles
            }
            3 -> {
                // SET b, r
                val valIn = getVal()
                val valOut = valIn or (1 shl bitIndex)
                setVal(valOut)
                return 8 + extraCycles
            }
        }
        return 8
    }

    // Helper functions for ALU
    private fun inc8(valIn: Int): Int {
        val res = (valIn + 1) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = (valIn and 0x0F) == 0x0F
        return res
    }

    private fun dec8(valIn: Int): Int {
        val res = (valIn - 1) and 0xFF
        flagZ = (res == 0)
        flagN = true
        flagH = (valIn and 0x0F) == 0x00
        return res
    }

    private fun add8(valIn: Int) {
        val sum = a + valIn
        val res = sum and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = ((a and 0x0F) + (valIn and 0x0F)) > 0x0F
        flagC = sum > 0xFF
        a = res
    }

    private fun adc8(valIn: Int) {
        val carry = if (flagC) 1 else 0
        val sum = a + valIn + carry
        val res = sum and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = ((a and 0x0F) + (valIn and 0x0F) + carry) > 0x0F
        flagC = sum > 0xFF
        a = res
    }

    private fun sub8(valIn: Int) {
        val diff = a - valIn
        val res = diff and 0xFF
        flagZ = (res == 0)
        flagN = true
        flagH = (a and 0x0F) < (valIn and 0x0F)
        flagC = a < valIn
        a = res
    }

    private fun sbc8(valIn: Int) {
        val carry = if (flagC) 1 else 0
        val diff = a - valIn - carry
        val res = diff and 0xFF
        flagZ = (res == 0)
        flagN = true
        flagH = (a and 0x0F) < ((valIn and 0x0F) + carry)
        flagC = a < (valIn + carry)
        a = res
    }

    private fun and8(valIn: Int) {
        a = a and valIn
        flagZ = (a == 0)
        flagN = false
        flagH = true
        flagC = false
    }

    private fun xor8(valIn: Int) {
        a = a xor valIn
        flagZ = (a == 0)
        flagN = false
        flagH = false
        flagC = false
    }

    private fun or8(valIn: Int) {
        a = a or valIn
        flagZ = (a == 0)
        flagN = false
        flagH = false
        flagC = false
    }

    private fun cp8(valIn: Int) {
        val diff = a - valIn
        flagZ = (diff and 0xFF) == 0
        flagN = true
        flagH = (a and 0x0F) < (valIn and 0x0F)
        flagC = a < valIn
    }

    private fun addHl(valIn: Int) {
        val sum = hl + valIn
        flagN = false
        flagH = ((hl and 0x0FFF) + (valIn and 0x0FFF)) > 0x0FFF
        flagC = sum > 0xFFFF
        hl = sum and 0xFFFF
    }

    private fun jr(cond: Boolean): Int {
        val offset = fetchByte().toByte().toInt()
        return if (cond) {
            pc = (pc + offset) and 0xFFFF
            12
        } else {
            8
        }
    }

    private fun jp(cond: Boolean): Int {
        val addr = fetchWord()
        return if (cond) {
            pc = addr
            16
        } else {
            12
        }
    }

    private fun call(cond: Boolean): Int {
        val addr = fetchWord()
        return if (cond) {
            pushWord(pc)
            pc = addr
            24
        } else {
            12
        }
    }

    private fun ret(cond: Boolean): Int {
        return if (cond) {
            pc = popWord()
            20
        } else {
            8
        }
    }

    private fun rst(vector: Int) {
        pushWord(pc)
        pc = vector
    }

    private fun rlca() {
        val carry = (a ushr 7) and 1
        a = ((a shl 1) or carry) and 0xFF
        flagZ = false
        flagN = false
        flagH = false
        flagC = (carry != 0)
    }

    private fun rrca() {
        val carry = a and 1
        a = ((a ushr 1) or (carry shl 7)) and 0xFF
        flagZ = false
        flagN = false
        flagH = false
        flagC = (carry != 0)
    }

    private fun rla() {
        val oldCarry = if (flagC) 1 else 0
        val newCarry = (a ushr 7) and 1
        a = ((a shl 1) or oldCarry) and 0xFF
        flagZ = false
        flagN = false
        flagH = false
        flagC = (newCarry != 0)
    }

    private fun rra() {
        val oldCarry = if (flagC) 1 else 0
        val newCarry = a and 1
        a = ((a ushr 1) or (oldCarry shl 7)) and 0xFF
        flagZ = false
        flagN = false
        flagH = false
        flagC = (newCarry != 0)
    }

    private fun rlc(valIn: Int): Int {
        val carry = (valIn ushr 7) and 1
        val res = ((valIn shl 1) or carry) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (carry != 0)
        return res
    }

    private fun rrc(valIn: Int): Int {
        val carry = valIn and 1
        val res = ((valIn ushr 1) or (carry shl 7)) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (carry != 0)
        return res
    }

    private fun rl(valIn: Int): Int {
        val oldCarry = if (flagC) 1 else 0
        val newCarry = (valIn ushr 7) and 1
        val res = ((valIn shl 1) or oldCarry) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (newCarry != 0)
        return res
    }

    private fun rr(valIn: Int): Int {
        val oldCarry = if (flagC) 1 else 0
        val newCarry = valIn and 1
        val res = ((valIn ushr 1) or (oldCarry shl 7)) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (newCarry != 0)
        return res
    }

    private fun sla(valIn: Int): Int {
        val carry = (valIn ushr 7) and 1
        val res = (valIn shl 1) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (carry != 0)
        return res
    }

    private fun sra(valIn: Int): Int {
        val carry = valIn and 1
        val msb = valIn and 0x80
        val res = (valIn ushr 1) or msb
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (carry != 0)
        return res
    }

    private fun swap(valIn: Int): Int {
        val res = ((valIn and 0x0F) shl 4) or ((valIn and 0xF0) ushr 4)
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = false
        return res
    }

    private fun srl(valIn: Int): Int {
        val carry = valIn and 1
        val res = (valIn ushr 1) and 0xFF
        flagZ = (res == 0)
        flagN = false
        flagH = false
        flagC = (carry != 0)
        return res
    }

    private fun daa() {
        var correction = 0
        var setCarry = false

        if (flagH || (!flagN && (a and 0x0F) > 0x09)) {
            correction = correction or 0x06
        }

        if (flagC || (!flagN && a > 0x99)) {
            correction = correction or 0x60
            setCarry = true
        }

        a = if (flagN) (a - correction) and 0xFF else (a + correction) and 0xFF

        flagZ = (a == 0)
        flagH = false
        if (setCarry) {
            flagC = true
        }
    }
}
