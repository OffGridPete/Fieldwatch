package app.fieldwatch.domain

/**
 * Spreadsheet CSV text. A cell whose first non-space character is = + - or @
 * is prefixed so Excel and Sheets keep it as text. A plain number, including
 * a negative coordinate, is left alone.
 */
object CsvCells {
    private val NUMBER = Regex("-?\\d+(?:\\.\\d+)?")

    fun quote(s: String): String {
        val body = guard(s)
        return if (body.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${body.replace("\"", "\"\"")}\""
        } else {
            body
        }
    }

    /** Log rows that flatten commas instead of quoting. */
    fun plain(s: String): String =
        guard(s.replace(',', ' ').replace('\n', ' ').replace('\r', ' ').replace('"', ' '))

    private fun guard(s: String): String = if (formula(s)) "'$s" else s

    private fun formula(s: String): Boolean {
        val i = s.indexOfFirst { !it.isWhitespace() }
        if (i < 0) return false
        if (s[i] !in "=+-@") return false
        return !NUMBER.matches(s.substring(i))
    }
}
