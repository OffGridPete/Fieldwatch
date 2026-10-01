package app.fieldwatch.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.ui.theme.AlertRed
import app.fieldwatch.wear.ui.theme.BrightCyan
import app.fieldwatch.wear.ui.theme.DarkGreen
import app.fieldwatch.wear.ui.theme.DarkSurface
import app.fieldwatch.wear.ui.theme.PureBlack

@Composable
fun DashboardScreen(
    onNavigateToRadar: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToHunt: () -> Unit,
    onTriggerSweep: () -> Unit,
) {
    val summary by WearStateRepository.summary.collectAsState()
    val phoneConnected by WearStateRepository.phoneConnected.collectAsState()
    val standaloneScanning by WearStateRepository.standaloneScanning.collectAsState()
    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        modifier = Modifier.background(PureBlack)
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header / App Title
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "FIELDWATCH",
                        style = MaterialTheme.typography.caption2.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = Color.LightGray
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // Connection Status Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (phoneConnected) DarkGreen else if (standaloneScanning) BrightCyan else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (phoneConnected) "PHONE SYNC" else if (standaloneScanning) "WATCH SWEEP" else "STANDALONE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            // Radio Count Grid
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Wi-Fi Count Box
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi",
                            tint = BrightCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${summary.wifiCount}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        Text(
                            text = "WI-FI",
                            fontSize = 8.sp,
                            color = Color.Gray
                        )
                    }

                    // BLE Count Box
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "BLE",
                            tint = DarkGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${summary.bleCount}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        Text(
                            text = "BLE",
                            fontSize = 8.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Tracker Alerts Chip (Highlighted in Red if any trackers present)
            item {
                Chip(
                    onClick = onNavigateToAlerts,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (summary.trackerCount > 0) AlertRed else DarkSurface,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerts",
                            tint = if (summary.trackerCount > 0) Color.White else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (summary.trackerCount > 0) "${summary.trackerCount} TRACKER ALERT" else "No Alerts",
                            fontWeight = if (summary.trackerCount > 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                )
            }

            // Classic Radar View Button
            item {
                Chip(
                    onClick = onNavigateToRadar,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = Color(0xFF0D2818),
                        contentColor = DarkGreen
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = DarkGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "CLASSIC RADAR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = DarkGreen
                        )
                    },
                    secondaryLabel = {
                        Text("360° phosphor scope", fontSize = 9.sp, color = Color.Gray)
                    }
                )
            }

            // Tactile Hunt Mode Button
            item {
                Chip(
                    onClick = onNavigateToHunt,
                    colors = ChipDefaults.chipColors(backgroundColor = DarkSurface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Hunt",
                            tint = DarkGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text("Wrist Hunt (Radar)", fontSize = 12.sp)
                    },
                    secondaryLabel = {
                        Text("Haptic Geiger meter", fontSize = 9.sp, color = Color.Gray)
                    }
                )
            }

            // Standalone Quick Sweep Button
            item {
                Chip(
                    onClick = onTriggerSweep,
                    colors = ChipDefaults.chipColors(backgroundColor = DarkSurface),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    label = {
                        Text(
                            text = if (standaloneScanning) "Scanning (10s)..." else "Watch BLE Sweep",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        }
    }
}
