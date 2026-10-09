package app.fieldwatch.data

import android.content.Context
import app.fieldwatch.domain.GnssPhoneProfile
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** This phone's GNSS calibration. Not part of the settings pack. */
class GnssProfileStore(context: Context) {
    private val file = File(context.filesDir, "gnss-phone.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<GnssPhoneProfile?> = _profile.asStateFlow()

    fun save(profile: GnssPhoneProfile) {
        runCatching { file.writeText(json.encodeToString(profile)) }
        _profile.value = profile
    }

    private fun read(): GnssPhoneProfile? {
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<GnssPhoneProfile>(file.readText()) }.getOrNull()
    }
}
