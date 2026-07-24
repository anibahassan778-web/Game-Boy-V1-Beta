package com.example.emulator

class Ppu(private val mmu: Mmu) {

    val frameBuffer = IntArray(160 * 144)
    var isFrameReady: Boolean = false

    var ly: Int = 0
    var lyc: Int = 0
    var lcdc: Int = 0x91
    var stat: Int = 0x80
    var scy: Int = 0
    var scx: Int = 0
    var wy: Int = 0
    var wx: Int = 0

    // DMG Palettes
    var bgp: Int = 0xFC
    var obp0: Int = 0xFF
    var obp1: Int = 0xFF

    // CGB Palettes (64 bytes each for BG and OBJ: 8 palettes x 4 colors x 2 bytes)
    val cgbBgPalettes = ByteArray(64)
    val cgbObjPalettes = ByteArray(64)
    var bcps: Int = 0
    var ocps: Int = 0

    var modeClock: Int = 0
    var currentMode: Int = 2 // Start in Mode 2

    // Color palette profiles (0 = DMG Classic Green, 1 = DMG Pocket B&W, 2 = Vibrant)
    var colorPaletteMode: Int = 0

    private val classicGreenColors = intArrayOf(
        0xFF9BBC0F.toInt(), // Lightest
        0xFF8BAC0F.toInt(), // Light
        0xFF306230.toInt(), // Dark
        0xFF0F380F.toInt()  // Darkest
    )

    private val pocketColors = intArrayOf(
        0xFFE0E8D0.toInt(),
        0xFFA0B088.toInt(),
        0xFF486048.toInt(),
        0xFF081810.toInt()
    )

    private fun getPaletteColors(): IntArray {
        return when (colorPaletteMode) {
            1 -> pocketColors
            else -> classicGreenColors
        }
    }

    fun step(cycles: Int) {
        if ((lcdc and 0x80) == 0) {
            // LCD is disabled
            ly = 0
            modeClock = 0
            currentMode = 0
            stat = (stat and 0xFC)
            return
        }

        modeClock += cycles

        when (currentMode) {
            2 -> { // Mode 2: OAM Search (80 T-cycles)
                if (modeClock >= 80) {
                    modeClock -= 80
                    currentMode = 3
                    stat = (stat and 0xFC) or 3
                }
            }
            3 -> { // Mode 3: Pixel Transfer (~172 T-cycles)
                if (modeClock >= 172) {
                    modeClock -= 172
                    currentMode = 0
                    stat = (stat and 0xFC)
                    renderScanline()

                    // Check STAT HBlank Interrupt
                    if ((stat and 0x08) != 0) {
                        mmu.ifReg = mmu.ifReg or 0x02
                    }

                    // CGB HBlank HDMA copy
                    if (mmu.isCgb) {
                        mmu.checkHblankHdma()
                    }
                }
            }
            0 -> { // Mode 0: HBlank (Remaining to 456 T-cycles = 204 T-cycles)
                if (modeClock >= 204) {
                    modeClock -= 204
                    ly++
                    checkLycInterrupt()

                    if (ly == 144) {
                        currentMode = 1
                        stat = (stat and 0xFC) or 1
                        mmu.ifReg = mmu.ifReg or 0x01 // Request VBlank Interrupt

                        // Check STAT VBlank Interrupt
                        if ((stat and 0x10) != 0) {
                            mmu.ifReg = mmu.ifReg or 0x02
                        }
                        isFrameReady = true
                    } else {
                        currentMode = 2
                        stat = (stat and 0xFC) or 2
                        // Check STAT OAM Interrupt
                        if ((stat and 0x20) != 0) {
                            mmu.ifReg = mmu.ifReg or 0x02
                        }
                    }
                }
            }
            1 -> { // Mode 1: VBlank (10 scanlines = 4560 T-cycles)
                if (modeClock >= 456) {
                    modeClock -= 456
                    ly++
                    if (ly > 153) {
                        ly = 0
                        currentMode = 2
                        stat = (stat and 0xFC) or 2
                        checkLycInterrupt()
                        if ((stat and 0x20) != 0) {
                            mmu.ifReg = mmu.ifReg or 0x02
                        }
                    } else {
                        checkLycInterrupt()
                    }
                }
            }
        }
    }

    fun checkLycInterrupt() {
        if (ly == lyc) {
            stat = stat or 0x04
            if ((stat and 0x40) != 0) {
                mmu.ifReg = mmu.ifReg or 0x02 // Request STAT interrupt
            }
        } else {
            stat = stat and 0x04.inv()
        }
    }

