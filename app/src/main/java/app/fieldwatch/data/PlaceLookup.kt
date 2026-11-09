package app.fieldwatch.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import app.fieldwatch.domain.DebriefPlaces
import app.fieldwatch.domain.GpsSample
import app.fieldwatch.domain.LanguageText
import app.fieldwatch.domain.Rssi
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
    data class Fix(
        val label: String,
        val lat: Double,
        val lon: Double,
        val namedOnly: Boolean = false,
    )

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
        extra: List<Fix> = emptyList(),
        onProgress: ((String) -> Unit)? = null,
    ): DebriefPlaces = withContext(Dispatchers.IO) {
        runCatching { lookupInner(context, path, devices, now, extra, onProgress) }
            .getOrElse { DebriefPlaces(attempted = true, available = false, note = "Online lookup failed silently. Coordinates only.") }
    }

    /**
     * Street names for a few phone fixes. Empty when offline or the geocoder
     * has nothing. Callers keep the map and skip cells they already named.
     */
    suspend fun namesAt(context: Context, points: List<Pair<Double, Double>>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (points.isEmpty() || !online(context) || !Geocoder.isPresent()) return@withContext emptyMap()
            val geocoder = Geocoder(context, Locale.getDefault())
            val out = LinkedHashMap<String, String>()
            for ((lat, lon) in points) {
                if (out.size >= 8) break
                if (lat == 0.0 && lon == 0.0) continue
                val key = app.fieldwatch.domain.Geo.cellKey(lat, lon)
                if (key in out) continue
                val name = reverse(geocoder, lat, lon)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                out[key] = name
            }
            out
        }

    private suspend fun lookupInner(
        context: Context,
        path: List<GpsSample>,
        devices: List<Sighting>,
        now: Long,
        extra: List<Fix>,
        onProgress: ((String) -> Unit)?,
    ): DebriefPlaces {
        if (!online(context)) {
            return DebriefPlaces(
                attempted = true,
                available = false,
                note = "Online lookup skipped: no working internet. Coordinates only.",
            )
        }
        if (!Geocoder.isPresent()) {
            return DebriefPlaces(
                attempted = true,
                available = false,
                note = "Online lookup skipped: this phone has no system geocoder (needs Google Play / network location). Coordinates only.",
            )
        }
        val fixes = withExtra(collect(path, devices, now), extra)
        if (fixes.isEmpty()) {
            return DebriefPlaces(
                attempted = true,
                available = true,
                note = LanguageText.text("report.place.lookup.none"),
            )
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        val cache = LinkedHashMap<String, String>()
        val lines = ArrayList<String>(fixes.size)
        for ((i, fix) in fixes.withIndex()) {
            onProgress?.invoke(
                LanguageText.text(
                    "report.place.progress",
                    mapOf("i" to "${i + 1}", "n" to "${fixes.size}"),
                ),
            )
            val key = app.fieldwatch.domain.Geo.cellKey(fix.lat, fix.lon)
            val name = cache[key] ?: reverse(geocoder, fix.lat, fix.lon)?.also { cache[key] = it }
            if (!fix.namedOnly) {
                val coord = "%.5f, %.5f".format(Locale.US, fix.lat, fix.lon)
                val none = LanguageText.text("report.place.noname")
                lines += if (name.isNullOrBlank()) {
                    "  · ${fix.label}  $coord  $none"
                } else {
                    "  · ${fix.label}  $name  ($coord)"
                }
            }
        }
        val named = cache.size
        return DebriefPlaces(
            attempted = true,
            available = named > 0,
            note = if (named > 0) {
                LanguageText.text("report.place.lookup.named", mapOf("n" to "$named"))
            } else {
                LanguageText.text("report.place.lookup.empty")
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
                add(LanguageText.text("report.place.stay", mapOf("n" to "$stayN")), leg.lat, leg.lon)
            } else {
                add(LanguageText.text("report.place.from"), leg.lat, leg.lon)
                add(LanguageText.text("report.place.to"), leg.endLat, leg.endLon)
            }
        }
        if (out.isEmpty() && path.isNotEmpty()) {
            add(LanguageText.text("report.place.start"), path.first().lat, path.first().lon)
            if (path.size > 1) add(LanguageText.text("report.place.end"), path.last().lat, path.last().lon)
        }
        if (out.size < 8) {
            devices.filter { it.gpsTrail.isNotEmpty() && Rssi.measured(it.rssi) && it.rssi >= -70 }
                .sortedByDescending { it.rssi }
                .take(4)
                .forEach { d ->
                    val p = d.gpsTrail.last()
                    add(d.listTitle().take(28), p.lat, p.lon)
                }
        }
        return out.take(8)
    }

    /** Path cells first, then up to eight more from [extra] (place stops). */
    private fun withExtra(base: List<Fix>, extra: List<Fix>): List<Fix> {
        if (extra.isEmpty()) return base
        val out = ArrayList<Fix>(base.size + 8)
        val seen = HashSet<String>()
        for (fix in base) {
            out += fix
            seen += app.fieldwatch.domain.Geo.cellKey(fix.lat, fix.lon)
        }
        var added = 0
        for (fix in extra) {
            if (added >= 8) break
            if (fix.lat == 0.0 && fix.lon == 0.0) continue
            val key = app.fieldwatch.domain.Geo.cellKey(fix.lat, fix.lon)
            if (!seen.add(key)) continue
            out += fix
            added++
        }
        return out
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
