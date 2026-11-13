package app.fieldwatch.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ASTM F3411 Authentication message, type nibble 2.
 * Page 0 carries the last page, the declared length, and the timestamp.
 * A later page replaces the type and the page and leaves that header in place.
 * The authentication bytes are not stored.
 */
@Serializable
data class RemoteIdAuth(
    val type: Int,
    val page: Int,
    val lastPage: Int? = null,
    val length: Int? = null,
    /** Seconds after 2019-01-01 00:00:00 UTC. 0 means not set. Null until a page 0 is seen. */
    val timestamp: Long? = null,
) {
    /** A page 0 replaces the header, including a timestamp of 0. A later page keeps it. */
    fun onto(prev: RemoteIdAuth?): RemoteIdAuth {
        if (page == 0 || prev == null) return this
        return copy(
            lastPage = prev.lastPage,
            length = prev.length,
            timestamp = prev.timestamp,
        )
    }

    fun typeName(): String = when (type) {
        0 -> LanguageText.text("rid.auth.none")
        1 -> LanguageText.text("rid.auth.uas")
        2 -> LanguageText.text("rid.auth.operator")
        3 -> LanguageText.text("rid.auth.set")
        4 -> LanguageText.text("rid.auth.network")
        5 -> LanguageText.text("rid.auth.specific")
        in 6..9 -> LanguageText.text("rid.auth.reserved", mapOf("n" to type.toString()))
        else -> LanguageText.text("rid.auth.private", mapOf("n" to type.toString()))
    }

    fun pageValue(): String = if (lastPage != null) {
        LanguageText.text(
            "rid.auth.page.value",
            mapOf("page" to page.toString(), "last" to lastPage.toString()),
        )
    } else {
        page.toString()
    }

    fun timeText(zone: ZoneId = ZoneId.systemDefault()): String? {
        val raw = timestamp ?: return null
        if (raw == 0L) return LanguageText.text("rid.auth.time.unset")
        val whenAt = Instant.ofEpochSecond(raw + EPOCH_2019).atZone(zone)
        return TIME.format(whenAt)
    }

    /** Declared length from page 0. Zero and a page that has not been seen stay blank. */
    fun dataLine(): String? {
        val n = length ?: return null
        if (n == 0) return null
        return LanguageText.text("rid.auth.present", mapOf("n" to n.toString()))
    }

    fun pastLine(): String? =
        if (lastPage != null && page > lastPage) LanguageText.text("rid.auth.past") else null

    /** Types 1–4 only. None, specific, reserved, and private stay off the live row. */
    fun chipLabel(): String? = if (type in 1..4) typeName() else null

    fun note(): String? = if (type != 0) LanguageText.text("rid.auth.note") else null

    private companion object {
        const val EPOCH_2019 = 1_546_300_800L
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
    }
}
