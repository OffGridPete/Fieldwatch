package app.fieldwatch.data

import app.fieldwatch.i18n.localized

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.Sighting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Optional Debrief enrichment: system reverse-geocoder only (no API key, no
 * cloud account). Never throws to the caller; offline or missing geocoder
 * becomes a note in the report.
 */
object PlaceLookup {
    data class Fix(val label: String, val lat: Double, val lon: Double)

    fun online(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun lookup(
        context: Context,
        path: List<GpsSample>,
        devices: List<Sighting>,
        now: Long = System.currentTimeMillis(),
        onProgress: ((String) -> Unit)? = null,
    ): DebriefPlaces = withContext(Dispatchers.IO) {
        runCatching { lookupInner(context, path, devices, now, onProgress) }
            .getOrElse { DebriefPlaces(attempted = true, available = false, note = localized("place_lookup_online_lookup_failed_silently_coordinates_only", "Online lookup failed silently. Coordinates only.")) }
    }

    private suspend fun lookupInner(
        context: Context,
        path: List<GpsSample>,
        devices: List<Sighting>,
        now: Long,
        onProgress: ((String) -> Unit)?,
    ): DebriefPlaces {
        if (!online(context)) {
            return DebriefPlaces(
                attempted = true,
                available = false,
                note = localized("place_lookup_online_lookup_skipped_no_working_internet_coordinates", "Online lookup skipped: no working internet. Coordinates only."),
            )
        }
        if (!Geocoder.isPresent()) {
            return DebriefPlaces(
                attempted = true,
                available = false,
                note = localized("place_lookup_online_lookup_skipped_this_phone_has_no", "Online lookup skipped: this phone has no system geocoder (needs Google Play / network location). Coordinates only."),
            )
        }
        val fixes = collect(path, devices, now)
        if (fixes.isEmpty()) {
            return DebriefPlaces(
                attempted = true,
                available = true,
                note = localized("place_lookup_online_lookup_on_but_this_sit_had", "Online lookup on, but this sit had no GPS stamps to name."),
            )
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        val cache = LinkedHashMap<String, String>()
        val lines = ArrayList<String>(fixes.size)
        for ((i, fix) in fixes.withIndex()) {
            onProgress?.invoke(localized("place_lookup_looking_up_place_names_of", "Looking up place names (%1\$s of %2\$s)…", i + 1, fixes.size))
            val key = app.fieldwatch.domain.Geo.cellKey(fix.lat, fix.lon)
            val name = cache[key] ?: reverse(geocoder, fix.lat, fix.lon)?.also { cache[key] = it }
            val coord = "%.5f, %.5f".format(Locale.US, fix.lat, fix.lon)
            lines += if (name.isNullOrBlank()) {
                localized("place_lookup_no_name_returned", "  · %1\$s  %2\$s  (no name returned)", fix.label, coord)
            } else {
                "  · ${fix.label}  $name  ($coord)"
            }
        }
        val named = cache.size
        return DebriefPlaces(
            attempted = true,
            available = named > 0,
            note = if (named > 0) {
                localized("place_lookup_online_lookup_system_geocoder_named_distinct_gps", "Online lookup: system geocoder named %1\$s distinct GPS cell(s). Street names are from the phone’s network geocoder, not a Fieldwatch cloud. Approximate.", named)
            } else {
                localized("place_lookup_online_lookup_ran_but_the_system_geocoder", "Online lookup ran, but the system geocoder returned no street names. Coordinates only.")
            },
            lines = lines,
            namesByCell = cache.toMap(),
        )
    }

    fun collect(path: List<GpsSample>, devices: List<Sighting>, now: Long = System.currentTimeMillis()): List<Fix> {
        val out = ArrayList<Fix>(8)
        val seen = HashSet<String>()
        fun add(label: String, lat: Double, lon: Double) {
            if (lat == 0.0 && lon == 0.0) return
            val key = app.fieldwatch.domain.Geo.cellKey(lat, lon)
            if (!seen.add(key)) return
            out += Fix(label, lat, lon)
        }
        val legs = app.fieldwatch.domain.Geo.legs(path, now = now)
        var stayN = 0
        legs.forEach { leg ->
            if (leg.stay) {
                stayN++
                add(localized("place_lookup_stay", "Stay %1\$s", stayN), leg.lat, leg.lon)
            } else {
                add(localized("place_lookup_transit_from", "Transit from"), leg.lat, leg.lon)
                add(localized("place_lookup_transit_to", "Transit to"), leg.endLat, leg.endLon)
            }
        }
        if (out.isEmpty() && path.isNotEmpty()) {
            add(localized("place_lookup_path_start", "Path start"), path.first().lat, path.first().lon)
            if (path.size > 1) add(localized("place_lookup_path_end", "Path end"), path.last().lat, path.last().lon)
        }
        if (out.size < 8) {
            devices.filter { it.gpsTrail.isNotEmpty() && it.rssi >= -70 }
                .sortedByDescending { it.rssi }
                .take(4)
                .forEach { d ->
                    val p = d.gpsTrail.last()
                    add(d.listTitle().take(28), p.lat, p.lon)
                }
        }
        return out.take(8)
    }

    private suspend fun reverse(geocoder: Geocoder, lat: Double, lon: Double): String? {
        val async = withTimeoutOrNull(8_000L) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(
                            lat, lon, 1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (cont.isActive) cont.resume(format(addresses.firstOrNull()))
                                }
                                override fun onError(errorMessage: String?) {
                                    if (cont.isActive) cont.resume(null)
                                }
                            },
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    format(geocoder.getFromLocation(lat, lon, 1)?.firstOrNull())
                }
            }.getOrNull()
        }
        if (!async.isNullOrBlank()) return async
        return runCatching {
            @Suppress("DEPRECATION")
            format(geocoder.getFromLocation(lat, lon, 1)?.firstOrNull())
        }.getOrNull()
    }

    private fun format(addr: Address?): String? {
        if (addr == null) return null
        addr.getAddressLine(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val parts = listOfNotNull(
            listOfNotNull(addr.subThoroughfare, addr.thoroughfare).joinToString(" ").ifBlank { null },
            addr.locality,
            addr.adminArea,
            addr.postalCode,
        )
        return parts.joinToString(", ").ifBlank { null }
    }
}
