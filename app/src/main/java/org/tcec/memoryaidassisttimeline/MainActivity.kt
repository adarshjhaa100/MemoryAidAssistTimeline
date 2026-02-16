package org.tcec.memoryaidassisttimeline

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.tcec.memoryaidassisttimeline.services.PassiveSensorService
import org.tcec.memoryaidassisttimeline.ui.MemoryViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint
import org.tcec.memoryaidassisttimeline.data.MemoryNode
import org.tcec.memoryaidassisttimeline.data.MemoryType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import java.text.SimpleDateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(startService = {
                    startForegroundService(Intent(this, PassiveSensorService::class.java))
                })
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(startService: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MemoryViewModel = hiltViewModel()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val permissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )

    LaunchedEffect(true) {
        permissions.launchMultiplePermissionRequest()
    }

    val systemUiController = androidx.compose.ui.platform.LocalView.current
    if (!systemUiController.isInEditMode) {
        SideEffect {
            val window = (systemUiController.context as Activity).window
            window.statusBarColor = android.graphics.Color.parseColor("#020617") // Slate950
            window.navigationBarColor = android.graphics.Color.parseColor("#020617") // Slate950
            androidx.core.view.WindowCompat.getInsetsController(window, systemUiController).isAppearanceLightStatusBars = false
            androidx.core.view.WindowCompat.getInsetsController(window, systemUiController).isAppearanceLightNavigationBars = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Memory Aid", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { viewModel.exportData(context) }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Share, contentDescription = "Export Data")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                onClick = { 
                    if (permissions.allPermissionsGranted) {
                        if (isServiceRunning) {
                             context.startService(Intent(context, PassiveSensorService::class.java).apply {
                                 action = PassiveSensorService.ACTION_STOP
                             })
                        } else {
                            startService() 
                        }
                    } else {
                        permissions.launchMultiplePermissionRequest()
                    }
                }
            ) {
                Text(if (isServiceRunning) "OFF" else "ON", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).background(MaterialTheme.colorScheme.background)) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text("Search memories...") },
                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = MaterialTheme.shapes.medium
            )

            // Status & Graph Section
            if (permissions.allPermissionsGranted) {
                StatusSection()
                Spacer(modifier = Modifier.height(16.dp))
                DecibelGraph()
                Spacer(modifier = Modifier.height(16.dp))
                RawDataSection()
                Divider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                MemoryTimeline(modifier = Modifier.weight(1f))
            } else {
                Text("Please grant permissions to start.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

// ... Status, RawData, DecibelGraph ...

@Composable
fun StatusSection(viewModel: MemoryViewModel = hiltViewModel()) {
    val voskStatus by viewModel.voskStatus.collectAsState()
    val tfliteStatus by viewModel.tfliteStatus.collectAsState()
    val location by viewModel.location.collectAsState()
    
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "SYSTEM STATUS", 
            style = MaterialTheme.typography.labelMedium, 
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Voice Engine Card
            StatusCard(
                title = "Voice Engine",
                status = voskStatus.substringAfter("Vosk: "),
                icon = androidx.compose.material.icons.Icons.Default.Star, // Placeholder for mic
                modifier = Modifier.weight(1f),
                isActive = voskStatus.contains("Listening") || voskStatus.contains("Ready")
            )
            
            // Location Card
            StatusCard(
                title = "Location",
                status = location?.replace("Lat:", "")?.replace("Lon:", "")?.trim() ?: "Waiting...",
                icon = androidx.compose.material.icons.Icons.Default.LocationOn,
                modifier = Modifier.weight(1f),
                isActive = location != null
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // AI Model Card (Full Width)
        StatusCard(
            title = "Context AI",
            status = tfliteStatus.substringAfter("TFLite: "),
            icon = androidx.compose.material.icons.Icons.Default.Info,
            modifier = Modifier.fillMaxWidth(),
            isActive = true
        )
    }
}

@Composable
fun StatusCard(title: String, status: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, isActive: Boolean) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    title, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                status, 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RawDataSection(viewModel: MemoryViewModel = hiltViewModel()) {
    val liveText by viewModel.liveTranscription.collectAsState()
    if (liveText.isNotBlank()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Text(
                text = "Live: $liveText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun DecibelGraph(viewModel: MemoryViewModel = hiltViewModel()) {
    val decibels by viewModel.decibels.collectAsState()
    
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "AUDIO FREQUENCY", 
            style = MaterialTheme.typography.labelMedium, 
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Heartbeat Visualizer
        androidx.compose.foundation.Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(60.dp) // Taller for waveform
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            val centerY = size.height / 2f
            val widthPerPoint = size.width / 100f
            
            // Draw baseline
            drawLine(
                color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, centerY),
                end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                strokeWidth = 1.dp.toPx()
            )

            if (decibels.isNotEmpty()) {
                val path = androidx.compose.ui.graphics.Path()
                // Start at center-left
                path.moveTo(0f, centerY)

                decibels.takeLast(100).forEachIndexed { index, db ->
                    // Normalize db (-60 to 0 mostly) to amplitude (0 to 1)
                    // -60db -> 0, 0db -> 1
                    val normalized = ((db + 60f) / 60f).coerceIn(0f, 1f)
                    
                    // Amplitude affects height. Max height is centerY (half height)
                    val amplitude = normalized * (size.height / 2.2f) 
                    
                    val x = index * widthPerPoint
                    
                    // Draw mirrored wave
                    // We simulate a wave by alternating up and down or just drawing the envelope?
                    // Let's draw the envelope (mirrored)
                    
                    // Actually, for a heartbeat/waveform look, we want the path to go up and down.
                    // But we only have RMS/Decibels (envelope). 
                    // To look cool, we mirror the envelope.
                    
                    // We'll draw top half first, then can flip for bottom? 
                    // Or just draw line from centerY-amp to centerY+amp
                    
                   val topY = centerY - amplitude
                   val bottomY = centerY + amplitude
                   
                   drawLine(
                       color = androidx.compose.ui.graphics.Color(0xFF6366F1), // Violet500
                       start = androidx.compose.ui.geometry.Offset(x, topY),
                       end = androidx.compose.ui.geometry.Offset(x, bottomY),
                       strokeWidth = 3f // Thicker bars
                   )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryTimeline(
    modifier: Modifier = Modifier,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching = searchQuery.isNotEmpty()
    
    // Group: Date -> 30-min block
    val groupedMemories = remember(memories) {
        memories.groupBy { 
            SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).format(Date(it.timestamp))
        }.mapValues { (_, dayMemories) ->
            dayMemories.groupBy { 
                val calendar = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val minute = calendar.get(Calendar.MINUTE)
                val blockStart = if (minute < 30) 0 else 30
                String.format(Locale.getDefault(), "%02d:%02d - %02d:%02d", hour, blockStart, hour + (if (blockStart == 30) 1 else 0), if (blockStart == 0) 30 else 0)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Sort Dates Descending (Newest Date First)
        val sortedDates = groupedMemories.keys.sortedByDescending { dateStr ->
             SimpleDateFormat("EEEE, MMM dd", Locale.getDefault()).parse(dateStr)?.time ?: 0L
        }

        for (date in sortedDates) {
            val timeBlocks = groupedMemories[date] ?: continue
            
            stickyHeader {
                DateHeader(date)
            }
            
            // Sort Time Blocks Descending (Newest Block First)
            val sortedBlocks = timeBlocks.keys.sortedDescending()
            
            for (timeBlock in sortedBlocks) {
                val nodes = timeBlocks[timeBlock] ?: continue
                item {
                    // Sort Nodes Descending (Newest Item First)
                    val sortedNodes = nodes.sortedByDescending { it.timestamp }
                    CollapsibleTimeBlock(timeBlock, sortedNodes, initiallyExpanded = isSearching)
                }
            }
        }

        if (memories.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(androidx.compose.material.icons.Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No memories yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Start the service to begin logging.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                     }
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant, // Distinction from block background
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun CollapsibleTimeBlock(timeRange: String, nodes: List<MemoryNode>, initiallyExpanded: Boolean) {
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // Simple Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
             
             Icon(
                imageVector = if (expanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                nodes.forEach { node ->
                    TimelineItem(node)
                }
            }
        }
    }
}

@Composable
fun TimelineItem(node: MemoryNode) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                 // Icon Type
                val icon = when (node.type) {
                    MemoryType.LOCATION -> androidx.compose.material.icons.Icons.Default.LocationOn
                    else -> androidx.compose.material.icons.Icons.Default.Edit // Or mic
                }
                val tint = when (node.type) {
                     MemoryType.LOCATION -> MaterialTheme.colorScheme.secondary
                     else -> MaterialTheme.colorScheme.primary
                }

                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))

                // Timestamp
                 Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(node.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = node.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (node.details != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = node.details,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}