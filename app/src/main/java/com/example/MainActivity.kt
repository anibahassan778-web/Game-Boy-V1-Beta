package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.ui.DebugInspectorView
import com.example.ui.EmulatorViewModel
import com.example.ui.GameBoyScreen
import com.example.ui.RomManagerView
import com.example.ui.SettingsView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: EmulatorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF1E2228),
                            contentColor = Color.White,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            NavigationBarItem(
                                selected = (selectedTab == 0),
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.VideogameAsset, contentDescription = "Console") },
                                label = { Text("Console") },
                                modifier = Modifier.testTag("nav_console_tab")
                            )
                            NavigationBarItem(
                                selected = (selectedTab == 1),
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Folder, contentDescription = "ROMs") },
                                label = { Text("ROMs") },
                                modifier = Modifier.testTag("nav_roms_tab")
                            )
                            NavigationBarItem(
                                selected = (selectedTab == 2),
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.BugReport, contentDescription = "Debugger") },
                                label = { Text("Debugger") },
                                modifier = Modifier.testTag("nav_debugger_tab")
                            )
                            NavigationBarItem(
                                selected = (selectedTab == 3),
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_settings_tab")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> GameBoyScreen(viewModel = viewModel, onOpenMenu = { selectedTab = 1 })
                            1 -> RomManagerView(viewModel = viewModel, onRomSelected = { selectedTab = 0 })
                            2 -> DebugInspectorView(viewModel = viewModel)
                            3 -> SettingsView(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
