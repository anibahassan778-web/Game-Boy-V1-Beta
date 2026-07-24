package com.example.emulator

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

data class RomMetadata(
    val title: String = "No ROM Loaded",
    val manufacturer: String = "Unknown",
    val manufacturerCode: String = "",
    val cartridgeType: String = "ROM ONLY",
    val cartTypeCode: Int = 0,
    val romSize: String = "32 KB",
    val ramSize: String = "None",
    val destination: String = "Worldwide",
    val cgbSupport: String = "DMG Only",
    val headerChecksumPassed: Boolean = true,
    val headerChecksum: String = "0x00",
    val globalChecksum: String = "0x0000",
    val licenseCode: String = "00",
    val maskRomVersion: Int = 0
)

class Cartridge(val romData: ByteArray) {

    val title: String
    val isCgbSupported: Boolean
    val cartType: Int
    val romSizeCode: Int
    val ramSizeCode: Int
    val headerChecksumPassed: Boolean
    val mbc: Mbc
    val metadata: RomMetadata

    init {
        // Extract Title (0x0134..0x0143)
        val sb = StringBuilder()
        for (i in 0x0134..0x0143) {
            if (i >= romData.size) break
            val b = romData[i].toInt() and 0xFF
            if (b == 0) break
            if (b in 32..126) {
                sb.append(b.toChar())
            }
        }
        title = sb.toString().trim().ifBlank { "UNTITLED ROM" }

        // CGB Flag (0x0143)
        val cgbByte = if (romData.size > 0x0143) romData[0x0143].toInt() and 0xFF else 0
        isCgbSupported = (cgbByte == 0x80 || cgbByte == 0xC0)
        val cgbDesc = when (cgbByte) {
            0x80 -> "CGB & DMG (Dual Compatible)"
            0xC0 -> "CGB Only"
            else -> "DMG Only"
        }

        // Manufacturer Code (0x013F..0x0142)
        val mfgSb = StringBuilder()
        for (i in 0x013F..0x0142) {
            if (i < romData.size) {
                val b = romData[i].toInt() and 0xFF
                if (b in 32..126) mfgSb.append(b.toChar())
            }
        }
        val manufacturerCodeStr = mfgSb.toString().trim()

        // Cartridge Type (0x0147)
        cartType = if (romData.size > 0x0147) romData[0x0147].toInt() and 0xFF else 0
        romSizeCode = if (romData.size > 0x0148) romData[0x0148].toInt() and 0xFF else 0
        ramSizeCode = if (romData.size > 0x0149) romData[0x0149].toInt() and 0xFF else 0

        val cartTypeDesc = parseCartridgeType(cartType)
        val romSizeDesc = parseRomSize(romSizeCode, romData.size)
        val ramSizeDesc = parseRamSize(ramSizeCode)

        // Destination Code (0x014A)
        val destByte = if (romData.size > 0x014A) romData[0x014A].toInt() and 0xFF else 1
        val destDesc = if (destByte == 0) "Japanese" else "Non-Japanese / Overseas"

        // Licensee Codes (0x014B Old, 0x0144..0x0145 New)
        val oldLicensee = if (romData.size > 0x014B) romData[0x014B].toInt() and 0xFF else 0
        val newLicenseeStr = if (romData.size > 0x0145) {
            val c1 = romData[0x0144].toInt().toChar()
            val c2 = romData[0x0145].toInt().toChar()
            "$c1$c2"
        } else "00"

        val (licenseCodeStr, manufacturerName) = parseManufacturer(oldLicensee, newLicenseeStr, manufacturerCodeStr)

        // Mask ROM Version (0x014C)
        val versionByte = if (romData.size > 0x014C) romData[0x014C].toInt() and 0xFF else 0

        // Header Checksum (0x014D)
        var checksum = 0
        for (i in 0x0134..0x014C) {
            if (i < romData.size) {
                checksum = (checksum - (romData[i].toInt() and 0xFF) - 1) and 0xFF
            }
        }
        val expectedChecksum = if (romData.size > 0x014D) romData[0x014D].toInt() and 0xFF else -1
        headerChecksumPassed = (checksum == expectedChecksum)

        val headerChecksumHex = String.format("0x%02X", expectedChecksum)

        // Global Checksum (0x014E..0x014F)
        val globalChecksumVal = if (romData.size > 0x014F) {
            ((romData[0x014E].toInt() and 0xFF) shl 8) or (romData[0x014F].toInt() and 0xFF)
        } else 0
        val globalChecksumHex = String.format("0x%04X", globalChecksumVal)

        // Instantiate appropriate MBC
        mbc = createMbc(cartType, romData, ramSizeCode)

        metadata = RomMetadata(
            title = title,
            manufacturer = manufacturerName,
            manufacturerCode = manufacturerCodeStr,
            cartridgeType = cartTypeDesc,
            cartTypeCode = cartType,
            romSize = romSizeDesc,
            ramSize = ramSizeDesc,
            destination = destDesc,
            cgbSupport = cgbDesc,
            headerChecksumPassed = headerChecksumPassed,
            headerChecksum = headerChecksumHex,
            globalChecksum = globalChecksumHex,
            licenseCode = licenseCodeStr,
            maskRomVersion = versionByte
        )
    }

