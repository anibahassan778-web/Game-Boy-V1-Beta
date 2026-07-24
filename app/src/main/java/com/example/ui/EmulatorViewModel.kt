package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CartridgeSramEntity
import com.example.data.SaveStateEntity
import com.example.data.SaveStateRepository
import com.example.emulator.GameBoy
import com.example.emulator.RomMetadata
import com.example.emulator.SaveStateData
import com.example.emulator.TestRoms
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    val gameBoy = GameBoy()
    private val repository = SaveStateRepository(AppDatabase.getDatabase(application).saveStateDao())
    private val moshi = Moshi.Builder().build()
    private val saveStateAdapter = moshi.adapter(SaveStateData::class.java)

    private var frameLoopJob: Job? = null

    // Bitmap rendering target (160 x 144)
    private val screenBitmap = Bitmap.createBitmap(160, 144, Bitmap.Config.ARGB_8888)

    private val _screenImage = MutableStateFlow<ImageBitmap?>(null)
    val screenImage: StateFlow<ImageBitmap?> = _screenImage.asStateFlow()

    private val _romTitle = MutableStateFlow("No ROM Loaded")
    val romTitle: StateFlow<String> = _romTitle.asStateFlow()

    private val _romMetadata = MutableStateFlow(RomMetadata())
    val romMetadata: StateFlow<RomMetadata> = _romMetadata.asStateFlow()

    private val _isCgb = MutableStateFlow(false)
    val isCgb: StateFlow<Boolean> = _isCgb.asStateFlow()

    private val _headerChecksumPassed = MutableStateFlow(true)
    val headerChecksumPassed: StateFlow<Boolean> = _headerChecksumPassed.asStateFlow()

    private val _serialLogs = MutableStateFlow("")
    val serialLogs: StateFlow<String> = _serialLogs.asStateFlow()

    private val _isTurbo = MutableStateFlow(false)
    val isTurbo: StateFlow<Boolean> = _isTurbo.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _debugTick = MutableStateFlow(0L)
    val debugTick: StateFlow<Long> = _debugTick.asStateFlow()

    // Blargg Test Suite Results
    private val _testResults = MutableStateFlow<List<TestRoms.TestResult>>(emptyList())
    val testResults: StateFlow<List<TestRoms.TestResult>> = _testResults.asStateFlow()

    // Shell & Display Preferences
    val shellTheme = MutableStateFlow("Classic DMG")
    val colorPaletteMode = MutableStateFlow(0) // 0 = DMG Green, 1 = Pocket B&W
    val enableCrtScanlines = MutableStateFlow(false)
    val isAudioMuted = MutableStateFlow(false)

    // Joypad state bitmasks (1 = unpressed, 0 = pressed)
    private var joypadDir = 0x0F
    private var joypadAct = 0x0F

    init {
        // Default to Blargg CPU Test 1
        loadBuiltInTestRom(1)
    }

    fun loadRomFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val rawBytes = inputStream?.readBytes()
                inputStream?.close()

                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    var romBytes: ByteArray = rawBytes
                    var romName = uri.lastPathSegment ?: "Loaded ROM"

                    // Handle zipped ROM files
                    if (romName.endsWith(".zip", ignoreCase = true) ||
                        (rawBytes.size > 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte())
                    ) {
                        try {
                            val zipIn = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(rawBytes))
                            var entry = zipIn.nextEntry
                            while (entry != null) {
                                if (!entry.isDirectory &&
                                    (entry.name.endsWith(".gb", ignoreCase = true) ||
                                     entry.name.endsWith(".gbc", ignoreCase = true) ||
                                     entry.name.endsWith(".bin", ignoreCase = true))
                                ) {
                                    romBytes = zipIn.readBytes()
                                    romName = entry.name
                                    break
                                }
                                entry = zipIn.nextEntry
                            }
                            zipIn.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        gameBoy.loadRom(romBytes, romName)
                        val cart = gameBoy.mmu.cartridge
                        _romTitle.value = cart?.title?.ifBlank { romName } ?: romName
                        _romMetadata.value = cart?.metadata ?: RomMetadata(title = romName)
                        _isCgb.value = gameBoy.mmu.isCgb
                        _headerChecksumPassed.value = cart?.headerChecksumPassed ?: true
                        startFrameLoop()
                    }

                    // Load saved SRAM if available
                    val savedSram = repository.getSram(romName)
                    if (savedSram != null) {
                        gameBoy.mmu.cartridge?.loadSramData(savedSram.sramBytes)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadBuiltInTestRom(testIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val romBytes = TestRoms.createCpuTestRom(testIndex)
            val testName = "Blargg Test #$testIndex"
            withContext(Dispatchers.Main) {
                gameBoy.loadRom(romBytes, testName)
                val cart = gameBoy.mmu.cartridge
                _romTitle.value = testName
                _romMetadata.value = cart?.metadata ?: RomMetadata(title = testName)
                _isCgb.value = false
                _headerChecksumPassed.value = true
                startFrameLoop()
            }
        }
    }

    fun loadAcid2Test() {
        viewModelScope.launch(Dispatchers.IO) {
            val romBytes = TestRoms.createAcid2TestRom()
            withContext(Dispatchers.Main) {
                gameBoy.loadRom(romBytes, "dmg-acid2")
                val cart = gameBoy.mmu.cartridge
                _romTitle.value = "dmg-acid2 PPU Test"
                _romMetadata.value = cart?.metadata ?: RomMetadata(title = "dmg-acid2 PPU Test")
                _isCgb.value = false
                _headerChecksumPassed.value = true
                startFrameLoop()
            }
        }
    }

    fun runAllBlarggTests() {
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<TestRoms.TestResult>()
            for (i in 1..11) {
                val romBytes = TestRoms.createCpuTestRom(i)
                val testName = TestRoms.blarggCpuTests.getOrElse(i - 1) { "Test #$i" }

                withContext(Dispatchers.Main) {
                    gameBoy.loadRom(romBytes, testName)
                }

                // Run 60 frames (~1 sec) to give time for test completion
                for (f in 0 until 60) {
                    gameBoy.runFrame()
                }

                val logs = gameBoy.mmu.serialOutputBuffer.toString()
                val passed = logs.contains("Passed") || logs.contains("Passed\n")
                results.add(
                    TestRoms.TestResult(
                        testName = testName,
                        status = if (passed) TestRoms.TestStatus.PASSED else TestRoms.TestStatus.FAILED,
                        details = if (passed) "Passed serial verification!" else "Output: $logs"
                    )
                )
            }
            _testResults.value = results
        }
    }

    private fun startFrameLoop() {
        frameLoopJob?.cancel()
        frameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            val targetFrameTimeMs = 16L
            while (gameBoy.isRunning) {
                if (_isPaused.value) {
                    delay(50)
                    continue
                }

                val startTime = System.currentTimeMillis()

                gameBoy.ppu.colorPaletteMode = colorPaletteMode.value
                gameBoy.apu.isMuted = isAudioMuted.value
                gameBoy.isTurbo = _isTurbo.value

                // Update joypad
                gameBoy.updateJoypad(joypadDir, joypadAct)

                // Run 1 frame
                val pixelData = gameBoy.runFrame()

                // Copy pixel array to Bitmap
                screenBitmap.setPixels(pixelData, 0, 160, 0, 0, 160, 144)
                val imgBitmap = screenBitmap.asImageBitmap()

                _screenImage.value = imgBitmap
                _serialLogs.value = gameBoy.mmu.serialOutputBuffer.toString()
                _debugTick.value++

                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = targetFrameTimeMs - elapsed
                if (sleepTime > 0 && !_isTurbo.value) {
                    delay(sleepTime)
                } else {
                    delay(1)
                }
            }
        }
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
    }

    fun stepInstruction(count: Int = 1) {
        _isPaused.value = true
        viewModelScope.launch(Dispatchers.Default) {
            for (i in 0 until count) {
                gameBoy.stepInstruction()
            }
            screenBitmap.setPixels(gameBoy.ppu.frameBuffer, 0, 160, 0, 0, 160, 144)
            _screenImage.value = screenBitmap.asImageBitmap()
            _debugTick.value++
        }
    }

    fun stepFrame() {
        _isPaused.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val pixelData = gameBoy.runFrame()
            screenBitmap.setPixels(pixelData, 0, 160, 0, 0, 160, 144)
            _screenImage.value = screenBitmap.asImageBitmap()
            _debugTick.value++
        }
    }

    fun toggleTurbo() {
        _isTurbo.value = !_isTurbo.value
    }

    fun toggleAudioMute() {
        isAudioMuted.value = !isAudioMuted.value
    }

    // Button input handlers (active low: 0 when pressed, 1 when unpressed)
    fun setButtonState(button: GbButton, pressed: Boolean) {
        when (button) {
            GbButton.RIGHT -> joypadDir = if (pressed) joypadDir and 0x01.inv() else joypadDir or 0x01
            GbButton.LEFT  -> joypadDir = if (pressed) joypadDir and 0x02.inv() else joypadDir or 0x02
            GbButton.UP    -> joypadDir = if (pressed) joypadDir and 0x04.inv() else joypadDir or 0x04
            GbButton.DOWN  -> joypadDir = if (pressed) joypadDir and 0x08.inv() else joypadDir or 0x08

            GbButton.A      -> joypadAct = if (pressed) joypadAct and 0x01.inv() else joypadAct or 0x01
            GbButton.B      -> joypadAct = if (pressed) joypadAct and 0x02.inv() else joypadAct or 0x02
            GbButton.SELECT -> joypadAct = if (pressed) joypadAct and 0x04.inv() else joypadAct or 0x04
            GbButton.START  -> joypadAct = if (pressed) joypadAct and 0x08.inv() else joypadAct or 0x08
        }
    }

    fun quickSaveState(slot: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            val stateData = gameBoy.createSaveState()
            val jsonStr = saveStateAdapter.toJson(stateData)
            val entity = SaveStateEntity(
                romName = gameBoy.currentRomName,
                slotIndex = slot,
                title = "Slot $slot - ${gameBoy.currentRomName}",
                stateJson = jsonStr
            )
            repository.saveState(entity)

            // Save SRAM battery data
            val sramData = gameBoy.mmu.cartridge?.getSramData() ?: ByteArray(0)
            if (sramData.isNotEmpty()) {
                repository.saveSram(CartridgeSramEntity(gameBoy.currentRomName, sramData))
            }
        }
    }

    fun quickLoadState(slot: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.getSaveStates(gameBoy.currentRomName).collect { states ->
                val targetState = states.find { it.slotIndex == slot }
                if (targetState != null) {
                    val stateData = saveStateAdapter.fromJson(targetState.stateJson)
                    if (stateData != null) {
                        withContext(Dispatchers.Main) {
                            gameBoy.loadSaveState(stateData)
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameBoy.release()
    }
}

enum class GbButton {
    UP, DOWN, LEFT, RIGHT, A, B, SELECT, START
}
