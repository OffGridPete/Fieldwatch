package app.fieldwatch.data

import app.fieldwatch.domain.AppUpdate
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException

/** The notes file and the APK on GitHub. No account. The notes file does not choose the address. */
object AppUpdateClient {
    const val NOTE_URL =
        "https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/dist/app-update.json"
    const val APK_URL =
        "https://raw.githubusercontent.com/OffGridPete/Fieldwatch/main/dist/Fieldwatch.apk"

    fun fetchNote(
        url: String = NOTE_URL,
        userAgent: String = "Fieldwatch",
        connectTimeoutMs: Int = 4_000,
        readTimeoutMs: Int = 4_000,
    ): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url, userAgent, connectTimeoutMs, readTimeoutMs, "application/json")
            val code = connection.responseCode
            if (code !in 200..299) return null
            val advertised = connection.contentLengthLong
            if (advertised == 0L || advertised > AppUpdate.MAX_NOTE_BYTES) return null
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(2048)
            var total = 0
            connection.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > AppUpdate.MAX_NOTE_BYTES) return null
                    out.write(buf, 0, n)
                }
            }
            if (out.size() == 0) return null
            return out.toString(Charsets.UTF_8.name())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Writes cache/update/Fieldwatch.apk. A partial file is removed.
     * [running] is checked between reads so Later or back can stop the download.
     */
    fun downloadApk(
        cacheDir: File,
        url: String = APK_URL,
        userAgent: String = "Fieldwatch",
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 20_000,
        running: () -> Boolean = { true },
    ): File? {
        val dir = File(cacheDir, "update").apply { mkdirs() }
        val part = File(dir, "Fieldwatch.apk.part")
        val dest = File(dir, "Fieldwatch.apk")
        if (part.exists() && !part.delete()) return null
        var connection: HttpURLConnection? = null
        try {
            if (!running()) return null
            connection = open(url, userAgent, connectTimeoutMs, readTimeoutMs, "*/*")
            val code = connection.responseCode
            if (code !in 200..299) return null
            val advertised = connection.contentLengthLong
            if (advertised == 0L || advertised > AppUpdate.MAX_APK_BYTES) return null
            val magic = ByteArray(4)
            var magicGot = 0
            var total = 0L
            part.outputStream().use { out ->
                connection.inputStream.use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        if (!running()) return null
                        val n = input.read(buf)
                        if (n < 0) break
                        if (magicGot < 4) {
                            val take = minOf(4 - magicGot, n)
                            System.arraycopy(buf, 0, magic, magicGot, take)
                            magicGot += take
                            if (magicGot >= 4 && !AppUpdate.apkLooksLikeZip(magic)) return null
                        }
                        total += n
                        if (total > AppUpdate.MAX_APK_BYTES) return null
                        out.write(buf, 0, n)
                    }
                }
            }
            if (magicGot < 4 || !AppUpdate.apkLooksLikeZip(magic) || total < 4L) return null
            if (dest.exists() && !dest.delete()) return null
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                if (!part.delete()) return null
            }
            return dest.takeIf { it.isFile && it.length() == total }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            connection?.disconnect()
            if (part.exists()) part.delete()
        }
    }

    private fun open(
        url: String,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        accept: String,
    ): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            useCaches = false
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = "GET"
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("User-Agent", userAgent)
        }
    }
}
