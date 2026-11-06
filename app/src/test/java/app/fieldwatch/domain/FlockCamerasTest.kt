package app.fieldwatch.domain

import app.fieldwatch.data.FlockFiles
import app.fieldwatch.data.SensorList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import kotlin.math.floor
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.net.ssl.SSLHandshakeException

class FlockCamerasTest {
    @Test
    fun parseKeepsEveryPlateReaderAndDropsBadPoints() {
        val parsed = parseFlock(StringReader(FIXTURE))
        assertEquals(listOf(10L, 11L, -12L, 13L), parsed.map { it.key })
        assertEquals(listOf(90), parsed.first { it.key == 10L }.facings)
        assertTrue(parsed.first { it.key == 10L }.flock)
        assertEquals(SensorKind.PLATE, parsed.first { it.key == 10L }.kind)
        assertEquals(SensorKind.PLATE, parsed.first { it.key == 11L }.kind)
        val vigilant = parsed.first { it.key == 11L }
        assertEquals("Vigilant", vigilant.brand)
        assertFalse(vigilant.flock)
        assertTrue(vigilant.facings.isEmpty())
        val way = parsed.first { it.key == -12L }
        assertEquals(listOf(0, 180, 270, 45), way.facings)
        assertTrue(way.flock)
        assertEquals("flock safety", way.brand)
        assertTrue(parsed.first { it.key == 13L }.facings.isEmpty())
        assertEquals(39.74, parsed.first().lat, 0.000001)
        assertEquals(-104.99, parsed.first().lon, 0.000001)
    }

