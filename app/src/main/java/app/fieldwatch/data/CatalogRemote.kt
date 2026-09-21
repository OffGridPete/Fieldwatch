package app.fieldwatch.data

import java.net.HttpURLConnection
import java.net.URL

/** HTTPS GET of the stock signature pack on GitHub. No account. Fails to the caller. */
object CatalogRemote {
    const val STOCK_PACK_URL =
        "https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/dist/fieldwatch-signatures.json"

    fun fetch(
        url: String = STOCK_PACK_URL,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000,
        userAgent: String = "Fieldwatch",
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("GitHub returned HTTP $code")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
