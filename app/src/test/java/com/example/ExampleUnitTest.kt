package com.example

import com.example.emulator.GameBoy
import com.example.ui.GbButton
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying Game Boy joypad input system and button mapping.
 */
class ExampleUnitTest {

    @Test
    fun joypad_directions_mapping_and_interrupt() {
        val gb = GameBoy()

        // Initially no buttons pressed: joypad register bits are all 1 (active low, 0x0F)
        assertEquals(0x0F, gb.mmu.joypadDirections)
        assertEquals(0x0F, gb.mmu.joypadActions)

        // Press RIGHT (bit 0 = 0)
        gb.updateJoypad(directions = 0x0E, actions = 0x0F)
        assertEquals(0x0E, gb.mmu.joypadDirections)
        // Verify Joypad interrupt request (bit 4 of IF register)
        assertTrue((gb.mmu.ifReg and 0x10) != 0)

        // Press UP (bit 2 = 0) + RIGHT (bit 0 = 0) diagonally
        val upRight = 0x0F and 0x01.inv() and 0x04.inv() // 0x0A
        gb.updateJoypad(directions = upRight, actions = 0x0F)
        assertEquals(0x0A, gb.mmu.joypadDirections)

        // Release directions
        gb.updateJoypad(directions = 0x0F, actions = 0x0F)
        assertEquals(0x0F, gb.mmu.joypadDirections)
    }

    @Test
    fun joypad_actions_mapping() {
        val gb = GameBoy()

        // Press Button A (bit 0 = 0)
        gb.updateJoypad(directions = 0x0F, actions = 0x0E)
        assertEquals(0x0E, gb.mmu.joypadActions)

        // Press Button B (bit 1 = 0)
        gb.updateJoypad(directions = 0x0F, actions = 0x0D)
        assertEquals(0x0D, gb.mmu.joypadActions)

        // Press A + B together (bits 0 and 1 = 0)
        gb.updateJoypad(directions = 0x0F, actions = 0x0C)
        assertEquals(0x0C, gb.mmu.joypadActions)

        // Press START (bit 3 = 0) + SELECT (bit 2 = 0)
        gb.updateJoypad(directions = 0x0F, actions = 0x03)
        assertEquals(0x03, gb.mmu.joypadActions)
    }
}