    @Test
    fun truncatedJsonThrows() {
        var threw = false
        try {
            parseFlock(StringReader("""{"type":"FeatureCollection","features":["""))
        } catch (_: FlockParseException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun gzipBytesParseTheSameCameras() {
        val raw = FIXTURE.toByteArray()
        val zipped = java.io.ByteArrayOutputStream().use { sink ->
            GZIPOutputStream(sink).use { it.write(raw) }
            sink.toByteArray()
        }
        assertEquals(0x1f, zipped[0].toInt() and 0xff)
        assertEquals(parseFlock(StringReader(FIXTURE)).map { it.key }, FlockFiles.camerasFrom(zipped).map { it.key })
        val dir = tempDir()
        assertFalse(FlockFiles.commitParsed(dir, zipped, "etag", 10L))
        assertFalse(FlockFiles.file(dir).exists())
    }

    @Test
    fun aheadAndLookingUseCourseFacingAndSpeed() {
        val userLat = 39.0
        val userLon = -104.0
        val north = FlockApproach.destination(userLat, userLon, 0.0, 100.0)
        val ahead = FlockApproach.classify(
            userLat, userLon, 0.0, 2.0, north.first, north.second, listOf(180),
        )
        assertEquals(FlockApproach.Kind.LOOKING, ahead)
        val unknown = FlockApproach.classify(
            userLat, userLon, 0.0, 2.0, north.first, north.second, emptyList(),
        )
        assertEquals(FlockApproach.Kind.AHEAD, unknown)
        val away = FlockApproach.classify(
            userLat, userLon, 0.0, 2.0, north.first, north.second, listOf(0),
        )
        assertNull(away)
        val behind = FlockApproach.destination(userLat, userLon, 180.0, 100.0)
        assertNull(
            FlockApproach.classify(
                userLat, userLon, 0.0, 2.0, behind.first, behind.second, emptyList(),
            ),
        )
        val side = FlockApproach.destination(userLat, userLon, 50.0, 100.0)
        assertNull(
            FlockApproach.classify(
                userLat, userLon, 0.0, 2.0, side.first, side.second, emptyList(),
            ),
        )
        val early = FlockApproach.destination(userLat, userLon, 0.0, 400.0)
        assertEquals(
            FlockApproach.Kind.AHEAD,
            FlockApproach.classify(
                userLat, userLon, 0.0, 2.0, early.first, early.second, emptyList(),
            ),
        )
        val far = FlockApproach.destination(userLat, userLon, 0.0, 650.0)
        assertNull(
            FlockApproach.classify(
                userLat, userLon, 0.0, 2.0, far.first, far.second, emptyList(),
            ),
        )
        assertNull(
            FlockApproach.classify(
                userLat, userLon, 0.0, 1.5, north.first, north.second, emptyList(),
            ),
        )
        assertNull(
            FlockApproach.classify(
                userLat, userLon, Double.NaN, 3.0, north.first, north.second, emptyList(),
            ),
        )
    }

    @Test
    fun latchFiresOnceUntilThePhoneLeavesAndReturns() {
        val userLat = 39.0
        val userLon = -104.0
        val at = FlockApproach.destination(userLat, userLon, 0.0, 100.0)
        val cam = FlockCamera(7L, at.first, at.second, listOf(180))
        val latch = FlockLatch()
        val first = latch.consider(userLat, userLon, 0.0, 3.0, listOf(cam))
        assertEquals(FlockHit(7L, true), first)
        assertNull(latch.consider(userLat, userLon, 0.0, 3.0, listOf(cam)))
        val held = FlockApproach.destination(userLat, userLon, 180.0, 150.0)
        assertNull(latch.consider(held.first, held.second, 180.0, 3.0, listOf(cam)))
        assertNull(latch.consider(userLat, userLon, 0.0, 3.0, listOf(cam)))
        val gone = FlockApproach.destination(userLat, userLon, 180.0, 1_200.0)
        assertNull(latch.consider(gone.first, gone.second, 180.0, 3.0, listOf(cam)))
        val again = latch.consider(userLat, userLon, 0.0, 3.0, listOf(cam))
        assertEquals(FlockHit(7L, true), again)
    }

    @Test
    fun stoppedDoesNotAlertAndDoesNotRearmACloseCamera() {
        val userLat = 39.0
        val userLon = -104.0
        val at = FlockApproach.destination(userLat, userLon, 0.0, 80.0)
        val cam = FlockCamera(4L, at.first, at.second, emptyList())
        val latch = FlockLatch()
        assertEquals(FlockHit(4L, false), latch.consider(userLat, userLon, 0.0, 3.0, listOf(cam)))
        assertNull(latch.consider(userLat, userLon, 0.0, 0.0, listOf(cam)))
        assertNull(latch.consider(userLat, userLon, 0.0, 3.0, listOf(cam)))
    }

    @Test
    fun compactFileRoundTripsAndAShortParseDoesNotReplaceIt() {
        val dir = tempDir()
        val cameras = List(FlockApproach.MIN_COUNT) { i ->
            FlockCamera(
                key = i + 1L,
                lat = 25.0 + (i % 200) * 0.01,
                lon = -120.0 + (i / 200) * 0.01,
                facings = when {
                    i % 5 == 0 -> listOf(90, 270)
                    i == 3 -> listOf(12)
                    else -> emptyList()
                },
                brand = if (i == 0) "Flock" else if (i == 1) "Vigilant" else "",
                flock = i == 0,
                kind = when (i) {
                    2 -> SensorKind.SPEED
                    3 -> SensorKind.GUNSHOT
                    else -> SensorKind.PLATE
                },
            )
        }
        assertTrue(FlockFiles.install(dir, cameras, "\"abc\"", 1_700_000_000_000L))
        val saved = FlockFiles.read(dir)
        assertEquals(FlockApproach.MIN_COUNT, saved!!.cameras.size)
        assertEquals(cameras.first().key, saved.cameras.first().key)
        assertEquals(cameras.first().facings, saved.cameras.first().facings)
        assertEquals(cameras.first().lat, saved.cameras.first().lat, 0.000002)
        assertEquals("\"abc\"", saved.header.etag)
        assertEquals(1_700_000_000_000L, saved.header.savedAt)
        assertEquals(FlockFiles.FORMAT, saved.header.version)
        assertEquals("Flock", saved.cameras.first().brand)
        assertTrue(saved.cameras.first().flock)
        assertEquals("Vigilant", saved.cameras.first { it.key == 2L }.brand)
        assertFalse(saved.cameras.first { it.key == 2L }.flock)
        assertEquals(SensorKind.PLATE, saved.cameras.first().kind)
        assertEquals(SensorKind.SPEED, saved.cameras.first { it.key == 3L }.kind)
        assertEquals(SensorKind.GUNSHOT, saved.cameras.first { it.key == 4L }.kind)
        assertTrue(saved.cameras.first { it.key == 4L }.facings.isEmpty())
        val before = FlockFiles.file(dir).readBytes()
        assertFalse(FlockFiles.commitParsed(dir, """{"type":"FeatureCollection","features":[]}""".toByteArray(), "new", 9L))
        assertFalse(FlockFiles.commitParsed(dir, byteArrayOf('{'.code.toByte()), "new", 9L))
        assertTrue(before.contentEquals(FlockFiles.file(dir).readBytes()))
        assertFalse(FlockFiles.install(dir, cameras.take(3), "nope", 2L))
        assertEquals("\"abc\"", FlockFiles.peek(dir)!!.etag)
    }

    @Test
    fun aCrowdBecomesACount() {
        val index = FlockIndex()
        val cameras = List(300) { i ->
            FlockCamera(i + 1L, 39.0 + i * 0.00001, -104.0 + (i % 10) * 0.00001, listOf(10))
        }
        index.replace(cameras)
        val marks = index.marks(
            38.9, 39.2, -104.1, -103.9,
            project = { lat, lon ->
                ((lon + 104.0) * 100_000f).toFloat() to ((lat - 39.0) * 100_000f).toFloat()
            },
            inside = { _, _ -> true },
        )
        assertTrue(marks.size < 300)
        assertTrue(marks.size <= FlockApproach.MARK_CAP)
        assertEquals(300, marks.sumOf { it.count })
        assertTrue(marks.any { it.count > 1 })
        val spaced = FlockIndex()
        spaced.replace(
            listOf(
                FlockCamera(1L, 39.0, -104.0, listOf(15)),
                FlockCamera(2L, 40.0, -105.0, emptyList()),
                FlockCamera(3L, 41.0, -106.0, listOf(200)),
            ),
        )
        val three = spaced.marks(
            38.0, 42.0, -107.0, -103.0,
            project = { lat, lon -> lat.toFloat() * 100f to lon.toFloat() * 100f },
            inside = { _, _ -> true },
        )
        assertEquals(3, three.size)
        assertEquals(listOf(15), three.first { it.lat == 39.0 }.facings)
    }

    @Test
    fun aSpeedCameraStaysVisibleAmongPlateReaders() {
        val index = FlockIndex()
        val plates = List(300) { i ->
            FlockCamera(
                i + 1L,
                39.0 + (i % 20) * 0.00001,
                -104.0 + (i / 20) * 0.00001,
                listOf(10),
                flock = true,
            )
        }
        val speed = FlockCamera(9_000L, 39.00005, -104.00005, listOf(90), kind = SensorKind.SPEED)
        index.replace(plates + speed)
        val marks = index.marks(
            38.9, 39.2, -104.1, -103.9,
            project = { lat, lon ->
                ((lon + 104.0) * 100_000f).toFloat() to ((lat - 39.0) * 100_000f).toFloat()
            },
            inside = { _, _ -> true },
        )
        val speeds = marks.filter { it.kind == SensorKind.SPEED }
        assertEquals(1, speeds.size)
        assertEquals(1, speeds.single().count)
        assertEquals(listOf(90), speeds.single().facings)
        assertEquals(300, marks.filter { it.kind == SensorKind.PLATE }.sumOf { it.count })
    }

    @Test
    fun aWideViewKeepsEachType() {
        val index = FlockIndex()
        val plates = List(8_001) { i ->
            FlockCamera(i + 1L, 39.0 + (i % 40) * 0.0001, -104.0, emptyList(), flock = true)
        }
        val speed = FlockCamera(90_000L, 39.001, -104.0, listOf(10), kind = SensorKind.SPEED)
        index.replace(plates + speed)
        val marks = index.marks(
            38.9, 39.2, -104.1, -103.9,
            project = { lat, lon -> lat.toFloat() to lon.toFloat() },
            inside = { _, _ -> true },
        )
        val speeds = marks.filter { it.kind == SensorKind.SPEED }
        assertEquals(1, speeds.size)
        assertEquals(1, speeds.single().count)
        assertEquals(8_001, marks.filter { it.kind == SensorKind.PLATE }.sumOf { it.count })
    }

    @Test
    fun tagsPickTheKindAndAWordBearingIsIgnored() {
        val parsed = parseFlock(StringReader(KINDS))
        val speed = parsed.first { it.key == 20L }
        assertEquals(SensorKind.SPEED, speed.kind)
        assertEquals(listOf(90, 180), speed.facings)
        val relation = sensorKey(21L, "relation")
        assertEquals(SensorKind.RED, parsed.first { it.key == relation }.kind)
        val gun = parsed.first { it.key == 22L }
        assertEquals(SensorKind.GUNSHOT, gun.kind)
        assertTrue(gun.facings.isEmpty())
        assertEquals(listOf(270), parsed.first { it.key == 23L }.facings)
        assertEquals(SensorKind.CAMERA, parsed.first { it.key == 23L }.kind)
        assertEquals(SensorKind.SPEED, parsed.first { it.key == 24L }.kind)
        assertEquals(SensorKind.PLATE, parsed.first { it.key == 25L }.kind)
        assertEquals(SensorKind.CAMERA, parsed.first { it.key == 27L }.kind)
        assertTrue(parsed.none { it.key == 26L })
        assertEquals(SensorKind.RED, parsed.first { it.key == 29L }.kind)
    }

    @Test
    fun publishedSensorListKeepsTheFourTypes() {
        val file = listOf(
            File("dist/fieldwatch-sensors.geojson.gz"),
            File("../dist/fieldwatch-sensors.geojson.gz"),
        ).first { it.isFile }
        val parsed = GZIPInputStream(FileInputStream(file)).use { stream ->
            parseFlock(InputStreamReader(stream, Charsets.UTF_8))
        }
        assertEquals(40496, parsed.size)
        assertEquals(1828, parsed.count { it.kind == SensorKind.SPEED })
        assertEquals(682, parsed.count { it.kind == SensorKind.RED })
        assertEquals(4019, parsed.count { it.kind == SensorKind.GUNSHOT })
        assertEquals(33967, parsed.count { it.kind == SensorKind.CAMERA })
        assertTrue(parsed.none { it.kind == SensorKind.PLATE })
    }

    @Test
    fun plateAndOtherListsStayInSeparateFiles() {
        val dir = tempDir()
        val plates = List(FlockApproach.MIN_COUNT) { i ->
            FlockCamera(i + 1L, 30.0, -100.0, emptyList(), kind = SensorKind.PLATE, flock = true, brand = "Flock")
        }
        val other = List(FlockApproach.MIN_COUNT) { i ->
            FlockCamera(10_000_000L + i, 31.0, -101.0, listOf(20), kind = SensorKind.SPEED)
        }
        assertTrue(FlockFiles.install(dir, plates, "plates", 1L, SensorList.PLATES))
        assertTrue(FlockFiles.install(dir, other, "other", 2L, SensorList.OTHER))
        val savedPlates = FlockFiles.read(dir, SensorList.PLATES)!!
        val savedOther = FlockFiles.read(dir, SensorList.OTHER)!!
        assertEquals(SensorKind.PLATE, savedPlates.cameras.first().kind)
        assertEquals("plates", savedPlates.header.etag)
        assertEquals(SensorKind.SPEED, savedOther.cameras.first().kind)
        assertEquals("other", savedOther.header.etag)
        assertTrue(savedOther.cameras.none { it.kind == SensorKind.PLATE })
    }

    @Test
    fun aFailedDownloadNamesTheCause() {
        assertEquals(FlockFiles.Reason.REACH, FlockFiles.failReason(UnknownHostException("list")))
        assertEquals(FlockFiles.Reason.REACH, FlockFiles.failReason(SocketTimeoutException("timed out")))
        assertEquals(FlockFiles.Reason.TRUST, FlockFiles.failReason(SSLHandshakeException("bad cert")))
        assertEquals(FlockFiles.Reason.OTHER, FlockFiles.failReason(IllegalStateException("other")))
    }

    @Test
    fun aBrokenCameraFileReadsAsNothing() {
        val dir = tempDir()
        val junk = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        FlockFiles.file(dir, SensorList.PLATES).writeBytes(junk)
        FlockFiles.file(dir, SensorList.OTHER).writeBytes(junk)
        assertNull(FlockFiles.peek(dir, SensorList.PLATES))
        assertNull(FlockFiles.read(dir, SensorList.PLATES))
        assertNull(FlockFiles.peek(dir, SensorList.OTHER))
        assertNull(FlockFiles.read(dir, SensorList.OTHER))
    }

    @Test
    fun aBadDownloadDoesNotReplaceAGoodFile() {
        val dir = tempDir()
        val raw = platesJson(FlockApproach.MIN_COUNT)
        assertTrue(FlockFiles.commitParsed(dir, raw.toByteArray(), "e", 3L))
        val saved = FlockFiles.read(dir)!!
        assertEquals(SensorKind.PLATE, saved.cameras.first { it.key == 1L }.kind)
        val before = FlockFiles.file(dir).readBytes()
        assertFalse(FlockFiles.commitParsed(dir, byteArrayOf(1, 2, 3), "e2", 4L))
        assertEquals("e", FlockFiles.peek(dir)!!.etag)
        assertEquals(FlockApproach.MIN_COUNT, FlockFiles.read(dir)!!.cameras.size)
        assertTrue(before.contentEquals(FlockFiles.file(dir).readBytes()))
        assertFalse(FlockFiles.commitParsed(dir, platesJson(3).toByteArray(), "short", 5L))
        assertEquals("e", FlockFiles.peek(dir)!!.etag)
    }

    @Test
    fun anOlderPlateFileStillReads() {
        val dir = tempDir()
        DataOutputStream(FileOutputStream(FlockFiles.file(dir))).use { out ->
            out.write(byteArrayOf('F'.code.toByte(), 'W'.code.toByte(), 'F'.code.toByte(), 'C'.code.toByte()))
            out.writeShort(2)
            out.writeInt(FlockApproach.MIN_COUNT)
            out.writeLong(5L)
            val etag = "v2".toByteArray()
            out.writeShort(etag.size)
            out.write(etag)
            repeat(FlockApproach.MIN_COUNT) { i ->
                out.writeLong(i + 1L)
                out.writeInt(30_000_000)
                out.writeInt(-100_000_000)
                out.writeByte(if (i == 0) 1 else 0)
                val brand = if (i == 1) "Vigilant".toByteArray() else ByteArray(0)
                out.writeByte(brand.size)
                out.write(brand)
                out.writeByte(0)
            }
        }
        val saved = FlockFiles.read(dir)!!
        assertEquals(2, saved.header.version)
        assertEquals(SensorKind.PLATE, saved.cameras.first().kind)
        assertTrue(saved.cameras.first().flock)
        assertEquals("Vigilant", saved.cameras.first { it.key == 2L }.brand)
        assertTrue(FlockFiles.peek(dir)!!.version >= FlockFiles.ALL_PLATES)
        val flockOnly = tempDir()
        DataOutputStream(FileOutputStream(FlockFiles.file(flockOnly))).use { out ->
            out.write(byteArrayOf('F'.code.toByte(), 'W'.code.toByte(), 'F'.code.toByte(), 'C'.code.toByte()))
            out.writeShort(1)
            out.writeInt(FlockApproach.MIN_COUNT)
            out.writeLong(5L)
            val etag = "v1".toByteArray()
            out.writeShort(etag.size)
            out.write(etag)
            repeat(FlockApproach.MIN_COUNT) { i ->
                out.writeLong(i + 1L)
                out.writeInt(30_000_000)
                out.writeInt(-100_000_000)
                out.writeByte(0)
            }
        }
        val old = FlockFiles.read(flockOnly)!!
        assertEquals(1, old.header.version)
        assertEquals(SensorKind.PLATE, old.cameras.first().kind)
        assertTrue(old.cameras.first().flock)
        assertFalse(FlockFiles.peek(flockOnly)!!.version >= FlockFiles.ALL_PLATES)
    }

    @Test
    fun tapReadsTheClosestCameraAndLeavesATieToTheDetection() {
        val speed = FlockMark(39.0, -104.0, 1, listOf(0), kind = SensorKind.SPEED)
        val red = FlockMark(39.1, -104.0, 1, emptyList(), kind = SensorKind.RED)
        fun project(lat: Double, lon: Double) = (lat * 1000.0).toFloat() to (lon * 1000.0).toFloat()
        assertEquals(SensorKind.SPEED, cameraAt(listOf(speed, red), 39020f, -104000f, project = ::project)?.kind)
        assertNull(cameraAt(listOf(speed), 39050f, -104000f, project = ::project))
        assertEquals(SensorKind.SPEED, cameraAt(listOf(speed), 39040f, -104000f, project = ::project)?.kind)
        val same = FlockMark(1.0, 2.0, 1, emptyList(), kind = SensorKind.RED)
        assertEquals(
            SensorKind.SPEED,
            cameraAt(listOf(speed, same), 0f, 0f) { _, _ -> 10f to 0f }?.kind,
        )
        assertFalse(cameraWinsTap(10f, 10f, null))
        assertFalse(cameraWinsTap(10f, null, 10f))
        assertFalse(cameraWinsTap(9f, 10f, 12f))
        assertTrue(cameraWinsTap(10f, 30f, null))
        assertTrue(cameraWinsTap(4f, null, null))
    }

    @Test
    fun camerasOnTheSameSpotShareOneCard() {
        val speed = FlockMark(0.0, 0.0, 1, emptyList(), kind = SensorKind.SPEED)
        val red = FlockMark(0.0, 0.0001, 1, listOf(90), kind = SensorKind.RED)
        val far = FlockMark(1.0, 1.0, 1, emptyList(), kind = SensorKind.GUNSHOT)
        fun project(lat: Double, lon: Double) = when {
            lat == 0.0 && lon == 0.0 -> 100f to 100f
            lat == 0.0 -> 112f to 108f
            else -> 400f to 400f
        }
        val spot = camerasOnSpot(listOf(far, red, speed), speed, project = ::project)
        assertEquals(listOf(SensorKind.SPEED, SensorKind.RED), spot.map { it.kind })
        assertEquals(listOf(SensorKind.GUNSHOT), camerasOnSpot(listOf(far, speed), far, project = ::project).map { it.kind })
        assertEquals(100f to 40f, cardAnchor(10f, 40f, 100f, 20f, 260f, 90f))
        assertEquals(100f to 20f, cardAnchor(10f, 4f, 100f, 20f, 260f, 90f))
        assertEquals(150f to 40f, cardAnchor(150f, 40f, 100f, 20f, 260f, 90f))
    }

    @Test
    fun cameraCardNamesOneSensorAndCountsACrowd() {
        val flock = FlockMark(1.0, 2.0, 1, listOf(0, 90), flock = true, kind = SensorKind.PLATE)
        val card = cameraCard(flock, "Flock Safety")
        assertEquals("ui.cameras.kind.flock", card.kindKey)
        assertEquals("Flock Safety", card.brand)
        assertEquals("N, E", card.facing)
        val crowd = flock.copy(count = 4, facings = listOf(0))
        val piled = cameraCard(crowd, "Flock Safety")
        assertEquals("settings.layer.flock", piled.kindKey)
        assertEquals("", piled.brand)
        assertEquals("", piled.facing)
        assertEquals(4, piled.count)
        val gun = cameraCard(FlockMark(0.0, 0.0, 1, emptyList(), kind = SensorKind.GUNSHOT), "Nope")
        assertEquals("ui.cameras.kind.gun", gun.kindKey)
        assertEquals("", gun.facing)
        val other = cameraCard(FlockMark(0.0, 0.0, 1, emptyList(), kind = SensorKind.PLATE), "Vigilant")
        assertEquals("ui.cameras.kind.plate", other.kindKey)
        assertEquals("N", compassPoint(0))
        assertEquals("N", compassPoint(22))
        assertEquals("NE", compassPoint(23))
        assertEquals("NW", compassPoint(337))
        assertEquals("N", compassPoint(338))
        assertEquals("S", compassPoint(180))
        assertEquals("N", compassPoint(-10))
        val index = FlockIndex()
        index.replace(
            listOf(
                FlockCamera(1L, 39.0, -104.0, listOf(90), brand = "Near", kind = SensorKind.SPEED),
                FlockCamera(2L, 39.0001, -104.0, listOf(0), brand = "Farther", kind = SensorKind.SPEED),
                FlockCamera(3L, 39.0, -104.0, emptyList(), brand = "Red Co", kind = SensorKind.RED),
                FlockCamera(4L, 39.0, -104.0, listOf(90), brand = "Flock Safety", flock = true),
                FlockCamera(5L, 39.0, -104.0, listOf(90), brand = "Vigilant"),
            ),
        )
        val aimed = FlockMark(39.0, -104.0, 1, listOf(90), kind = SensorKind.SPEED)
        assertEquals("Near", cameraBrand(index, aimed))
        assertEquals("", cameraBrand(index, aimed.copy(count = 3)))
        val plate = FlockMark(39.0, -104.0, 1, listOf(90), flock = true, kind = SensorKind.PLATE)
        assertEquals("Flock Safety", cameraBrand(index, plate))
    }

    @Test
    fun noticesFollowTheSensorType() {
        assertEquals("alert.speed.looking", sensorNotice(FlockHit(1L, true, kind = SensorKind.SPEED)).first)
        assertEquals("alert.speed.ahead", sensorNotice(FlockHit(1L, false, kind = SensorKind.SPEED)).first)
        assertEquals("alert.red.ahead", sensorNotice(FlockHit(1L, false, kind = SensorKind.RED)).first)
        assertEquals("alert.gun.ahead", sensorNotice(FlockHit(1L, true, kind = SensorKind.GUNSHOT)).first)
        assertEquals("alert.camera.looking", sensorNotice(FlockHit(1L, true, kind = SensorKind.CAMERA)).first)
        assertEquals("alert.flock.ahead", sensorNotice(FlockHit(1L, false, flock = true)).first)
        assertEquals("Vigilant", sensorNotice(FlockHit(1L, false, brand = "Vigilant")).second["brand"])
    }

    @Test
    fun theCameraControlWaitsUntilAListIsLoaded() {
        val index = FlockIndex()
        assertFalse(index.hasCameras())
        index.markMissing()
        assertFalse(index.hasCameras())
        index.replace(listOf(FlockCamera(1L, 39.0, -104.0, listOf(90))))
        assertTrue(index.hasCameras())
        index.replace(emptyList())
        assertFalse(index.hasCameras())
        index.clear()
        assertFalse(index.hasCameras())
    }

    @Test
    fun aPaddedGatherMatchesTheExactBox() {
        val index = FlockIndex()
        index.replace(
            listOf(
                FlockCamera(1L, 39.0, -104.0, listOf(15)),
                FlockCamera(2L, 39.001, -104.03, emptyList()),
                FlockCamera(3L, 39.002, -103.99, listOf(200)),
            ),
        )
        val south = 38.99
        val north = 39.01
        val west = -104.02
        val east = -103.98
        val direct = marksOf(index, south, north, west, east)
        val gather = index.gatherFor(null, south, north, west, east)
        assertFalse(gather.cells)
        assertTrue(gather.south < south)
        assertTrue(gather.west < west)
        assertEquals(direct, packed(gather, south, north, west, east))
        assertTrue(gather.visible(south, north, west, east).none { it.lon == -104.03 })
        val inside = index.gatherFor(gather, south + 0.001, north - 0.001, west + 0.001, east - 0.001)
        assertSame(gather, inside)
        index.replace(listOf(FlockCamera(1L, 39.0, -104.0, emptyList())))
        assertTrue(index.gatherFor(gather, south, north, west, east) !== gather)
    }

    @Test
    fun aCellGatherKeepsAnEdgeCell() {
        val cell = FlockIndex.CELL_DEG
        val band = 1950
        val south = band * cell + cell * 0.55
        val north = south + cell
        val edgeLat = band * cell + cell * 0.2
        val outsideLat = 38.995
        assertTrue(edgeLat < south)
        assertEquals(floor(south / cell).toInt(), floor(edgeLat / cell).toInt())
        assertTrue(floor(outsideLat / cell).toInt() < floor(south / cell).toInt())
        val plates = List(8_001) { i ->
            FlockCamera(i + 1L, 39.015 + (i % 400) * 0.00003, -104.0, emptyList(), flock = true)
        }
        val index = FlockIndex()
        index.replace(
            plates + listOf(
                FlockCamera(9_000_001L, edgeLat, -104.03, listOf(10)),
                FlockCamera(9_000_002L, outsideLat, -104.0, emptyList()),
            ),
        )
        val west = -104.05
        val east = -103.95
        val direct = marksOf(index, south, north, west, east)
        val gather = index.gatherFor(null, south, north, west, east)
        assertTrue(gather.cells)
        assertTrue(gather.south < south)
        assertTrue(direct.any { it.lat == edgeLat })
        assertTrue(direct.none { it.lat == outsideLat })
        assertTrue(gather.visible(south, north, west, east).any { it.lat == edgeLat })
        assertTrue(gather.visible(south, north, west, east).none { it.lat == outsideLat })
        assertEquals(direct, packed(gather, south, north, west, east))
        val close = index.gatherFor(gather, 39.024, 39.030, west, east)
        assertFalse(close.cells)
        assertTrue(close !== gather)
    }

    @Test
    fun paddingDoesNotFlipSliceAndCell() {
        val index = FlockIndex()
        val core = List(8_000) { i ->
            FlockCamera(i + 1L, 39.0 + (i % 20) * 0.00001, -104.0, emptyList())
        }
        val extra = List(200) { i ->
            FlockCamera(20_000L + i, 39.025, -104.0, emptyList())
        }
        index.replace(core + extra)
        val south = 38.999
        val north = 39.011
        val west = -104.1
        val east = -103.9
        val gather = index.gatherFor(null, south, north, west, east)
        assertFalse(gather.cells)
        assertEquals(south, gather.south, 0.0)
        assertEquals(north, gather.north, 0.0)
        assertEquals(west, gather.west, 0.0)
        assertEquals(east, gather.east, 0.0)
        val marks = marksOf(index, south, north, west, east)
        assertEquals(8_000, marks.sumOf { it.count })
        assertTrue(marks.none { it.lat == 39.025 })
        assertEquals(marks, packed(gather, south, north, west, east))
    }

    @Test
    fun aDateLineGatherStaysOnTheExactBox() {
        val index = FlockIndex()
        index.replace(listOf(FlockCamera(1L, 10.5, 179.5, emptyList())))
        val gather = index.gatherFor(null, 10.0, 11.0, 179.0, -179.0)
        assertTrue(gather.west > gather.east)
        assertEquals(179.0, gather.west, 0.0)
        assertEquals(-179.0, gather.east, 0.0)
        assertSame(gather, index.gatherFor(gather, 10.0, 11.0, 179.0, -179.0))
        assertTrue(index.gatherFor(gather, 10.0, 11.0, 178.0, -178.0) !== gather)
    }

    private fun marksOf(
        index: FlockIndex,
        south: Double,
        north: Double,
        west: Double,
        east: Double,
    ): List<FlockMark> = index.marks(south, north, west, east, { lat, lon -> lat.toFloat() to lon.toFloat() }, { _, _ -> true })

    private fun packed(
        gather: CameraGather,
        south: Double,
        north: Double,
        west: Double,
        east: Double,
    ): List<FlockMark> = FlockIndex.pack(
        gather.visible(south, north, west, east),
        { lat, lon -> lat.toFloat() to lon.toFloat() },
        { _, _ -> true },
    )

    private fun tempDir(): File = File.createTempFile("flock", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }

    private fun platesJson(count: Int): String {
        val features = (1..count).joinToString(",") { i ->
            val lon = -100.0 + i * 0.0001
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lon,30.0]},"properties":{"osmId":$i,"osmType":"node","brand":"Flock"}}"""
        }
        return """{"type":"FeatureCollection","features":[$features]}"""
    }

    private companion object {
        val FIXTURE = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.99,39.74]},
               "properties":{"osmId":10,"osmType":"node","brand":"Flock","operator":"Flock Safety","direction":90}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.98,39.75]},
               "properties":{"osmId":11,"osmType":"node","brand":"Vigilant","operator":"Motorola"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.97,39.76]},
               "properties":{"osmId":12,"osmType":"way","operator":"flock safety","directions":[0,180,270,45,10]}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.96,39.77]},
               "properties":{"osmId":13,"osmType":"node","brand":"Flock Safety"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[999,39.7]},
               "properties":{"osmId":14,"osmType":"node","brand":"Flock"}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[0,0],[1,1]]},
               "properties":{"osmId":15,"osmType":"node","brand":"Flock"}},
              {"type":"Feature","geometry":null,"properties":{"osmId":16,"brand":"Flock"}}
            ]}
        """.trimIndent()

        val KINDS = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.1,39.1]},
               "properties":{"osmId":20,"osmType":"node","highway":"speed_camera","direction":"90;180"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.2,39.2]},
               "properties":{"osmId":21,"osmType":"relation","enforcement":"red_light_camera"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.3,39.3]},
               "properties":{"osmId":22,"osmType":"node","surveillance:type":"gunshot_detector","direction":"40"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.4,39.4]},
               "properties":{"osmId":23,"osmType":"node","surveillance:type":"camera","camera:direction":"270"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.5,39.5]},
               "properties":{"osmId":24,"osmType":"node","surveillance:type":"camera;radar"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.6,39.6]},
               "properties":{"osmId":25,"osmType":"node","surveillance:type":"ALPR","brand":"Flock"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.7,39.7]},
               "properties":{"osmId":26,"osmType":"node","enforcement":"stop"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.8,39.8]},
               "properties":{"osmId":27,"osmType":"node","kind":"camera","highway":"speed_camera"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-104.9,39.9]},
               "properties":{"osmId":28,"osmType":"node","direction":"NW","brand":"Flock"}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[-105.0,40.0]},
               "properties":{"osmId":29,"osmType":"node","highway":"speed_camera","enforcement":"red_light_camera","direction":"260"}}
            ]}
        """.trimIndent()
    }
}
