package org.tcec.memoryaidassisttimeline

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(startService: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: MemoryViewModel = hiltViewModel()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    
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
        floatingActionButton = {
            FloatingActionButton(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                onClick = { 
                    if (isServiceRunning) {
                         context.startService(Intent(context, PassiveSensorService::class.java).apply {
                             action = PassiveSensorService.ACTION_STOP
                         })
                    } else {
                        startService() 
                    }
                }
            ) {
                Text(if (isServiceRunning) "OFF" else "ON")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Status & Graph Section
            if (permissions.allPermissionsGranted) {
                StatusSection()
                Spacer(modifier = Modifier.height(8.dp))
                DecibelGraph()
                Spacer(modifier = Modifier.height(8.dp))
                RawDataSection()
                Divider(thickness = 2.dp)
                MemoryTimeline(modifier = Modifier.weight(1f))
            } else {
                Text("Please grant permissions to start.", Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun StatusSection(viewModel: MemoryViewModel = hiltViewModel()) {
    val voskStatus by viewModel.voskStatus.collectAsState()
    val tfliteStatus by viewModel.tfliteStatus.collectAsState()
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Status Monitor", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = voskStatus, style = MaterialTheme.typography.bodySmall)
        Text(text = tfliteStatus, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RawDataSection(viewModel: MemoryViewModel = hiltViewModel()) {
    val liveText by viewModel.liveTranscription.collectAsState()
    
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Raw Data (Live)", style = MaterialTheme.typography.labelMedium)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                 Text(
                    text = if (liveText.isEmpty()) "Waiting for speech..." else liveText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DecibelGraph(viewModel: MemoryViewModel = hiltViewModel()) {
    val decibels by viewModel.decibels.collectAsState()
    
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Audio Levels (dB)", style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp) // Reduced height
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clip(MaterialTheme.shapes.small)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                if (decibels.isNotEmpty()) {
                    val widthPerPoint = size.width / 100f
                    val path = androidx.compose.ui.graphics.Path()
                    
                    // Normalize -100dB to 0dB range to 0..height
                    fun yPos(db: Float): Float {
                        val normalized = (db + 100) / 100f // 0.0 to 1.0
                        return size.height - (normalized * size.height)
                    }
                    
                    path.moveTo(0f, yPos(decibels.first()))
                    decibels.forEachIndexed { index, db ->
                        path.lineTo(index * widthPerPoint, yPos(db))
                    }
                    
                    drawPath(
                        path = path,
                        color = androidx.compose.ui.graphics.Color.Green,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
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
    
    // Group memories by date
    val groupedMemories = remember(memories) {
        memories.groupBy { 
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.timestamp))
        }
    }

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        groupedMemories.forEach { (date, memoryList) ->
            stickyHeader {
                DateHeader(date)
            }

            items(memoryList) { node ->
                TimelineItem(node)
            }
        }
        
        if (memories.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No memories yet. Say something!", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = date, // You might want to format this nicely (e.g., "Today", "Yesterday")
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun TimelineItem(node: MemoryNode) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        // Timeline Line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
        ) {
            // Top line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            // Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Bottom line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
        
        // Content
        Spacer(modifier = Modifier.width(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(node.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = node.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}