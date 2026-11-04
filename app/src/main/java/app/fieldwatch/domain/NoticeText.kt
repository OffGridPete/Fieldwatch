package app.fieldwatch.domain

/**
 * Display split for a warning or a release note.
 * Blank lines start a paragraph. The words stay as written.
 */
object NoticeText {
    data class Row(val label: String?, val value: String)

    fun paragraphs(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return emptyList()
        return Regex("""\n[ \t]*\n""")
            .split(normalized)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /** "Label: value" is a row. A line with no colon-space, such as a MAC, stays one value. */
    fun rows(text: String): List<Row> {
        return text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val cut = line.indexOf(": ")
                if (cut in 1..48) Row(line.substring(0, cut), line.substring(cut + 2))
                else Row(null, line)
            }
    }
}