    private fun createMbc(type: Int, rom: ByteArray, ramCode: Int): Mbc {
        val ramSize = when (ramCode) {
            1 -> 2048
            2 -> 8192
            3 -> 32768
            4 -> 131072
            5 -> 65536
            else -> 0
        }

        return when (type) {
            0x00 -> RomOnlyMbc(rom, ramSize)
            0x01, 0x02, 0x03 -> Mbc1(rom, ramSize)
            0x05, 0x06 -> Mbc2(rom)
            0x0F, 0x10, 0x11, 0x12, 0x13 -> Mbc3(rom, ramSize)
            0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E -> Mbc5(rom, ramSize)
            else -> Mbc1(rom, ramSize) // Fallback to MBC1
        }
    }

    fun readRom(address: Int): Int = mbc.readRom(address)
    fun writeRom(address: Int, value: Int) = mbc.writeRom(address, value)
    fun readRam(address: Int): Int = mbc.readRam(address)
    fun writeRam(address: Int, value: Int) = mbc.writeRam(address, value)

    fun getSramData(): ByteArray = mbc.getSramData()
    fun loadSramData(data: ByteArray) = mbc.loadSramData(data)

    private fun parseCartridgeType(type: Int): String {
        return when (type) {
            0x00 -> "ROM ONLY"
            0x01 -> "MBC1"
            0x02 -> "MBC1 + RAM"
            0x03 -> "MBC1 + RAM + BATTERY"
            0x05 -> "MBC2"
            0x06 -> "MBC2 + BATTERY"
            0x08 -> "ROM + RAM"
            0x09 -> "ROM + RAM + BATTERY"
            0x0B -> "MMM01"
            0x0C -> "MMM01 + RAM"
            0x0D -> "MMM01 + RAM + BATTERY"
            0x0F -> "MBC3 + TIMER + BATTERY"
            0x10 -> "MBC3 + TIMER + RAM + BATTERY"
            0x11 -> "MBC3"
            0x12 -> "MBC3 + RAM"
            0x13 -> "MBC3 + RAM + BATTERY"
            0x19 -> "MBC5"
            0x1A -> "MBC5 + RAM"
            0x1B -> "MBC5 + RAM + BATTERY"
            0x1C -> "MBC5 + RUMBLE"
            0x1D -> "MBC5 + RUMBLE + RAM"
            0x1E -> "MBC5 + RUMBLE + RAM + BATTERY"
            0x20 -> "MBC6"
            0x22 -> "MBC7 + SENSOR + RUMBLE + RAM + BATTERY"
            0xFC -> "POCKET CAMERA"
            0xFD -> "BANDAI TAMA5"
            0xFE -> "HuC3"
            0xFF -> "HuC1 + RAM + BATTERY"
            else -> String.format("Unknown Mapper (0x%02X)", type)
        }
    }

