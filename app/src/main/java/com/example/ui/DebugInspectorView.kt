package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.TestRoms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugInspectorView(
    viewModel: EmulatorViewModel,
    modifier: Modifier = Modifier
) {
    val gameBoy = viewModel.gameBoy
    val cpu = gameBoy.cpu
    val mmu = gameBoy.mmu
    val serialLogs by viewModel.serialLogs.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val debugTick by viewModel.debugTick.collectAsState()
    val romMetadata by viewModel.romMetadata.collectAsState()

    var hexAddressInput by remember { mutableStateOf("0100") }
    var inspectAddress by remember { mutableIntStateOf(0x0100) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121417))
            .padding(16.dp)
            .testTag("debug_inspector_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SM83 Hardware Debugger",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isPaused) "PAUSED" else "RUNNING",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ROM Header Metadata Panel
        item {
            RomMetadataPanel(metadata = romMetadata)
        }

        // Stepping & Execution Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Execution Control", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.togglePause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPaused) Color(0xFF2E7D32) else Color(0xFFC62828)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }

                        Button(
                            onClick = { viewModel.stepInstruction(1) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("step_instruction_button")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Step 1")
                        }

                        OutlinedButton(
                            onClick = { viewModel.stepInstruction(10) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("step_10_instructions_button")
                        ) {
                            Text("Step 10")
                        }

                        OutlinedButton(
                            onClick = { viewModel.stepFrame() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("step_frame_button")
                        ) {
                            Text("1 Frame")
                        }
                    }
                }
            }
        }

        // Real-time CPU Registers Card
        item {
            key(debugTick) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CPU Registers (SM83)", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
                            Text(
                                text = if (mmu.isDoubleSpeed) "Mode: CGB 8.38MHz" else "Mode: DMG 4.19MHz",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // 16-bit register pairs requested by prompt (PC, SP, AF, BC, DE, HL)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RegisterChip("PC", String.format("0x%04X", cpu.pc))
                            RegisterChip("SP", String.format("0x%04X", cpu.sp))
                            RegisterChip("AF", String.format("0x%04X", cpu.af))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RegisterChip("BC", String.format("0x%04X", cpu.bc))
                            RegisterChip("DE", String.format("0x%04X", cpu.de))
                            RegisterChip("HL", String.format("0x%04X", cpu.hl))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("8-bit Individual Registers", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RegisterChip("A", String.format("0x%02X", cpu.a))
                            RegisterChip("F", String.format("0x%02X", cpu.f))
                            RegisterChip("B", String.format("0x%02X", cpu.b))
                            RegisterChip("C", String.format("0x%02X", cpu.c))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RegisterChip("D", String.format("0x%02X", cpu.d))
                            RegisterChip("E", String.format("0x%02X", cpu.e))
                            RegisterChip("H", String.format("0x%02X", cpu.h))
                            RegisterChip("L", String.format("0x%02X", cpu.l))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Flags (F Register)", color = Color.Gray, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlagBadge("Z (Zero)", cpu.flagZ)
                            FlagBadge("N (Sub)", cpu.flagN)
                            FlagBadge("H (Half-Carry)", cpu.flagH)
                            FlagBadge("C (Carry)", cpu.flagC)
                        }
                    }
                }
            }
        }

        // Real-time Memory Status & Hex Inspector Card
        item {
            key(debugTick, inspectAddress) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Memory Status & Hex Inspector", color = Color(0xFFAB47BC), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MemoryBankChip("IE (0xFFFF)", String.format("0x%02X", mmu.ie))
                            MemoryBankChip("IF (0xFF0F)", String.format("0x%02X", mmu.ifReg))
                            MemoryBankChip("VRAM Bank", "${mmu.vramBankSelect}")
                            MemoryBankChip("WRAM Bank", "${mmu.wramBankSelect}")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Address Inspector Field
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = hexAddressInput,
                                onValueChange = { input ->
                                    hexAddressInput = input
                                    input.toIntOrNull(16)?.let { addr ->
                                        inspectAddress = addr.coerceIn(0, 0xFFF0)
                                    }
                                },
                                label = { Text("Hex Address (e.g. 0100, C000)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFAB47BC),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color(0xFFAB47BC),
                                    unfocusedLabelColor = Color.Gray
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("memory_address_input")
                            )

                            Button(
                                onClick = { inspectAddress = cpu.pc and 0xFFF0 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                            ) {
                                Text("Jump to PC")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Hex View Display (4 rows x 16 bytes = 64 bytes)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            val startAddr = inspectAddress and 0xFFF0
                            for (row in 0 until 4) {
                                val rowAddr = startAddr + (row * 16)
                                if (rowAddr <= 0xFFFF) {
                                    val hexBytes = StringBuilder()
                                    val asciiBytes = StringBuilder()
                                    for (col in 0 until 16) {
                                        val addr = rowAddr + col
                                        if (addr <= 0xFFFF) {
                                            val byteVal = mmu.readByte(addr)
                                            hexBytes.append(String.format("%02X ", byteVal))
                                            asciiBytes.append(
                                                if (byteVal in 32..126) byteVal.toChar() else '.'
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = String.format("%04X:", rowAddr),
                                            color = Color(0xFFFFB74D),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = hexBytes.toString().trim(),
                                            color = Color(0xFF81C784),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = asciiBytes.toString(),
                                            color = Color.LightGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Blargg's Test Suite Execution
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Blargg's Hardware Verification Suite", color = Color(0xFF66BB6A), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Test cycle-accuracy against all 11 cpu_instrs, instr_timing, mem_timing & dmg-acid2.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.runAllBlarggTests() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.testTag("run_blargg_tests_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run All 11 Tests")
                        }

                        OutlinedButton(
                            onClick = { viewModel.loadAcid2Test() },
                            modifier = Modifier.testTag("run_acid2_test_button")
                        ) {
                            Text("Run dmg-acid2")
                        }
                    }
                }
            }
        }

        // Test Results Matrix
        if (testResults.isNotEmpty()) {
            item {
                Text("Test Results (Pass/Fail Matrix)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            items(testResults) { result ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1D21), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (result.status == TestRoms.TestStatus.PASSED) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Passed", tint = Color(0xFF4CAF50))
                        } else {
                            Icon(Icons.Default.Error, contentDescription = "Failed", tint = Color(0xFFF44336))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(result.testName, color = Color.White, fontWeight = FontWeight.Medium)
                    }

                    Text(
                        text = if (result.status == TestRoms.TestStatus.PASSED) "PASSED" else "FAILED",
                        color = if (result.status == TestRoms.TestStatus.PASSED) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Serial Log Buffer Stream
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Serial Port Output (0xFF01 / SB)", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Color.Black, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = serialLogs.ifEmpty { "[No serial output yet]" },
                            color = Color(0xFF00FF00),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryBankChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF2B3038), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color(0xFFCE93D8), fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RegisterChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0xFF2B3038), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FlagBadge(label: String, active: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (active) Color(0xFF1B5E20) else Color(0xFF333333),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (active) Color(0xFF4CAF50) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFF81C784) else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
