package app.fieldwatch.wear.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
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
import androidx.wear.compose.material.items
import androidx.wear.compose.material.rememberScalingLazyListState
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.model.WearAlertPayload
import app.fieldwatch.wear.ui.theme.AlertRed
import app.fieldwatch.wear.ui.theme.DarkGreen
import app.fieldwatch.wear.ui.theme.DarkSurface
import app.fieldwatch.wear.ui.theme.PureBlack

@Composable
fun AlertsScreen(
    onSelectHuntTarget: (WearAlertPayload) -> Unit,
) {
    val alerts by WearStateRepository.alerts.collectAsState()
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
            item {
                Text(
                    text = "ACTIVE ALERTS",
                    style = MaterialTheme.typography.caption2.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = AlertRed
                    )
                )
            }

            if (alerts.isEmpty()) {
                item {
                    Text(
                        text = "No active tracker or co-travel alerts detected.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(alerts) { alert ->
                    Chip(
                        onClick = { onSelectHuntTarget(alert) },
                        colors = ChipDefaults.chipColors(backgroundColor = DarkSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        label = {
                            Text(
                                text = alert.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        secondaryLabel = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = alert.kind,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertRed
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${alert.rssi} dBm",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = DarkGreen
                                )
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Hunt",
                                tint = DarkGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}
