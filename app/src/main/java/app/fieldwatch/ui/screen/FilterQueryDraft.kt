package app.fieldwatch.ui.screen

/**
 * Text shown in a filter field while each keystroke is still being saved.
 * A late copy of an older keystroke is ignored. A reset or a preset replaces the text.
 */
internal class FilterQueryDraft(initial: String) {
    var text: String = initial
        private set

    private var seen: String = initial
    private val pending = ArrayDeque<String>()
    private val stale = ArrayDeque<String>()

    /** @return true when [next] is a new edit that should be saved. */
    fun edit(next: String): Boolean {
        if (next == text) return false
        text = next
        pending.addLast(next)
        return true
    }

    fun observe(saved: String) {
        if (saved == seen) return
        seen = saved
        when {
            pending.firstOrNull() == saved -> pending.removeFirst()
            saved in pending -> {
                while (pending.first() != saved) pending.removeFirst()
                pending.removeFirst()
            }
            saved in stale -> stale.remove(saved)
            else -> {
                stale.addAll(pending)
                pending.clear()
                text = saved
            }
        }
    }
}
