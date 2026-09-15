package app.fieldwatch.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntCue
import app.fieldwatch.domain.Palette
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.rssiColor
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuntScreen(
    vm: FieldwatchViewModel,
    onBack: () -> Unit,
    demoMode: Boolean = false,
    huntBeep: Boolean = false,
    huntVibrate: Boolean = false,
) {
    val hunt by vm.hunt.collectAsStateWithLifecycle()
    LaunchedEffect(hunt.active, huntBeep, huntVibrate) {
        if (!hunt.active || (!huntBeep && !huntVibrate)) return@LaunchedEffect
        var lastTick = 0L
        while (true) {
            val live = vm.hunt.value
            val interval = Hunt.tickIntervalMs(live.device?.rssi, live.cue)
            if (interval == null) {
                delay(200)
                continue
            }
            val wait = interval - (System.currentTimeMillis() - lastTick)
            if (wait > 0L) {
                delay(wait.coerceAtMost(80L))
                continue
            }
            vm.huntTick(huntBeep, huntVibrate)
            lastTick = System.currentTimeMillis()
        }
    }
    val device = hunt.device
    val rssi = device?.rssi
    val night = LocalNightMode.current
    val accent = (device?.fleetIds?.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssi?.let { rssiColor(it) }
        ?: MaterialTheme.colorScheme.primary)
        .nightIf(night)
    val cueColor = when (hunt.cue) {
        HuntCue.VERY_CLOSE -> Color(0xFF7CFF3D).nightIf(night)
        HuntCue.CLOSER -> Color(0xFF3DFF9A).nightIf(night)
        HuntCue.FURTHER -> Color(0xFFFF3D5A).nightIf(night)
        HuntCue.SAME -> MaterialTheme.colorScheme.onSurface
        HuntCue.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
        HuntCue.QUIET -> Color(0xFFFFB020).nightIf(night)
        HuntCue.GONE -> Color(0xFFFF3D5A).nightIf(night)
    }
    val now = System.currentTimeMillis()
    val heardAgo = if (hunt.lastSeen > 0L) ((now - hunt.lastSeen) / 1000L).coerceAtLeast(0L) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val shown = hunt.device?.let { MacUtil.redactMacIn(hunt.title, it.mac, demoMode) } ?: hunt.title
                    Text(shown.ifBlank { "Hunt" }, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FieldwatchActionButton(
                    onClick = vm::resetHunt,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = hunt.active,
                ) {
                    Text("Reset this hunt")
                }
                FieldwatchActionButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back to detail")
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Beep", Modifier.weight(1f))
                        FieldwatchSwitch(
                            huntBeep,
                            { on ->
                                vm.updateSettings { it.copy(huntBeep = on) }
                                if (on) vm.huntTick(true, huntVibrate)
                            },
                        )
                    }
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Vibrate", Modifier.weight(1f))
                        FieldwatchSwitch(
                            huntVibrate,
                            { on ->
                                vm.updateSettings { it.copy(huntVibrate = on) }
                                if (on) vm.huntTick(huntBeep, true)
                            },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Hunt.label(hunt.cue),
                    color = cueColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Text(
                    Hunt.hint(hunt.cue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                rssi?.toString() ?: "—",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 72.sp,
                color = accent,
            )
            Text(
                rssi?.let { DeviceExplain.rssiExplain(it) } ?: "no live RSSI",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (hunt.peakRssi > -127) {
                    "Loudest this hunt  ${hunt.peakRssi} dBm"
                } else {
                    "Loudest this hunt  —"
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when {
                    heardAgo == null -> "last heard  —"
                    heardAgo < 60L -> "last heard ${heardAgo}s ago"
                    else -> "last heard ${heardAgo / 60L}m ago"
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            HuntNeedle(
                cue = hunt.cue,
                color = cueColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Sparkline(
                hunt.samples,
                accent,
                Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )
        }
    }
}

@Composable
private fun HuntNeedle(
    cue: HuntCue,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val period = when (cue) {
        HuntCue.VERY_CLOSE -> 700
        HuntCue.CLOSER -> 900
        HuntCue.FURTHER -> 1100
        HuntCue.SAME -> 2200
        HuntCue.WAITING -> 1800
        HuntCue.QUIET, HuntCue.GONE -> 2400
    }
    val t by rememberInfiniteTransition(label = "huntNeedle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(period, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "huntNeedleT",
    )
    val youColor = MaterialTheme.colorScheme.onSurface
    val ringIdle = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val measurer = rememberTextMeasurer()
    val youStyle = androidx.compose.ui.text.TextStyle(
        color = youColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
    )
    Canvas(modifier.fillMaxWidth()) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val maxR = min(size.width, size.height) * 0.42f
        if (maxR < 8f) return@Canvas
        for (i in 1..3) {
            drawCircle(
                color = ringIdle,
                radius = maxR * i / 3f,
                center = c,
                style = Stroke(width = 1.4f),
            )
        }
        fun pulse(frac: Float, alpha: Float, width: Float) {
            val r = (maxR * frac).coerceAtLeast(2f)
            drawCircle(
                color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = r,
                center = c,
                style = Stroke(width = width),
            )
        }
        when (cue) {
            HuntCue.CLOSER, HuntCue.VERY_CLOSE -> {
                repeat(3) { i ->
                    val frac = ((1f - t) + i / 3f).mod(1f)
                    pulse(frac, (1f - frac) * 0.85f, if (cue == HuntCue.VERY_CLOSE) 5.5f else 4f)
                }
                if (cue == HuntCue.VERY_CLOSE) {
                    drawCircle(color.copy(alpha = 0.22f + 0.18f * (1f - t)), radius = maxR * 0.22f, center = c)
                }
            }
            HuntCue.FURTHER -> {
                repeat(3) { i ->
                    val frac = (t + i / 3f).mod(1f)
                    pulse(frac, (1f - frac) * 0.85f, 4f)
                }
            }
            HuntCue.SAME -> pulse(0.55f, 0.55f, 3.2f)
            HuntCue.WAITING -> pulse(0.4f + 0.2f * t, 0.35f, 2.6f)
            HuntCue.QUIET -> pulse(0.72f, 0.4f, 2.8f)
            HuntCue.GONE -> pulse(0.88f, 0.28f, 2.2f)
        }
        drawCircle(color.copy(alpha = 0.95f), radius = 5.5f, center = c)
        val you = measurer.measure("YOU", youStyle)
        drawText(
            you,
            topLeft = Offset(c.x - you.size.width / 2f, c.y + 10f),
        )
    }
}
