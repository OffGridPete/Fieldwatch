package app.fieldwatch.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.fieldwatch.wear.data.WearStateRepository
import app.fieldwatch.wear.model.HuntUpdatePayload
import app.fieldwatch.wear.model.WearPaths
import app.fieldwatch.wear.ui.screens.AlertsScreen
import app.fieldwatch.wear.ui.screens.DashboardScreen
import app.fieldwatch.wear.ui.screens.WristHuntScreen
import app.fieldwatch.wear.ui.theme.FieldwatchWearTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets

class WearMainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions evaluated */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions for standalone BLE scanning & location
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )

        setContent {
            FieldwatchWearTheme {
                val navController = rememberSwipeDismissableNavController()

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "dashboard"
                ) {
                    composable("dashboard") {
                        DashboardScreen(
                            onNavigateToRadar = { navController.navigate("radar") },
                            onNavigateToAlerts = { navController.navigate("alerts") },
                            onNavigateToHunt = { navController.navigate("hunt") },
                            onTriggerSweep = {
                                WearApp.instance.scanner.startBurstSweep()
                            }
                        )
                    }

                    composable("radar") {
                        app.fieldwatch.wear.ui.screens.ClassicRadarScreen(
                            onSelectHuntTarget = { contact ->
                                WearStateRepository.updateHunt(
                                    HuntUpdatePayload(
                                        targetKey = contact.key,
                                        targetName = contact.name,
                                        rssi = contact.rssi,
                                    )
                                )
                                sendCommandToPhone(WearPaths.CMD_START_HUNT, contact.key)
                                navController.navigate("hunt")
                            }
                        )
                    }

                    composable("alerts") {
                        AlertsScreen(
                            onSelectHuntTarget = { alert ->
                                // Set hunt target locally & send to phone
                                WearStateRepository.updateHunt(
                                    HuntUpdatePayload(
                                        targetKey = alert.key,
                                        targetName = alert.name,
                                        rssi = alert.rssi,
                                    )
                                )
                                sendCommandToPhone(WearPaths.CMD_START_HUNT, alert.key)
                                navController.navigate("hunt")
                            }
                        )
                    }

                    composable("hunt") {
                        WristHuntScreen(
                            onStopHunt = {
                                WearStateRepository.updateHunt(null)
                                sendCommandToPhone(WearPaths.CMD_STOP_HUNT, "")
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun sendCommandToPhone(path: String, payload: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val nodes = Wearable.getNodeClient(this@WearMainActivity).connectedNodes.await()
                val data = payload.toByteArray(StandardCharsets.UTF_8)
                for (node in nodes) {
                    Wearable.getMessageClient(this@WearMainActivity).sendMessage(node.id, path, data)
                }
            }
        }
    }
}