    fun turnOffLcd() {
        ly = 0
        modeClock = 0
        currentMode = 0
        stat = (stat and 0xFC)
        checkLycInterrupt()
    }

    fun resetClock() {
        modeClock = 0
        currentMode = 2 // Start in Mode 2
        stat = (stat and 0xFC) or 2
        checkLycInterrupt()
        if ((stat and 0x20) != 0) {
            mmu.ifReg = mmu.ifReg or 0x02
        }
    }

    private fun renderScanline() {
        if (ly >= 144) return

        val scanlineOffset = ly * 160

        // Render Background & Window
        val bgWindowPriority = BooleanArray(160)
        if ((lcdc and 0x01) != 0 || mmu.isCgb) { // In CGB bit 0 is BG enable / master priority
            renderBgAndWindow(scanlineOffset, bgWindowPriority)
        } else {
            val palette = getPaletteColors()
            for (x in 0 until 160) {
                frameBuffer[scanlineOffset + x] = palette[0]
            }
        }

        // Render Sprites (OBJ)
        if ((lcdc and 0x02) != 0) {
            renderSprites(scanlineOffset, bgWindowPriority)
        }
    }

    private fun renderBgAndWindow(scanlineOffset: Int, priorityMap: BooleanArray) {
        val palette = getPaletteColors()
        val isWindowVisible = (lcdc and 0x20) != 0 && ly >= wy && wx <= 166

        val bgMapSelect = (lcdc and 0x08) != 0
        val windowMapSelect = (lcdc and 0x40) != 0
        val tileDataUnsigned = (lcdc and 0x10) != 0

        val bgTileMapAddr = if (bgMapSelect) 0x9C00 else 0x9800
        val winTileMapAddr = if (windowMapSelect) 0x9C00 else 0x9800

        for (x in 0 until 160) {
            val isWinPixel = isWindowVisible && x >= (wx - 7)

            val mapX = if (isWinPixel) x - (wx - 7) else (x + scx) and 0xFF
            val mapY = if (isWinPixel) ly - wy else (ly + scy) and 0xFF

            val tileCol = mapX / 8
            val tileRow = mapY / 8

            val mapBase = if (isWinPixel) winTileMapAddr else bgTileMapAddr
            val tileMapOffset = mapBase + (tileRow * 32) + tileCol

            val tileIndex = mmu.readVram(tileMapOffset, 0)

            // CGB attributes from VRAM bank 1
            val attributes = if (mmu.isCgb) mmu.readVram(tileMapOffset, 1) else 0
            val vramBank = if (mmu.isCgb) (attributes ushr 3) and 1 else 0
            val hFlip = (attributes and 0x20) != 0
            val vFlip = (attributes and 0x40) != 0
            val bgPriority = (attributes and 0x80) != 0
            val cgbPaletteIndex = attributes and 0x07

            val tileDataAddr = if (tileDataUnsigned) {
                0x8000 + (tileIndex * 16)
            } else {
                val signedIndex = tileIndex.toByte().toInt()
                0x9000 + (signedIndex * 16)
            }

            val lineInTile = if (vFlip) 7 - (mapY % 8) else (mapY % 8)
            val byte1 = mmu.readVram(tileDataAddr + (lineInTile * 2), vramBank)
            val byte2 = mmu.readVram(tileDataAddr + (lineInTile * 2) + 1, vramBank)

            val bitInTile = if (hFlip) (mapX % 8) else 7 - (mapX % 8)
            val colorNum = (((byte2 ushr bitInTile) and 1) shl 1) or ((byte1 ushr bitInTile) and 1)

            priorityMap[x] = bgPriority && (colorNum != 0)

            val colorARGB = if (mmu.isCgb) {
                getCgbColor(cgbBgPalettes, cgbPaletteIndex, colorNum)
            } else {
                val colorIndex = (bgp ushr (colorNum * 2)) and 0x03
                palette[colorIndex]
            }

            frameBuffer[scanlineOffset + x] = colorARGB
        }
    }

    private class SpriteInfo(val x: Int, val y: Int, val tileIndex: Int, val attributes: Int, val oamIndex: Int)

