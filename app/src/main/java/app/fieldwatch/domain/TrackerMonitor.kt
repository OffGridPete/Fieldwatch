package app.fieldwatch.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One warning at a time. Dismiss, or opening the radio, keeps that radio quiet
 * while the same condition continues. The next radio waits.
 */
class TrackerMonitor {
    private val lock = Any()
    private var episodes = emptyList<TrackerWatch.Episode>()
    private val pending = ArrayDeque<TrackerWatch.Notice>()
    private val _notice = MutableStateFlow<TrackerWatch.Notice?>(null)
    val notice: StateFlow<TrackerWatch.Notice?> = _notice.asStateFlow()

    /**
     * Returns the warning that just became the one on screen, so the caller can
     * post the quiet notification once.
     */
    fun hear(
        hears: List<TrackerWatch.Hear>,
        mine: Set<String>,
        lat: Double?,
        lon: Double?,
        now: Long,
        followOn: Boolean,
        placesOn: Boolean,
        placeFresh: List<TrackerWatch.Notice>,
    ): TrackerWatch.Notice? = synchronized(lock) {
        val before = _notice.value?.id
        if (!followOn) {
            episodes = emptyList()
            pending.removeAll { it.kind == TrackerWatch.Kind.FOLLOW }
            if (_notice.value?.kind == TrackerWatch.Kind.FOLLOW) _notice.value = null
        } else {
            val follow = TrackerWatch.follow(episodes, hears, mine, lat, lon, now, enabled = true)
            episodes = follow.episodes
            val live = episodes.map { it.id }.toSet()
            pending.removeAll { it.kind == TrackerWatch.Kind.FOLLOW && it.id !in live }
            if (_notice.value?.kind == TrackerWatch.Kind.FOLLOW && _notice.value?.id !in live) {
                _notice.value = null
            }
            val places = if (placesOn) placeFresh else emptyList()
            enqueue(TrackerWatch.merge(follow.ready, places))
        }
        if (!placesOn) {
            pending.removeAll { it.kind == TrackerWatch.Kind.PLACES }
            if (_notice.value?.kind == TrackerWatch.Kind.PLACES) _notice.value = null
        } else if (!followOn) {
            enqueue(placeFresh)
        }
        promote()
        val shown = _notice.value
        if (shown != null && shown.id != before) shown else null
    }

    /** Dismiss the warning on screen. The next waiting radio comes up. */
    fun dismissCurrent(): TrackerWatch.Notice? = synchronized(lock) {
        val current = _notice.value ?: return null
        markSeen(current)
        _notice.value = null
        promote()
    }

    /**
     * Opening this radio from the warning counts as seeing it.
     * A different radio, including a watchlist tap, leaves the warning up.
     */
    fun seenDevice(deviceKey: String): TrackerWatch.Notice? = synchronized(lock) {
        val current = _notice.value
        val waiting = pending.any { it.deviceKey == deviceKey }
        if (current?.deviceKey != deviceKey && !waiting) return null
        episodes = episodes.map { if (it.deviceKey == deviceKey) it.copy(seen = true) else it }
        pending.removeAll { it.deviceKey == deviceKey }
        if (current?.deviceKey != deviceKey) return null
        _notice.value = null
        promote()
    }

    fun clear() = synchronized(lock) {
        episodes = emptyList()
        pending.clear()
        _notice.value = null
    }

    private fun markSeen(notice: TrackerWatch.Notice) {
        pending.removeAll { it.id == notice.id }
        episodes = episodes.map { if (it.id == notice.id) it.copy(seen = true) else it }
    }

    private fun enqueue(notices: List<TrackerWatch.Notice>) {
        val have = HashSet<String>()
        _notice.value?.id?.let { have += it }
        pending.forEach { have += it.id }
        for (notice in notices) {
            if (!have.add(notice.id)) continue
            pending.add(notice)
        }
    }

    private fun promote(): TrackerWatch.Notice? {
        if (_notice.value == null) _notice.value = pending.removeFirstOrNull()
        return _notice.value
    }
}