    private fun parseRomSize(code: Int, actualBytes: Int): String {
        return when (code) {
            0 -> "32 KB (2 banks)"
            1 -> "64 KB (4 banks)"
            2 -> "128 KB (8 banks)"
            3 -> "256 KB (16 banks)"
            4 -> "512 KB (32 banks)"
            5 -> "1 MB (64 banks)"
            6 -> "2 MB (128 banks)"
            7 -> "4 MB (256 banks)"
            8 -> "8 MB (512 banks)"
            0x52 -> "1.1 MB (72 banks)"
            0x53 -> "1.2 MB (80 banks)"
            0x54 -> "1.5 MB (96 banks)"
            else -> "${actualBytes / 1024} KB"
        }
    }

    private fun parseRamSize(code: Int): String {
        return when (code) {
            0 -> "No RAM"
            1 -> "2 KB"
            2 -> "8 KB (1 bank)"
            3 -> "32 KB (4 banks)"
            4 -> "128 KB (16 banks)"
            5 -> "64 KB (8 banks)"
            else -> "Unknown ($code)"
        }
    }

    private fun parseManufacturer(oldCode: Int, newCodeStr: String, mfgCodeStr: String): Pair<String, String> {
        val codeStr = if (oldCode == 0x33) newCodeStr else String.format("%02X", oldCode)
        val name = when (codeStr.uppercase()) {
            "01", "31" -> "Nintendo"
            "08", "38" -> "Capcom"
            "13", "69", "EA" -> "Electronic Arts"
            "18" -> "Hudson Soft"
            "19" -> "B-AI"
            "20" -> "KSS"
            "22" -> "POW"
            "24" -> "PCM Complete"
            "25" -> "San-X"
            "28" -> "Kemco"
            "29" -> "Seta"
            "30" -> "Viacom"
            "32" -> "Bandai"
            "33", "93" -> "Ocean / Acclaim"
            "34", "54", "A4" -> "Konami"
            "35" -> "Hector"
            "37" -> "Taito"
            "39" -> "Banpresto"
            "41" -> "Ubisoft"
            "42", "EB" -> "Atlus"
            "44" -> "Malibu Interactive"
            "46" -> "Angel"
            "47" -> "Bullet-Proof Software"
            "49" -> "Irem"
            "50" -> "Absolute"
            "51" -> "Acclaim"
            "52" -> "Activision"
            "53" -> "Sammy"
            "55" -> "Hi Tech Expressions"
            "56" -> "LJN"
            "57" -> "Matchbox"
            "58" -> "Mattel"
            "59" -> "Milton Bradley"
            "60" -> "Titus"
            "61" -> "Virgin Games"
            "64" -> "LucasArts"
            "67" -> "Ocean Software"
            "70" -> "Infogrames"
            "71" -> "Interplay"
            "72" -> "Broderbund"
            "73" -> "Sculptured Software"
            "75" -> "Sales Curve"
            "78" -> "THQ"
            "79" -> "Accolade"
            "80" -> "Misawa"
            "83", "DK" -> "LOZC / Lozc"
            "86" -> "Tokuma Shoten"
            "87" -> "Tsukuda Original"
            "91" -> "Chunsoft"
            "92" -> "Video System"
            "95" -> "Varie"
            "96" -> "Yonezawa / S'Pal"
            "97" -> "Kaneko"
            "99" -> "Pack-In-Video"
            "BL" -> "MTO"
            else -> if (mfgCodeStr.isNotBlank()) "Publisher ($mfgCodeStr)" else "Licensed Developer ($codeStr)"
        }
        return Pair(codeStr, name)
    }
}

interface Mbc {
    fun readRom(address: Int): Int
    fun writeRom(address: Int, value: Int)
    fun readRam(address: Int): Int
    fun writeRam(address: Int, value: Int)
    fun getSramData(): ByteArray
    fun loadSramData(data: ByteArray)
    fun getMbcState(): ByteArray = ByteArray(0)
    fun loadMbcState(data: ByteArray) {}
}

