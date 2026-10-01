package app.fieldwatch.wear.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.haptics.WearHaptics
import app.fieldwatch.wear.model.WearContactPayload
import app.fieldwatch.wear.ui.theme.AlertRed
import app.fieldwatch.wear.ui.theme.BrightCyan
import app.fieldwatch.wear.ui.theme.DarkGreen
import app.fieldwatch.wear.ui.theme.DarkSurface
import app.fieldwatch.wear.ui.theme.PureBlack
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClassicRadarScreen(
    onSelectHuntTarget: (WearContactPayload) -> Unit,
) {
    val summary by WearStateRepository.summary.collectAsState()
    val phoneConnected by WearStateRepository.phoneConnected.collectAsState()
    val standaloneScanning by WearStateRepository.standaloneScanning.collectAsState()

    LaunchedEffect(phoneConnected) {
        if (!phoneConnected) {
            app.fieldwatch.wear.WearApp.instance.scanner.startBurstSweep(durationMs = 20_000L)
        }
    }

    val context = LocalContext.current
    val haptics = remember { WearHaptics(context) }
    val textMeasurer = rememberTextMeasurer()

    var zoom by remember { mutableFloatStateOf(1.0f) }
    var selectedContact by remember { mutableStateOf<WearContactPayload?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 360-degree smooth radar sweep rotation (3.5 seconds per revolution)
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing)
        ),
        label = "SweepAngle"
    )

    val contacts = summary.contacts

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                // Physical crown rotation zooms radar in/out
                zoom = (zoom + event.verticalScrollPixels * 0.004f).coerceIn(1.0f, 3.5f)
                haptics.vibrateHuntTick()
                true
            }
    ) {
        // Main Radar Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(contacts, zoom) {
                    detectTapGestures(
                        onDoubleTap = { zoom = 1.0f },
                        onTap = { tapOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxR = minOf(size.width, size.height) / 2f - 14f

                            // Find nearest contact blip to tap
                            val hit = contacts.minByOrNull { contact ->
                                val pos = calculateBlipPosition(contact, center, maxR, zoom)
                                (pos - tapOffset).getDistance()
                            }
                            if (hit != null) {
                                val pos = calculateBlipPosition(hit, center, maxR, zoom)
                                if ((pos - tapOffset).getDistance() < 42f) {
                                    selectedContact = hit
                                    haptics.vibrateHeavyClick()
                                } else {
                                    selectedContact = null
                                }
                            } else {
                                selectedContact = null
                            }
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f - 14f

            // 1. Radar background disc & crosshairs
            drawCircle(
                color = Color(0xFF04180A),
                radius = maxR,
                center = center
            )
            // Delicate outer rim
            drawCircle(
                color = DarkGreen.copy(alpha = 0.5f),
                radius = maxR,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // Range rings (-40, -60, -80, -100 dBm)
            val rings = listOf(-40, -60, -80, -100)
            val ringStyle = TextStyle(
                color = DarkGreen.copy(alpha = 0.45f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )

            rings.forEach { dbm ->
                val r = calculateRadius(dbm, maxR, zoom)
                if (r <= maxR) {
                    drawCircle(
                        color = DarkGreen.copy(alpha = 0.28f),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                    // Draw ring label
                    val layout = textMeasurer.measure("$dbm", ringStyle)
                    drawText(
                        layout,
                        topLeft = Offset(center.x + 4f, center.y - r - layout.size.height)
                    )
                }
            }

            // Crosshair lines
            drawLine(
                color = DarkGreen.copy(alpha = 0.25f),
                start = Offset(center.x - maxR, center.y),
                end = Offset(center.x + maxR, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = DarkGreen.copy(alpha = 0.25f),
                start = Offset(center.x, center.y - maxR),
                end = Offset(center.x, center.y + maxR),
                strokeWidth = 1f
            )

            // 2. Rotating Phosphor Sweep Beam with fading trail
            rotate(sweepAngle, center) {
                drawRadarSweepBeam(center, maxR)
            }

            // 3. Contacts / Blips
            contacts.forEach { contact ->
                val pos = calculateBlipPosition(contact, center, maxR, zoom)
                val distFromCenter = (pos - center).getDistance()

                // Only render if within the circular radar disc
                if (distFromCenter <= maxR) {
                    val behindDeg = calculateSweepBehindDegrees(sweepAngle, contact.mac)
                    val phosphorGlow = calculatePhosphorDecay(behindDeg)

                    val baseColor = when {
                        contact.isAlert -> AlertRed
                        contact.kind == "WIFI" -> BrightCyan
                        else -> DarkGreen
                    }
                    val blipAlpha = 0.35f + 0.65f * phosphorGlow
                    val blipColor = baseColor.copy(alpha = blipAlpha)

                    // Draw contact blip
                    val blipRadius = if (contact.isAlert) 5.5f else 4f
                    drawCircle(blipColor, radius = blipRadius, center = pos)

                    // Draw phosphor halo if freshly swept
                    if (phosphorGlow > 0.6f) {
                        drawCircle(
                            blipColor.copy(alpha = 0.25f * phosphorGlow),
                            radius = blipRadius + 6f,
                            center = pos
                        )
                    }

                    // Highlight selected contact with yellow targeting reticle
                    if (selectedContact?.key == contact.key) {
                        drawCircle(
                            color = Color(0xFFFFD700),
                            radius = blipRadius + 7f,
                            center = pos,
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // Center "You" dot
            drawCircle(Color.White, radius = 2.5f, center = center)
        }

        // Top Status Overlay (Zoom + Total Contacts)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${contacts.size} RAD",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = DarkGreen
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${"%.1f".format(zoom)}x",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.LightGray
            )
        }

        // Bottom Selected Contact HUD
        selectedContact?.let { sel ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sel.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel.isAlert) AlertRed else Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "${sel.kind} • ${sel.rssi} dBm",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = DarkGreen
                        )
                    }
                    Button(
                        onClick = { onSelectHuntTarget(sel) },
                        colors = ButtonDefaults.buttonColors(backgroundColor = DarkGreen),
                        modifier = Modifier.size(width = 62.dp, height = 28.dp)
                    ) {
                        Text("Hunt", fontSize = 10.sp, color = PureBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Radar Mathematical Helpers
// -------------------------------------------------------------

private fun calculateRadius(rssi: Int, maxR: Float, zoom: Float): Float {
    val t = ((-30 - rssi).toFloat() / 70f).coerceIn(0f, 1f)
    return maxR * (0.12f + t * 0.88f) * zoom
}

private fun calculateAngle(mac: String): Double {
    val hash = mac.hashCode()
    return ((hash ushr 1) % 360) * Math.PI / 180.0
}

private fun calculateBlipPosition(contact: WearContactPayload, center: Offset, maxR: Float, zoom: Float): Offset {
    val angle = calculateAngle(contact.mac)
    val dist = calculateRadius(contact.rssi, maxR, zoom)
    return Offset(
        center.x + (cos(angle) * dist).toFloat(),
        center.y + (sin(angle) * dist).toFloat(),
    )
}

private fun calculateSweepBehindDegrees(sweepDeg: Float, mac: String): Float {
    val blipDeg = ((mac.hashCode() ushr 1) % 360).toFloat()
    var beam = (270f + sweepDeg) % 360f
    if (beam < 0f) beam += 360f
    var behind = beam - blipDeg
    while (behind < 0f) behind += 360f
    return behind
}

private fun calculatePhosphorDecay(behindDeg: Float): Float = when {
    behindDeg <= 10f -> 1.0f
    behindDeg < 140f -> {
        val u = (behindDeg - 10f) / 130f
        (1.0f - u) * (1.0f - u)
    }
    else -> 0.18f
}

private fun DrawScope.drawRadarSweepBeam(center: Offset, maxR: Float) {
    val trailDegrees = 65f
    val steps = 22
    val slice = trailDegrees / steps
    val box = Size(maxR * 2, maxR * 2)
    val origin = Offset(center.x - maxR, center.y - maxR)

    for (i in 0 until steps) {
        val t = i / (steps - 1f).coerceAtLeast(1f)
        val fade = 1f - t
        drawArc(
            color = DarkGreen.copy(alpha = 0.35f * fade * fade * fade),
            startAngle = -90f - i * slice,
            sweepAngle = -(slice + 1f),
            useCenter = true,
            topLeft = origin,
            size = box,
        )
    }

    // Leading crisp beam line
    val tip = Offset(center.x, center.y - maxR)
    drawLine(DarkGreen.copy(alpha = 0.20f), center, tip, strokeWidth = 10f, cap = StrokeCap.Round)
    drawLine(DarkGreen.copy(alpha = 0.60f), center, tip, strokeWidth = 4f, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.95f), center, tip, strokeWidth = 1.8f, cap = StrokeCap.Round)
}
