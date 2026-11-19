package app.fieldwatch.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/**
 * HTTPS GET of the stock signature pack on GitHub. No account.
 * Phones on 1.1.44 still fetch dist/fieldwatch-signatures-v2.json.
 * This build fetches the RSI file. That file is not on GitHub until it is pushed.
 */
object CatalogRemote {
    const val STOCK_PACK_URL =
        "https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/dist/fieldwatch-signatures.rsi"

    enum class Reason {
        REACH, TRUST, SERVER, LARGE, EMPTY, OTHER,
    }

    sealed class Fetch {
        data class Ok(val text: String) : Fetch()
        data class Failed(val reason: Reason, val code: Int = 0) : Fetch()
    }

    fun failReason(error: Exception): Reason = when (error) {
        is SSLException -> Reason.TRUST
        is UnknownHostException, is SocketTimeoutException, is SocketException -> Reason.REACH
        else -> Reason.OTHER
    }

    fun fetch(
        url: String = STOCK_PACK_URL,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 15_000,
        userAgent: String = "Fieldwatch",
        maxBytes: Int = ImportFiles.SIGNATURE_MAX,
    ): Fetch {
        if (url.isBlank()) return Fetch.Failed(Reason.OTHER)
        var connection: HttpURLConnection? = null
        try {
            val opened = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", userAgent)
            }
            connection = opened
            val code = opened.responseCode
            if (code !in 200..299) return Fetch.Failed(Reason.SERVER, code)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            opened.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) return Fetch.Failed(Reason.LARGE)
                    out.write(buf, 0, n)
                }
            }
            if (out.size() == 0) return Fetch.Failed(Reason.EMPTY)
            return Fetch.Ok(out.toString(Charsets.UTF_8.name()))
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            return Fetch.Failed(failReason(error))
        } finally {
            connection?.disconnect()
        }
    }
}