class RomOnlyMbc(private val rom: ByteArray, ramSize: Int) : Mbc {
    private val ram = ByteArray(ramSize)

    override fun readRom(address: Int): Int {
        return if (address < rom.size) rom[address].toInt() and 0xFF else 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        // ROM ONLY - writes are ignored
    }

    override fun readRam(address: Int): Int {
        val offset = address - 0xA000
        return if (offset in ram.indices) ram[offset].toInt() and 0xFF else 0xFF
    }

    override fun writeRam(address: Int, value: Int) {
        val offset = address - 0xA000
        if (offset in ram.indices) {
            ram[offset] = value.toByte()
        }
    }

    override fun getSramData(): ByteArray = ram
    override fun loadSramData(data: ByteArray) {
        System.arraycopy(data, 0, ram, 0, minOf(data.size, ram.size))
    }
}

/**
 * MBC1 implementation with 0x00 bank quirk mapping to bank 1.
 */
class Mbc1(private val rom: ByteArray, ramSize: Int) : Mbc {
    private val ram = ByteArray(maxOf(ramSize, 32768))
    private var ramEnabled = false
    private var romBankLow = 1
    private var romRamBankHigh = 0
    private var mode = 0 // 0 = ROM banking mode, 1 = RAM banking mode

    override fun readRom(address: Int): Int {
        val bank = if (address < 0x4000) {
            if (mode == 1) (romRamBankHigh shl 5) else 0
        } else {
            val rawBank = (romRamBankHigh shl 5) or romBankLow
            if (rawBank and 0x1F == 0) rawBank + 1 else rawBank
        }
        val romAddr = (bank * 16384) + (address and 0x3FFF)
        val actualAddr = romAddr % rom.size
        return rom[actualAddr].toInt() and 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        when (address) {
            in 0x0000..0x1FFF -> {
                ramEnabled = (value and 0x0F) == 0x0A
            }
            in 0x2000..0x3FFF -> {
                var bank = value and 0x1F
                if (bank == 0) bank = 1
                romBankLow = bank
            }
            in 0x4000..0x5FFF -> {
                romRamBankHigh = value and 0x03
            }
            in 0x6000..0x7FFF -> {
                mode = value and 0x01
            }
        }
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        val bank = if (mode == 1) romRamBankHigh else 0
        val ramAddr = (bank * 8192) + (address - 0xA000)
        val actualAddr = ramAddr % ram.size
        return ram[actualAddr].toInt() and 0xFF
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        val bank = if (mode == 1) romRamBankHigh else 0
        val ramAddr = (bank * 8192) + (address - 0xA000)
        val actualAddr = ramAddr % ram.size
        ram[actualAddr] = value.toByte()
    }

    override fun getSramData(): ByteArray = ram
    override fun loadSramData(data: ByteArray) {
        System.arraycopy(data, 0, ram, 0, minOf(data.size, ram.size))
    }

    override fun getMbcState(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeBoolean(ramEnabled)
        dos.writeInt(romBankLow)
        dos.writeInt(romRamBankHigh)
        dos.writeInt(mode)
        return baos.toByteArray()
    }

    override fun loadMbcState(data: ByteArray) {
        if (data.isEmpty()) return
        val dis = DataInputStream(ByteArrayInputStream(data))
        ramEnabled = dis.readBoolean()
        romBankLow = dis.readInt()
        romRamBankHigh = dis.readInt()
        mode = dis.readInt()
    }
}

/**
 * MBC2 implementation with built-in 512 x 4-bit RAM.
 */
class Mbc2(private val rom: ByteArray) : Mbc {
    private val ram = ByteArray(512)
    private var ramEnabled = false
    private var romBank = 1

