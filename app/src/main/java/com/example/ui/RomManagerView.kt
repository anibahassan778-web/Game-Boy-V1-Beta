package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.TestRoms

@Composable
fun RomManagerView(
    viewModel: EmulatorViewModel,
    onRomSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val romTitle by viewModel.romTitle.collectAsState()
    val romMetadata by viewModel.romMetadata.collectAsState()
    val isCgb by viewModel.isCgb.collectAsState()
    val headerChecksumPassed by viewModel.headerChecksumPassed.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.loadRomFromUri(context, it)
            onRomSelected()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121417))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ROM Cartridge Library",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Active Cartridge Card with Header Metadata
        item {
            RomMetadataPanel(metadata = romMetadata)
        }

        // Import External ROM Button
        item {
            Button(
                onClick = { filePickerLauncher.launch("*/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("import_rom_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import .gb / .gbc ROM File", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Built-in Validation Test ROMs Section
        item {
            Text(
                text = "Built-in Blargg Test ROMs",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        items(TestRoms.blarggCpuTests.indices.toList()) { index ->
            val testNum = index + 1
            val testName = TestRoms.blarggCpuTests[index]

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2228)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.loadBuiltInTestRom(testNum)
                        onRomSelected()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideogameAsset, contentDescription = null, tint = Color(0xFFFFA726))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(testName, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Text("Run", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BadgeChip(text: String, backgroundColor: Color = Color(0xFF37474F)) {
    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
