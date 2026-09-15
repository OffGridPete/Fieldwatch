package app.fieldwatch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.SignatureClass

object ClassGlyphs {
    val unmatched: ImageVector get() = Icons.Outlined.QuestionMark

    fun of(kind: SignatureClass?): ImageVector = when (kind) {
        null -> unmatched
        SignatureClass.FINDER -> Icons.Outlined.Sell
        SignatureClass.BEACON -> Icons.Outlined.WifiTethering
        SignatureClass.SIGNAGE -> Icons.Outlined.Storefront
        SignatureClass.WEARABLE -> Icons.Outlined.Watch
        SignatureClass.SURVEILLANCE -> Icons.Outlined.Videocam
        SignatureClass.DRONE -> Icons.Outlined.Flight
        SignatureClass.HACKING -> Icons.Outlined.BugReport
        SignatureClass.BODYWORN -> Icons.Outlined.Watch
        SignatureClass.LAW_ENFORCEMENT -> Icons.Outlined.LocalPolice
        SignatureClass.VEHICLE -> Icons.Outlined.DirectionsCar
        SignatureClass.GLASSES -> Icons.Outlined.Face
        SignatureClass.AUDIO -> Icons.Outlined.Headphones
        SignatureClass.CAMERA -> Icons.Outlined.PhotoCamera
        SignatureClass.THERMOSTAT -> Icons.Outlined.DeviceThermostat
        SignatureClass.LOCK -> Icons.Outlined.Lock
        SignatureClass.HEALTH -> Icons.Outlined.LocalHospital
        SignatureClass.HOME -> Icons.Outlined.Home
        SignatureClass.ISP -> Icons.Outlined.Router
        SignatureClass.MESH -> Icons.Outlined.Hub
        SignatureClass.PHONE -> Icons.Outlined.Smartphone
        SignatureClass.OTHER -> Icons.Outlined.Category
    }
}

@Composable
fun RadioClassBadge(
    classKind: SignatureClass?,
    accent: Color,
    compact: Boolean = false,
) {
    Surface(
        shape = CircleShape,
        color = accent.copy(alpha = 0.18f),
        modifier = Modifier.size(if (compact) 26.dp else 28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                ClassGlyphs.of(classKind),
                contentDescription = classKind?.label() ?: "Unmatched",
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                tint = accent,
            )
        }
    }
}

@Composable
fun RadioKindMark(
    kind: RadioKind,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Icon(
        if (kind == RadioKind.WIFI) Icons.Outlined.Wifi else Icons.Outlined.Bluetooth,
        contentDescription = if (kind == RadioKind.WIFI) "Wi-Fi access point" else "BLE advertiser",
        modifier = modifier.size(size),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
