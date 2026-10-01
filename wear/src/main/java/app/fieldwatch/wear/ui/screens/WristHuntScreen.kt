package app.fieldwatch.wear.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.haptics.WearHaptics
import app.fieldwatch.wear.ui.theme.AlertRed
import app.fieldwatch.wear.ui.theme.DarkGreen
import app.fieldwatch.wear.ui.theme.PureBlack
import app.fieldwatch.wear.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WristHuntScreen(onStopHunt: () -> Unit) {
    val hunt by WearStateRepository.hunt.collectAsState()
    val context = LocalContext.current
    val haptics = WearHaptics(context)

    val currentRssi = hunt?.rssi ?: -100
    // Normalize RSSI from [-100, -35] to [0.0, 1.0]
    val normalizedSignal = ((currentRssi + 100).toFloat() / 65f).coerceIn(0f, 1f)

    // Tactile Geiger counter tick loop
    LaunchedEffect(currentRssi) {
        if (hunt != null) {
            val tickDelayMs = when {
                currentRssi >= -50 -> 120L
                currentRssi >= -65 -> 250L
                currentRssi >= -80 -> 550L
                currentRssi >= -90 -> 1000L
                else -> 1800L
            }
            while (isActive) {
                haptics.vibrateHuntTick()
                delay(tickDelayMs)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack),
        contentAlignment = Alignment.Center
    ) {
        // Circular RSSI Arc Gauge
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 8.dp.toPx()
            // Background Track
            drawArc(
                color = Color.DarkGray.copy(alpha = 0.4f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Active Signal Arc
            val arcColor = when {
                currentRssi >= -55 -> AlertRed
                currentRssi >= -75 -> DarkGreen
                else -> WarningAmber
            }
            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = 270f * normalizedSignal,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        // Center Info Stack
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "HUNTING",
                style = MaterialTheme.typography.caption2.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color.LightGray
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = hunt?.targetName ?: "Searching...",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$currentRssi",
                fontSize = 32.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (currentRssi >= -55) AlertRed else DarkGreen
            )
            Text(
                text = "dBm (${hunt?.proximity ?: "TRACKING"})",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onStopHunt,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF262626)),
                modifier = Modifier.size(width = 80.dp, height = 28.dp)
            ) {
                Text("Stop", fontSize = 10.sp, color = Color.White)
            }
        }
    }
}
