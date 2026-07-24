package com.example.emulator

class Timer(private val mmu: Mmu) {

    var divCounter: Int = 0
    var timaCounter: Int = 0

    var div: Int = 0
    var tima: Int = 0
    var tma: Int = 0
    var tac: Int = 0

    fun step(cycles: Int) {
        // DIV register always increments at 16384Hz (every 256 T-cycles)
        divCounter += cycles
        while (divCounter >= 256) {
            divCounter -= 256
            div = (div + 1) and 0xFF
        }

        // TIMA register handling
        val timerEnabled = (tac and 0x04) != 0
        if (timerEnabled) {
            timaCounter += cycles
            val threshold = getClockThreshold()
            while (timaCounter >= threshold) {
                timaCounter -= threshold
                tima++
                if (tima > 0xFF) {
                    tima = tma
                    mmu.ifReg = mmu.ifReg or 0x04 // Request Timer Interrupt
                }
            }
        }
    }

    private fun getClockThreshold(): Int {
        return when (tac and 0x03) {
            0 -> 1024 // 4096 Hz
            1 -> 16   // 262144 Hz
            2 -> 64   // 65536 Hz
            else -> 256 // 16384 Hz
        }
    }

    fun resetDiv() {
        div = 0
        divCounter = 0
    }
}
