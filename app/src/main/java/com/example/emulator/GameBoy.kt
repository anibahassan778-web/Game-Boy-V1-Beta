package com.example.emulator

import com.squareup.moshi.JsonClass
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class GameBoy {

    val mmu = Mmu()
    val ppu = Ppu(mmu)
    val apu = Apu()
    val timer = Timer(mmu)
    val cpu = Cpu(mmu)

    var isRunning = false
    var isTurbo = false
    var currentRomName = ""

    init {
        mmu.attachComponents(ppu, apu, timer)
    }

    fun loadRom(romBytes: ByteArray, romName: String) {
        val cart = Cartridge(romBytes)
        mmu.loadCartridge(cart)
        currentRomName = romName
        reset()
    }

    fun reset() {
        cpu.reset(mmu.isCgb)
        ppu.isFrameReady = false
        mmu.serialOutputBuffer.clear()
        isRunning = true
    }

    /**
     * Executes enough CPU steps to complete one frame (~70224 T-cycles).
     */
    fun runFrame(): IntArray {
        if (!isRunning) return ppu.frameBuffer

        ppu.isFrameReady = false
        val targetCycles = if (mmu.isDoubleSpeed) 140448 else 70224
        val multiplier = if (isTurbo) 4 else 1

        var totalCyclesExecuted = 0
        while (totalCyclesExecuted < targetCycles * multiplier) {
            val cycles = cpu.step()
            val baseCycles = if (mmu.isDoubleSpeed) cycles / 2 else cycles

            timer.step(baseCycles)
            ppu.step(baseCycles)
            apu.step(baseCycles)

            totalCyclesExecuted += baseCycles
        }

        return ppu.frameBuffer
    }

    fun stepInstruction(): Int {
        val cycles = cpu.step()
        val baseCycles = if (mmu.isDoubleSpeed) cycles / 2 else cycles
        timer.step(baseCycles)
        ppu.step(baseCycles)
        apu.step(baseCycles)
        return baseCycles
    }

    fun updateJoypad(directions: Int, actions: Int) {
        mmu.joypadDirections = directions and 0x0F
        mmu.joypadActions = actions and 0x0F

        // Request Joypad interrupt if any key pressed
        if (directions != 0x0F || actions != 0x0F) {
            mmu.ifReg = mmu.ifReg or 0x10
        }
    }

    /**
     * Serializes complete CPU, MMU, PPU, Timer state into a Save State snapshot.
     */
    fun createSaveState(): SaveStateData {
        val cartSram = mmu.cartridge?.getSramData() ?: ByteArray(0)
        return SaveStateData(
            cpuA = cpu.a, cpuF = cpu.f, cpuB = cpu.b, cpuC = cpu.c,
            cpuD = cpu.d, cpuE = cpu.e, cpuH = cpu.h, cpuL = cpu.l,
            cpuSP = cpu.sp, cpuPC = cpu.pc, cpuIme = cpu.ime, cpuHalted = cpu.halted,
            ppuLcdc = ppu.lcdc, ppuStat = ppu.stat, ppuScy = ppu.scy, ppuScx = ppu.scx,
            ppuLy = ppu.ly, ppuLyc = ppu.lyc, ppuBgp = ppu.bgp, ppuObp0 = ppu.obp0, ppuObp1 = ppu.obp1,
            ppuWy = ppu.wy, ppuWx = ppu.wx,
            timerDiv = timer.div, timerTima = timer.tima, timerTma = timer.tma, timerTac = timer.tac,
            ie = mmu.ie, ifReg = mmu.ifReg,
            hram = mmu.hram.clone(),
            oam = mmu.oam.clone(),
            vram0 = mmu.vramBank0.clone(),
            vram1 = mmu.vramBank1.clone(),
            wram0 = mmu.wramBanks[0].clone(),
            wram1 = mmu.wramBanks[1].clone(),
            cartSram = cartSram
        )
    }

    fun loadSaveState(state: SaveStateData) {
        cpu.a = state.cpuA; cpu.f = state.cpuF; cpu.b = state.cpuB; cpu.c = state.cpuC
        cpu.d = state.cpuD; cpu.e = state.cpuE; cpu.h = state.cpuH; cpu.l = state.cpuL
        cpu.sp = state.cpuSP; cpu.pc = state.cpuPC; cpu.ime = state.cpuIme; cpu.halted = state.cpuHalted

        ppu.lcdc = state.ppuLcdc; ppu.stat = state.ppuStat; ppu.scy = state.ppuScy; ppu.scx = state.ppuScx
        ppu.ly = state.ppuLy; ppu.lyc = state.ppuLyc; ppu.bgp = state.ppuBgp; ppu.obp0 = state.ppuObp0; ppu.obp1 = state.ppuObp1
        ppu.wy = state.ppuWy; ppu.wx = state.ppuWx

        timer.div = state.timerDiv; timer.tima = state.timerTima; timer.tma = state.timerTma; timer.tac = state.timerTac

        mmu.ie = state.ie; mmu.ifReg = state.ifReg
        System.arraycopy(state.hram, 0, mmu.hram, 0, minOf(state.hram.size, mmu.hram.size))
        System.arraycopy(state.oam, 0, mmu.oam, 0, minOf(state.oam.size, mmu.oam.size))
        System.arraycopy(state.vram0, 0, mmu.vramBank0, 0, minOf(state.vram0.size, mmu.vramBank0.size))
        System.arraycopy(state.vram1, 0, mmu.vramBank1, 0, minOf(state.vram1.size, mmu.vramBank1.size))
        System.arraycopy(state.wram0, 0, mmu.wramBanks[0], 0, minOf(state.wram0.size, mmu.wramBanks[0].size))
        System.arraycopy(state.wram1, 0, mmu.wramBanks[1], 0, minOf(state.wram1.size, mmu.wramBanks[1].size))

        if (state.cartSram.isNotEmpty()) {
            mmu.cartridge?.loadSramData(state.cartSram)
        }
    }

    fun saveStateToBytes(): ByteArray = SaveStateManager.serialize(this)
    fun loadStateFromBytes(bytes: ByteArray): Boolean = SaveStateManager.deserialize(this, bytes)

    fun release() {
        isRunning = false
        apu.release()
    }
}

@JsonClass(generateAdapter = true)
data class SaveStateData(
    val cpuA: Int, val cpuF: Int, val cpuB: Int, val cpuC: Int,
    val cpuD: Int, val cpuE: Int, val cpuH: Int, val cpuL: Int,
    val cpuSP: Int, val cpuPC: Int, val cpuIme: Boolean, val cpuHalted: Boolean,
    val ppuLcdc: Int, val ppuStat: Int, val ppuScy: Int, val ppuScx: Int,
    val ppuLy: Int, val ppuLyc: Int, val ppuBgp: Int, val ppuObp0: Int, val ppuObp1: Int,
    val ppuWy: Int, val ppuWx: Int,
    val timerDiv: Int, val timerTima: Int, val timerTma: Int, val timerTac: Int,
    val ie: Int, val ifReg: Int,
    val hram: ByteArray,
    val oam: ByteArray,
    val vram0: ByteArray,
    val vram1: ByteArray,
    val wram0: ByteArray,
    val wram1: ByteArray,
    val cartSram: ByteArray
) : Serializable
