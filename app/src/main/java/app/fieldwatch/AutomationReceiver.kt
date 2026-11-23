package app.fieldwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.fieldwatch.domain.Automation

/** Forwards a public action to [AutomationService]. A service intent is the sure path. */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (Automation.action(action) == null) return
        val start = Intent(context, AutomationService::class.java).setAction(action)
        intent.extras?.let { start.putExtras(it) }
        runCatching { ContextCompat.startForegroundService(context, start) }
    }
}