    override fun readRom(address: Int): Int {
        val bank = if (address < 0x4000) 0 else romBank
        val romAddr = (bank * 16384) + (address and 0x3FFF)
        val actualAddr = romAddr % rom.size
        return rom[actualAddr].toInt() and 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        if (address in 0x0000..0x3FFF) {
            if ((address and 0x0100) == 0) {
                ramEnabled = (value and 0x0F) == 0x0A
            } else {
                var bank = value and 0x0F
                if (bank == 0) bank = 1
                romBank = bank
            }
        }
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        val offset = (address - 0xA000) and 0x01FF
        return (ram[offset].toInt() and 0x0F) or 0xF0
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        val offset = (address - 0xA000) and 0x01FF
        ram[offset] = (value and 0x0F).toByte()
    }

    override fun getSramData(): ByteArray = ram
    override fun loadSramData(data: ByteArray) {
        System.arraycopy(data, 0, ram, 0, minOf(data.size, ram.size))
    }

    override fun getMbcState(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeBoolean(ramEnabled)
        dos.writeInt(romBank)
        return baos.toByteArray()
    }

    override fun loadMbcState(data: ByteArray) {
        if (data.isEmpty()) return
        val dis = DataInputStream(ByteArrayInputStream(data))
        ramEnabled = dis.readBoolean()
        romBank = dis.readInt()
    }
}

/**
 * MBC3 implementation with ROM/RAM switching and full Real-Time Clock (RTC).
 */
class Mbc3(private val rom: ByteArray, ramSize: Int) : Mbc {
    private val ram = ByteArray(maxOf(ramSize, 32768))
    private var ramEnabled = false
    private var romBank = 1
    private var ramBankOrRtcSelect = 0

    // RTC registers: seconds, minutes, hours, daysLow, daysHigh
    private var rtcSeconds = 0
    private var rtcMinutes = 0
    private var rtcHours = 0
    private var rtcDaysLow = 0
    private var rtcDaysHigh = 0
    private var rtcLatchState = 0
    private var latchedSeconds = 0
    private var latchedMinutes = 0
    private var latchedHours = 0
    private var latchedDaysLow = 0
    private var latchedDaysHigh = 0

    override fun readRom(address: Int): Int {
        val bank = if (address < 0x4000) 0 else romBank
        val romAddr = (bank * 16384) + (address and 0x3FFF)
        val actualAddr = romAddr % rom.size
        return rom[actualAddr].toInt() and 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        when (address) {
            in 0x0000..0x1FFF -> {
                ramEnabled = (value and 0x0F) == 0x0A
            }
            in 0x2000..0x3FFF -> {
                var bank = value and 0x7F
                if (bank == 0) bank = 1
                romBank = bank
            }
            in 0x4000..0x5FFF -> {
                ramBankOrRtcSelect = value
            }
            in 0x6000..0x7FFF -> {
                if (rtcLatchState == 0 && value == 0) {
                    rtcLatchState = 1
                } else if (rtcLatchState == 1 && value == 1) {
                    rtcLatchState = 0
                    latchRtc()
                } else {
                    rtcLatchState = 0
                }
            }
        }
    }

    private fun latchRtc() {
        val now = System.currentTimeMillis() / 1000
        latchedSeconds = (now % 60).toInt()
        latchedMinutes = ((now / 60) % 60).toInt()
        latchedHours = ((now / 3600) % 24).toInt()
        val days = (now / 86400).toInt()
        latchedDaysLow = days and 0xFF
        latchedDaysHigh = (days ushr 8) and 0x01
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        return when (ramBankOrRtcSelect) {
            in 0x00..0x03 -> {
                val ramAddr = (ramBankOrRtcSelect * 8192) + (address - 0xA000)
                val actualAddr = ramAddr % ram.size
                ram[actualAddr].toInt() and 0xFF
            }
            0x08 -> latchedSeconds
            0x09 -> latchedMinutes
            0x0A -> latchedHours
            0x0B -> latchedDaysLow
            0x0C -> latchedDaysHigh
            else -> 0xFF
        }
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        when (ramBankOrRtcSelect) {
            in 0x00..0x03 -> {
                val ramAddr = (ramBankOrRtcSelect * 8192) + (address - 0xA000)
                val actualAddr = ramAddr % ram.size
                ram[actualAddr] = value.toByte()
            }
            0x08 -> rtcSeconds = value
            0x09 -> rtcMinutes = value
            0x0A -> rtcHours = value
            0x0B -> rtcDaysLow = value
            0x0C -> rtcDaysHigh = value
        }
    }

