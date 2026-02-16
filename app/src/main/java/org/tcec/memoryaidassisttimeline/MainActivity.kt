package org.tcec.memoryaidassisttimeline

import android.Manifest
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory Aid") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
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
                Text(if (isServiceRunning) "OFF" else "ON")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
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
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = MaterialTheme.shapes.medium
            )

            // Status & Graph Section
            if (permissions.allPermissionsGranted) {
                StatusSection()
                Spacer(modifier = Modifier.height(8.dp))
                DecibelGraph()
                Spacer(modifier = Modifier.height(8.dp))
                RawDataSection()
                Divider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
                MemoryTimeline(modifier = Modifier.weight(1f))
            } else {
                Text("Please grant permissions to start.", Modifier.padding(16.dp))
            }
        }
    }
}

// ... Status, RawData, DecibelGraph ... (Keeping them implies I need to ensure I don't overwrite them or I rewrite them if I selected the whole file range. I selected from line 44 down, so I need to include them or be careful.
// Wait, I selected from line 44 to 285. This covers everything including StatusSection, etc. I must re-include them or I will lose them.
// I will re-include them for safety, but formatted nicely.

@Composable
fun StatusSection(viewModel: MemoryViewModel = hiltViewModel()) {
    val voskStatus by viewModel.voskStatus.collectAsState()
    val tfliteStatus by viewModel.tfliteStatus.collectAsState()
    val location by viewModel.location.collectAsState()
    
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
             Icon(androidx.compose.material.icons.Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
             Spacer(modifier = Modifier.width(4.dp))
             Text("System Status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = voskStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(text = tfliteStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (location != null) {
            Text(text = location ?: "Checking location...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        } else {
             Text(text = "Location: Waiting...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    // Minimal graph
    androidx.compose.foundation.Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(30.dp)
        .padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        if (decibels.isNotEmpty()) {
            val widthPerPoint = size.width / 100f
            val path = androidx.compose.ui.graphics.Path()
            fun yPos(db: Float): Float {
                val normalized = (db + 100) / 100f
                return size.height - (normalized * size.height)
            }
            path.moveTo(0f, yPos(decibels.first()))
            decibels.forEachIndexed { index, db ->
                path.lineTo(index * widthPerPoint, yPos(db))
            }
            drawPath(path = path, color = androidx.compose.ui.graphics.Color.Green, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
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
    
    // Group: Date -> 30-min block (Epoch / (30*60*1000))
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
        for ((date, timeBlocks) in groupedMemories) {
            stickyHeader {
                DateHeader(date)
            }
            
            for ((timeBlock, nodes) in timeBlocks.toSortedMap()) {
                item {
                    CollapsibleTimeBlock(timeBlock, nodes)
                }
            }
        }

        if (memories.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No memories found.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

@Composable
fun CollapsibleTimeBlock(timeRange: String, nodes: List<MemoryNode>) {
    var expanded by remember { mutableStateOf(false) } 
    
    // Summary Logic: Paragraph style
    // Filter out duplicates usually? For now just join.
    // We prioritize text content.
    val distinctContent = nodes.map { it.content }.distinct()
    val summaryText = if (distinctContent.isEmpty()) {
        "No Activity"
    } else {
        // Join with separating bullet or period
        distinctContent.joinToString(". ") {
             if (it.length > 50) it.take(50) + "..." else it
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { expanded = !expanded }
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (expanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        // Content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300))
        ) {
            Column {
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                nodes.sortedBy { it.timestamp }.forEach { node ->
                    TimelineItem(node)
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
fun TimelineItem(node: MemoryNode) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)) {
        
        // Icon based on type
        val icon = when (node.type) {
            MemoryType.LOCATION -> Icons.Default.LocationOn
            MemoryType.SENSOR -> Icons.Default.Star
            else -> Icons.Default.Edit
        }
        
        val color = when (node.type) {
            MemoryType.LOCATION -> Color(0xFF4D96FF)
            MemoryType.SENSOR -> Color(0xFF6BCB77)
            else -> MaterialTheme.colorScheme.onSurface
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(node.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.small
            ) {
                 Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = node.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (node.details != null) {
                         Text(
                            text = node.details,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                 }
            }
        }
    }
}