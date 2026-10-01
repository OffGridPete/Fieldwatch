package app.fieldwatch.wear

import android.app.Application
import app.fieldwatch.wear.radio.WatchBleScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WearApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var scanner: WatchBleScanner
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        scanner = WatchBleScanner(this, scope)
    }

    companion object {
        lateinit var instance: WearApp
            private set
    }
}