    override fun getSramData(): ByteArray = ram
    override fun loadSramData(data: ByteArray) {
        System.arraycopy(data, 0, ram, 0, minOf(data.size, ram.size))
    }

    override fun getMbcState(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeBoolean(ramEnabled)
        dos.writeInt(romBank)
        dos.writeInt(ramBankOrRtcSelect)
        dos.writeInt(rtcSeconds)
        dos.writeInt(rtcMinutes)
        dos.writeInt(rtcHours)
        dos.writeInt(rtcDaysLow)
        dos.writeInt(rtcDaysHigh)
        return baos.toByteArray()
    }

    override fun loadMbcState(data: ByteArray) {
        if (data.isEmpty()) return
        val dis = DataInputStream(ByteArrayInputStream(data))
        ramEnabled = dis.readBoolean()
        romBank = dis.readInt()
        ramBankOrRtcSelect = dis.readInt()
        rtcSeconds = dis.readInt()
        rtcMinutes = dis.readInt()
        rtcHours = dis.readInt()
        rtcDaysLow = dis.readInt()
        rtcDaysHigh = dis.readInt()
    }
}

/**
 * MBC5 implementation supporting up to 8MB ROM and 128KB RAM.
 */
class Mbc5(private val rom: ByteArray, ramSize: Int) : Mbc {
    private val ram = ByteArray(maxOf(ramSize, 131072))
    private var ramEnabled = false
    private var romBankLow = 1
    private var romBankHigh = 0
    private var ramBank = 0

    override fun readRom(address: Int): Int {
        val bank = if (address < 0x4000) {
            0
        } else {
            (romBankHigh shl 8) or romBankLow
        }
        val romAddr = (bank * 16384) + (address and 0x3FFF)
        val actualAddr = romAddr % rom.size
        return rom[actualAddr].toInt() and 0xFF
    }

    override fun writeRom(address: Int, value: Int) {
        when (address) {
            in 0x0000..0x1FFF -> {
                ramEnabled = (value and 0x0F) == 0x0A
            }
            in 0x2000..0x2FFF -> {
                romBankLow = value and 0xFF
            }
            in 0x3000..0x3FFF -> {
                romBankHigh = value and 0x01
            }
            in 0x4000..0x5FFF -> {
                ramBank = value and 0x0F
            }
        }
    }

    override fun readRam(address: Int): Int {
        if (!ramEnabled) return 0xFF
        val ramAddr = (ramBank * 8192) + (address - 0xA000)
        val actualAddr = ramAddr % ram.size
        return ram[actualAddr].toInt() and 0xFF
    }

    override fun writeRam(address: Int, value: Int) {
        if (!ramEnabled) return
        val ramAddr = (ramBank * 8192) + (address - 0xA000)
        val actualAddr = ramAddr % ram.size
        ram[actualAddr] = value.toByte()
    }

    override fun getSramData(): ByteArray = ram
    override fun loadSramData(data: ByteArray) {
        System.arraycopy(data, 0, ram, 0, minOf(data.size, ram.size))
    }

    override fun getMbcState(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeBoolean(ramEnabled)
        dos.writeInt(romBankLow)
        dos.writeInt(romBankHigh)
        dos.writeInt(ramBank)
        return baos.toByteArray()
    }

    override fun loadMbcState(data: ByteArray) {
        if (data.isEmpty()) return
        val dis = DataInputStream(ByteArrayInputStream(data))
        ramEnabled = dis.readBoolean()
        romBankLow = dis.readInt()
        romBankHigh = dis.readInt()
        ramBank = dis.readInt()
    }
}