    private fun renderSprites(scanlineOffset: Int, bgWindowPriority: BooleanArray) {
        val palette = getPaletteColors()
        val spriteHeight = if ((lcdc and 0x04) != 0) 16 else 8

        // OAM Scan: limit to max 10 sprites per line
        val sprites = mutableListOf<SpriteInfo>()
        for (i in 0 until 40) {
            val oamAddr = 0xFE00 + (i * 4)
            val spriteY = mmu.readByte(oamAddr) - 16
            val spriteX = mmu.readByte(oamAddr + 1) - 8
            val tileIndex = mmu.readByte(oamAddr + 2)
            val attributes = mmu.readByte(oamAddr + 3)

            if (ly in spriteY until (spriteY + spriteHeight)) {
                sprites.add(SpriteInfo(spriteX, spriteY, tileIndex, attributes, i))
                if (sprites.size == 10) break
            }
        }

        // Sort sprites by priority
        if (mmu.isCgb) {
            // CGB prioritizes strictly by OAM index
            sprites.sortBy { it.oamIndex }
        } else {
            // DMG prioritizes by X coordinate, then OAM index
            sprites.sortWith(Comparator { s1, s2 ->
                if (s1.x != s2.x) s1.x.compareTo(s2.x) else s1.oamIndex.compareTo(s2.oamIndex)
            })
        }

        for (sprite in sprites.reversed()) { // Render background-most first so foreground draws over
            val lineInSprite = if ((sprite.attributes and 0x40) != 0) {
                (spriteHeight - 1) - (ly - sprite.y)
            } else {
                ly - sprite.y
            }

            val tileIndex = if (spriteHeight == 16) {
                if (lineInSprite < 8) sprite.tileIndex and 0xFE else sprite.tileIndex or 0x01
            } else {
                sprite.tileIndex
            }

            val tileLine = lineInSprite % 8
            val vramBank = if (mmu.isCgb) (sprite.attributes ushr 3) and 1 else 0
            val tileDataAddr = 0x8000 + (tileIndex * 16) + (tileLine * 2)

            val byte1 = mmu.readVram(tileDataAddr, vramBank)
            val byte2 = mmu.readVram(tileDataAddr + 1, vramBank)

            val hFlip = (sprite.attributes and 0x20) != 0
            val bgOverObj = (sprite.attributes and 0x80) != 0
            val useObp1 = (sprite.attributes and 0x10) != 0
            val cgbPaletteIndex = sprite.attributes and 0x07

            for (px in 0 until 8) {
                val screenX = sprite.x + px
                if (screenX !in 0 until 160) continue

                val bitInTile = if (hFlip) px else 7 - px
                val colorNum = (((byte2 ushr bitInTile) and 1) shl 1) or ((byte1 ushr bitInTile) and 1)

                if (colorNum == 0) continue // Transparent color

                if (bgOverObj && bgWindowPriority[screenX]) continue // Covered by BG priority

                val colorARGB = if (mmu.isCgb) {
                    getCgbColor(cgbObjPalettes, cgbPaletteIndex, colorNum)
                } else {
                    val obp = if (useObp1) obp1 else obp0
                    val colorIndex = (obp ushr (colorNum * 2)) and 0x03
                    palette[colorIndex]
                }

                frameBuffer[scanlineOffset + screenX] = colorARGB
            }
        }
    }

    private fun getCgbColor(paletteData: ByteArray, paletteIndex: Int, colorNum: Int): Int {
        val offset = (paletteIndex * 8) + (colorNum * 2)
        val low = paletteData[offset].toInt() and 0xFF
        val high = paletteData[offset + 1].toInt() and 0xFF
        val rgb555 = (high shl 8) or low

        val r5 = rgb555 and 0x1F
        val g5 = (rgb555 ushr 5) and 0x1F
        val b5 = (rgb555 ushr 10) and 0x1F

        // Expand 5-bit channels to 8-bit
        val r8 = (r5 shl 3) or (r5 ushr 2)
        val g8 = (g5 shl 3) or (g5 ushr 2)
        val b8 = (b5 shl 3) or (b5 ushr 2)

        return (0xFF shl 24) or (r8 shl 16) or (g8 shl 8) or b8
    }

    fun readCgbBgPalette(): Int {
        val index = bcps and 0x3F
        return cgbBgPalettes[index].toInt() and 0xFF
    }

    fun writeCgbBgPalette(value: Int) {
        val index = bcps and 0x3F
        cgbBgPalettes[index] = value.toByte()
        if ((bcps and 0x80) != 0) { // Auto-increment
            bcps = (bcps and 0x80) or ((index + 1) and 0x3F)
        }
    }

    fun readCgbObjPalette(): Int {
        val index = ocps and 0x3F
        return cgbObjPalettes[index].toInt() and 0xFF
    }

    fun writeCgbObjPalette(value: Int) {
        val index = ocps and 0x3F
        cgbObjPalettes[index] = value.toByte()
        if ((ocps and 0x80) != 0) { // Auto-increment
            ocps = (ocps and 0x80) or ((index + 1) and 0x3F)
        }
    }
}
