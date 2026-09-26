package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.emulator.TestRoms

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomManagerView(
    viewModel: EmulatorViewModel,
    onRomSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val romList by viewModel.romList.collectAsState()
    val romMetadata by viewModel.romMetadata.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All Games") }
    var showMetadataDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.loadRomFromUri(context, it)
            onRomSelected()
        }
    }

    // Filter games by search query
    val filteredList = remember(romList, searchQuery) {
        if (searchQuery.isBlank()) {
            romList
        } else {
            romList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.fileName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C)) // Pure OLED dark background matching screenshot
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar matching screenshot
            Surface(
                color = Color(0xFF0A0A0C),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Hamburger Menu Icon
                        IconButton(
                            onClick = { showMenuSheet = true },
                            modifier = Modifier.testTag("menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Middle: "All Games" Title
                        if (!isSearchActive) {
                            Text(
                                text = selectedCategory,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                        } else {
                            // Expandable Search Input
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search games...", color = Color.Gray) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedIndicatorColor = Color.White,
                                    unfocusedIndicatorColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                        }

                        // Right: Search & Grid/List Icons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Game Library Content (List or Grid)
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DiscIcon(modifier = Modifier.size(72.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Games Found",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the folder icon below to import ROMs (.gb, .gbc, .iso, .zip)",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (isGridView) {
                // Grid View Layout
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { game ->
                        GameGridCard(
                            game = game,
                            onClick = {
                                viewModel.selectRomEntry(game)
                                onRomSelected()
                            }
                        )
                    }
                }
            } else {
                // List View Layout (Exact match to screenshot)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { game ->
                        GameListItem(
                            game = game,
                            onClick = {
                                viewModel.selectRomEntry(game)
                                onRomSelected()
                            },
                            onLongClick = {
                                viewModel.selectRomEntry(game)
                                showMetadataDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Floating Action Button at Bottom Right (matching screenshot: circular, dark, folder icon)
        FloatingActionButton(
            onClick = { filePickerLauncher.launch("*/*") },
            containerColor = Color(0xFF26262A), // Dark charcoal circle from screenshot
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .testTag("import_rom_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = "Import ROM",
                modifier = Modifier.size(26.dp)
            )
        }

        // Side Navigation Menu / Categories Bottom Sheet
        if (showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMenuSheet = false },
                containerColor = Color(0xFF16171B),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Library Navigation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    HorizontalDivider(color = Color(0xFF2C2D35))

                    MenuItemRow(
                        icon = Icons.Default.Games,
                        title = "All Games (${romList.size})",
                        isSelected = selectedCategory == "All Games",
                        onClick = {
                            selectedCategory = "All Games"
                            showMenuSheet = false
                        }
                    )

                    MenuItemRow(
                        icon = Icons.Default.SdCard,
                        title = "Inspect Active ROM Header",
                        isSelected = false,
                        onClick = {
                            showMenuSheet = false
                            showMetadataDialog = true
                        }
                    )

                    MenuItemRow(
                        icon = Icons.Default.Science,
                        title = "Run Blargg CPU Test Suite",
                        isSelected = false,
                        onClick = {
                            showMenuSheet = false
                            viewModel.runAllBlarggTests()
                        }
                    )

                    MenuItemRow(
                        icon = Icons.Default.FolderOpen,
                        title = "Import External ROM File",
                        isSelected = false,
                        onClick = {
                            showMenuSheet = false
                            filePickerLauncher.launch("*/*")
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // ROM Metadata Inspector Dialog
        if (showMetadataDialog) {
            AlertDialog(
                onDismissRequest = { showMetadataDialog = false },
                containerColor = Color(0xFF181A20),
                title = {
                    Text(
                        text = "ROM Cartridge Details",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    RomMetadataPanel(metadata = romMetadata)
                },
                confirmButton = {
                    TextButton(onClick = { showMetadataDialog = false }) {
                        Text("Close", color = Color(0xFF42A5F5))
                    }
                }
            )
        }
    }
}

/**
 * Game Row Item designed to match Screenshot_20260926_205152.jpg exactly:
 * - Left: Disc Icon (CD/DVD game disc outline with groove and hole)
 * - Middle: Title (e.g. "God of War II") + Subtitle (e.g. "2-9-2019.iso (1059.69 MB)")
 * - Right: 5 Yellow Stars (★★★★★) + USA Flag badge
 */
@Composable
fun GameListItem(
    game: RomEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Authentic Game Disc Icon (matching screenshot)
        DiscIcon(
            modifier = Modifier
                .size(46.dp)
                .padding(end = 4.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Middle: Game Title and File Information
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = game.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${game.fileName} (${game.fileSizeFormatted})",
                color = Color(0xFFA5A5AA),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: 5 Stars + USA Country Flag (matching screenshot)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            // 5 Yellow Stars
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(5) {
                    Text(
                        text = "★",
                        color = Color(0xFFFFD700), // Gold yellow
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Country Flag (USA Flag styled like the screenshot)
            UsaFlagBadge(
                modifier = Modifier.size(width = 24.dp, height = 15.dp)
            )
        }
    }
}

/**
 * Grid Card variant when switching to Grid layout
 */
@Composable
fun GameGridCard(
    game: RomEntry,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141519)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DiscIcon(modifier = Modifier.size(54.dp))
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = game.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = game.fileSizeFormatted,
                color = Color.Gray,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    repeat(5) {
                        Text(text = "★", color = Color(0xFFFFD700), fontSize = 9.sp)
                    }
                }
                UsaFlagBadge(modifier = Modifier.size(width = 18.dp, height = 11.dp))
            }
        }
    }
}

/**
 * Authentic Disc Icon matching the circular disc graphic in the screenshot:
 * - Outer boundary circle with smooth white stroke
 * - Concentric data-track groove circle
 * - Center spindle hub circle
 * - Center transparent cutout hole
 * - Clean anti-aliased reflection shine
 */
@Composable
fun DiscIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Outer rim
        drawCircle(
            color = Color.White,
            radius = radius - 1.5.dp.toPx(),
            center = center,
            style = Stroke(width = 1.8.dp.toPx())
        )

        // Middle concentric groove
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = radius * 0.62f,
            center = center,
            style = Stroke(width = 1.3.dp.toPx())
        )

        // Inner spindle hub
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = radius * 0.32f,
            center = center,
            style = Stroke(width = 1.4.dp.toPx())
        )

        // Center hole
        drawCircle(
            color = Color(0xFF0A0A0C),
            radius = radius * 0.16f,
            center = center
        )

        // Small decorative reflection arc on the disc surface
        drawArc(
            color = Color.White.copy(alpha = 0.35f),
            startAngle = 200f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.82f, center.y - radius * 0.82f),
            size = Size(radius * 1.64f, radius * 1.64f),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

/**
 * Ultra-crisp USA Flag badge rendered via Canvas to match the screenshot
 */
@Composable
fun UsaFlagBadge(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Background white
        drawRect(color = Color.White, topLeft = Offset.Zero, size = Size(w, h))

        // 7 Red stripes (13 stripes total: alternating Red and White)
        val stripeH = h / 13f
        val redColor = Color(0xFFB22234)
        for (i in 0 until 13 step 2) {
            drawRect(
                color = redColor,
                topLeft = Offset(0f, i * stripeH),
                size = Size(w, stripeH)
            )
        }

        // Blue Canton in top-left (7 stripes tall, ~40% width)
        val cantonW = w * 0.42f
        val cantonH = stripeH * 7f
        val blueColor = Color(0xFF3C3B6E)
        drawRect(
            color = blueColor,
            topLeft = Offset.Zero,
            size = Size(cantonW, cantonH)
        )

        // Subtle stars pattern (dots)
        val dotRadius = 0.7.dp.toPx()
        val numCols = 3
        val numRows = 3
        for (r in 1..numRows) {
            for (c in 1..numCols) {
                drawCircle(
                    color = Color.White,
                    radius = dotRadius,
                    center = Offset(cantonW * (c / (numCols + 1f)), cantonH * (r / (numRows + 1f)))
                )
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFF23252E) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF42A5F5) else Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = if (isSelected) Color(0xFF42A5F5) else Color.White,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}
