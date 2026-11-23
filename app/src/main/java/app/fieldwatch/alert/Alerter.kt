package app.fieldwatch.alert

import app.fieldwatch.domain.LanguageText

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sin
import androidx.core.app.NotificationCompat
import app.fieldwatch.MainActivity
import app.fieldwatch.R
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.DroneNear
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.QuietAlert
import app.fieldwatch.domain.QuietEdge
import app.fieldwatch.domain.QuietMark
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.swarmQuietsFleetWatch
import app.fieldwatch.domain.SpokenName
import app.fieldwatch.domain.WatchTarget
import app.fieldwatch.domain.spokenWatchPhrase
import app.fieldwatch.domain.testWatchPhrase
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class Alerter(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val announced = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var beepTrack: AudioTrack? = null
    private var huntTrack: AudioTrack? = null
    private var quietTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var utteranceStarted = false
    private var ttsSpeakTries = 0
    private var pendingSpeak: String? = null
    /** Written off the main thread, read when the beep timer fires. */
    @Volatile
    private var speakAfterBeep: String? = null
    /** Bumped before the beep is posted so a stale timer cannot eat the next phrase. */
    @Volatile
    private var beepToken = 0
    /** Init failed, or speak() found the engine unbound. The next attempt builds a new one. */
    private var ttsFailed = false
    private var ttsGeneration = 0
    private val speakWatchdog = Runnable {
        if (!speaking) return@Runnable
        val retry = pendingSpeak
        speaking = false
        utteranceStarted = false
        releaseFocus()
        if (retry != null && ttsSpeakTries < 2) speakClassName(retry)
    }
    private val _flashes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val flashes: SharedFlow<String> = _flashes.asSharedFlow()

    init {
        ensureChannel()
    }

    fun checkLive(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        alertsOn: Boolean,
        beepOn: Boolean,
        voiceOn: Boolean = false,
        voiceWhat: AlertVoiceWhat = AlertVoiceWhat.CLASS,
        shadeOn: Boolean = false,
        visibleOnLive: (Sighting) -> Boolean = { !it.gone },
        arrivalsOnly: Boolean = false,
        demoMode: Boolean = false,
        spokenNames: List<SpokenName> = emptyList(),
        quietDroneFleets: Set<String> = emptySet(),
        holdRemoteId: (Sighting) -> Boolean = { false },
    ) {
        if (!alertsOn || watchlist.isEmpty()) return
        fireNewAppearances(
            devices, fleets, watchlist, beepOn, voiceOn, voiceWhat, shadeOn,
            visibleOnLive, arrivalsOnly, demoMode, spokenNames, quietDroneFleets,
            holdRemoteId,
        )
    }

    /**
     * One drone that broadcast inside the chosen distance. Own channel, silent.
     * Beep and vibration are the pip. A later drone in the same pass can post
     * without beeping again.
     */
    fun droneNearAlert(
        title: String,
        text: String,
        phrase: String?,
        beep: Boolean,
        vibrate: Boolean,
        shade: Boolean,
        noteId: Int,
    ) {
        if (shade) {
            ensureDroneNearChannel()
            val pending = PendingIntent.getActivity(
                context,
                noteId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, NEAR_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(noteId, note)
        }
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
        if (vibrate) vibrate()
    }

    /**
     * One Remote ID emergency. Own channel, silent. A later drone in the same
     * pass can post without beeping again. The note stays when the declaration ends.
     */
    fun emergencyAlert(
        title: String,
        text: String,
        phrase: String?,
        beep: Boolean,
        vibrate: Boolean,
        shade: Boolean,
        noteId: Int,
    ) {
        if (shade) {
            ensureEmergencyChannel()
            val pending = PendingIntent.getActivity(
                context,
                noteId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, EMERGENCY_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(noteId, note)
        }
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
        if (vibrate) vibrate()
    }

    fun playTestBeep(
        beepOn: Boolean = true,
        speakClass: Boolean = false,
        voiceWhat: AlertVoiceWhat = AlertVoiceWhat.CLASS,
    ) {
        if (speakClass) {
            prepareVoice()
            queueVoice(testWatchPhrase(voiceWhat), afterBeep = beepOn)
        }
        if (beepOn) {
            beep(holdFocus = !speakClass)
            vibrate()
        }
    }

    /** Warm the TTS engine on the main thread so the first alert is not silent. */
    fun prepareVoice() {
        if (Looper.myLooper() == Looper.getMainLooper()) ensureTts()
        else main.post { ensureTts() }
    }

    /**
     * GNSS interference or spoofing. Own notification channel so it can be muted
     * without muting the watchlist. Phrase is null when voice is off.
     * Text must not include coordinates.
     */
    fun gnssAlert(title: String, text: String, phrase: String?, beep: Boolean, shade: Boolean) {
        if (shade) {
            ensureGnssChannel()
            val pending = PendingIntent.getActivity(
                context,
                GNSS_NOTE_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, GNSS_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(GNSS_NOTE_ID, note)
        } else {
            manager.cancel(GNSS_NOTE_ID)
        }
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
    }

    fun clearGnssNote() {
        manager.cancel(GNSS_NOTE_ID)
    }

    /**
     * One card for the whole Remote ID swarm, when [shade] is on. Later counts
     * replace it. Own channel, silent, separate from the watchlist. Beep,
     * voice, and vibration are for the first post. A rising count rewrites
     * the text only.
     */
    fun swarmAlert(
        title: String,
        text: String,
        phrase: String?,
        beep: Boolean,
        vibrate: Boolean,
        shade: Boolean,
    ) {
        if (shade) {
            ensureSwarmChannel()
            val pending = PendingIntent.getActivity(
                context,
                SWARM_NOTE_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, SWARM_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(SWARM_NOTE_ID, note)
        } else {
            manager.cancel(SWARM_NOTE_ID)
        }
        // Store the phrase before posting the beep. The timer can finish
        // before a later write, and then the line is never spoken.
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
        if (vibrate) vibrate()
    }

    fun clearSwarmNote() {
        manager.cancel(SWARM_NOTE_ID)
    }

    /**
     * Trusted-network alert. Own channel so it can be muted without muting the watchlist.
     * [title] and [body] must not include coordinates. Phrase is null when voice is off.
     */
    fun trustedAlert(title: String, body: String, phrase: String?, beep: Boolean, shade: Boolean) {
        if (shade) {
            ensureTrustedChannel()
            val pending = PendingIntent.getActivity(
                context,
                TRUSTED_NOTE_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, TRUSTED_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(TRUSTED_NOTE_ID, note)
        } else {
            manager.cancel(TRUSTED_NOTE_ID)
        }
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
    }

    fun clearTrustedNote() {
        manager.cancel(TRUSTED_NOTE_ID)
    }

    /**
     * Cell connection monitoring. Own channel so it can be muted without muting the watchlist.
     * [title] is the warning sentence. [body] has no cell id and no coordinates.
     */
    fun cellAlert(title: String, body: String, phrase: String?, beep: Boolean, shade: Boolean) {
        if (shade) {
            ensureCellChannel()
            val pending = PendingIntent.getActivity(
                context,
                CELL_NOTE_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, CELL_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_fieldwatch)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(CELL_NOTE_ID, note)
        } else {
            manager.cancel(CELL_NOTE_ID)
        }
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
    }

    fun clearCellNote() {
        manager.cancel(CELL_NOTE_ID)
    }

    /**
     * Tracker monitoring. Low and silent. [text] has no coordinates and no owner claim.
     * The tap opens that radio.
     */
    fun trackerNote(title: String, text: String, deviceKey: String) {
        ensureTrackerChannel()
        val pending = PendingIntent.getActivity(
            context,
            TRACKER_NOTE_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_DEVICE_KEY, deviceKey)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, TRACKER_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        manager.notify(TRACKER_NOTE_ID, note)
    }

    fun clearTrackerNote() {
        manager.cancel(TRACKER_NOTE_ID)
    }

    /** Beep and speak a tracker warning. The notification stays in [trackerNote]. */
    fun trackerCue(phrase: String?, beep: Boolean) {
        if (!phrase.isNullOrBlank()) {
            prepareVoice()
            queueVoice(phrase, afterBeep = beep)
        }
        if (beep) beep(holdFocus = phrase.isNullOrBlank())
    }

    /**
     * A known mapped sensor ahead. Speaks [text], posts it on the layer channel, and pulses once.
     * [text] has no coordinates. This does not use the watchlist beep.
     */
    fun flockNote(text: String) {
        if (text.isBlank()) return
        prepareVoice()
        main.post {
            ensureFlockChannel()
            val pending = PendingIntent.getActivity(
                context,
                FLOCK_NOTE_ID,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val note = NotificationCompat.Builder(context, FLOCK_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_layer)
                .setContentTitle(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setSilent(true)
                .setContentIntent(pending)
                .build()
            manager.notify(FLOCK_NOTE_ID, note)
            flockPulse()
            speakOver(text)
        }
    }

    fun clearFlockNote() {
        main.post { manager.cancel(FLOCK_NOTE_ID) }
    }

    /**
     * One low pip when a named radio goes quiet. Does not jump the Live list
     * and does not take audio focus, so a watchlist phrase is left alone.
     * Returns a mark for each radio that actually cued.
     */
    fun wentQuiet(
        edges: List<QuietEdge>,
        watchlist: List<WatchTarget>,
        beepOn: Boolean,
        voiceOn: Boolean,
        shadeOn: Boolean,
        demoMode: Boolean,
    ): List<QuietMark> {
        if (edges.isEmpty()) return emptyList()
        val rows = HashMap<String, WatchTarget>(watchlist.size)
        for (row in watchlist) {
            val key = row.deviceKey ?: continue
            if (QuietAlert.armed(row)) rows[key] = row
        }
        if (rows.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val marks = ArrayList<QuietMark>()
        var pip = false
        var pulse = false
        var phrase: String? = null
        for (edge in edges) {
            val row = rows[edge.key] ?: continue
            val name = QuietAlert.cueName(row.label, edge.name, edge.mac, demoMode)
            marks += QuietMark(
                at = now,
                lastHeard = edge.lastSeen,
                key = edge.key,
                name = name,
                rssi = edge.rssi,
            )
            if (shadeOn && row.notify) notifyQuiet(name, edge)
            if (row.vibrate) pulse = true
            if (voiceOn && phrase == null) {
                phrase = LanguageText.text("alert.quiet.voice", mapOf("name" to name))
            }
            if (beepOn) pip = true
        }
        if (marks.isEmpty()) return marks
        if (pip) main.post { playQuietPip() }
        if (pulse) main.post { vibrateOnce() }
        val say = phrase
        if (say != null && speakAfterBeep == null && !speaking) {
            prepareVoice()
            val wait = if (pip) QUIET_PIP_MS + 120L else 0L
            main.postDelayed({
                if (speakAfterBeep == null && !speaking) speakClassName(say)
            }, wait)
        }
        return marks
    }

    /** Short Hunt geiger tick. Not the watchlist double-pip. */
    fun huntTick(beepOn: Boolean, vibrateOn: Boolean) {
        if (vibrateOn) main.post { huntPulse() }
        if (beepOn) main.post { playHuntClick() }
    }

    fun forgetAnnounced() {
        announced.clear()
    }

    private fun fireNewAppearances(
        devices: List<Sighting>,
        fleets: List<Fleet>,
        watchlist: List<WatchTarget>,
        beepOn: Boolean,
        voiceOn: Boolean,
        voiceWhat: AlertVoiceWhat,
        shadeOn: Boolean,
        visibleOnLive: (Sighting) -> Boolean,
        arrivalsOnly: Boolean,
        demoMode: Boolean,
        spokenNames: List<SpokenName>,
        quietDroneFleets: Set<String>,
        holdRemoteId: (Sighting) -> Boolean,
    ) {
        val now = System.currentTimeMillis()
        val onAir = devices.filter { !it.gone }
        val liveKeys = onAir.map { it.key }.toSet()
        announced.removeAll { token -> token.substringAfter('\t') !in liveKeys }
        val fleetNames = fleets.associateBy { it.id }
        val mineKeys = RadioBookmarks.mineKeys(watchlist)
        val muteKeys = RadioBookmarks.muteKeys(watchlist)
        var beepFired = false
        var voicePhrase: String? = null
        if (voiceOn) prepareVoice()
        for (target in watchlist) {
            for (device in onAir) {
                if (swarmQuietsFleetWatch(target.deviceKey, target.fleetId, quietDroneFleets)) continue
                if (target.deviceKey == null &&
                    target.fleetId == DroneNear.REMOTE_ID_FLEET &&
                    holdRemoteId(device)
                ) {
                    continue
                }
                if (!shouldRaiseWatch(target, device, mineKeys, muteKeys)) continue
                if (!visibleOnLive(device)) continue
                val token = "${target.id}\t${device.key}"
                if (token in announced) continue
                if (!arrivalsOnly && !isNewAppearance(device, now)) continue
                announced.add(token)
                val label = MacUtil.redactMacIn(
                    target.label.ifBlank {
                        fleetNames[target.fleetId]?.name ?: device.displayName
                    },
                    device.mac,
                    demoMode,
                )
                if (shadeOn && target.notify) notify(label, device, demoMode)
                if (target.vibrate) vibrate()
                if (voiceOn && voicePhrase == null) {
                    voicePhrase = spokenWatchPhrase(
                        device, fleets, voiceWhat, target, spokenNames, watchlist,
                    )
                }
                if (beepOn && !beepFired) {
                    // The scan loop keeps going after this. Queue first so the
                    // beep timer cannot finish while the phrase is still unset.
                    if (voicePhrase != null) queueVoice(voicePhrase, afterBeep = true)
                    beep(holdFocus = voicePhrase == null)
                    beepFired = true
                }
                if (beepOn || voiceOn) _flashes.tryEmit(device.key)
            }
        }
        if (voicePhrase != null && !beepFired) queueVoice(voicePhrase, afterBeep = false)
    }

    private fun queueVoice(text: String, afterBeep: Boolean) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        if (afterBeep) {
            speakAfterBeep = phrase
        } else {
            main.post { speakClassName(phrase) }
        }
    }

    private fun isNewAppearance(device: Sighting, now: Long): Boolean {
        if (now - device.firstSeen <= NEW_WINDOW_MS) return true
        val spanStart = device.presence.lastOrNull()?.start ?: device.firstSeen
        return now - spanStart <= NEW_WINDOW_MS
    }

    private fun notify(label: String, device: Sighting, demoMode: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEVICE_KEY, device.key)
        }
        val pending = PendingIntent.getActivity(
            context,
            device.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(LanguageText.text("ui.fieldwatch.watchlist"))
            .setContentText("$label  ${device.rssi} dBm  ${MacUtil.screenMac(device.mac, demoMode)}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$label appeared\n${device.kind}  ${MacUtil.screenMac(device.mac, demoMode)}\n${device.rssi} dBm  ${MacUtil.redactMacIn(device.displayName, device.mac, demoMode)}",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
        manager.notify(device.key.hashCode() and 0x7FFFFFFF, note)
    }

    private fun beep(holdFocus: Boolean = true) {
        val token = beepToken + 1
        beepToken = token
        main.post { playSynthBeep(holdFocus, token) }
    }

    private fun playSynthBeep(holdFocus: Boolean, token: Int) {
        if (token != beepToken) return
        runCatching {
            stopBeep()
            val sampleRate = 22_050
            val pipSamples = sampleRate * PIP_MS / 1000
            val gapSamples = sampleRate * GAP_MS / 1000
            val total = pipSamples + gapSamples + pipSamples
            val pcm = ShortArray(total)
            writeSine(pcm, 0, pipSamples, sampleRate, FREQ_HZ)
            writeSine(pcm, pipSamples + gapSamples, pipSamples, sampleRate, FREQ_HZ)

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(total * 2, minBuf))
                .build()
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                track.release()
                if (token == beepToken) finishBeep()
                return@runCatching
            }
            // TTS engines run in another process. Holding media focus here ducks
            // or silences them, so skip focus when a phrase will follow the pip.
            if (holdFocus) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audio.requestAudioFocus(req)
                focusRequest = req
            }
            beepTrack = track
            track.play()
            val playMs = total * 1000L / sampleRate
            main.postDelayed({
                if (token != beepToken) return@postDelayed
                if (beepTrack === track) stopBeep()
                finishBeep()
            }, playMs + 40L)
        }.onFailure {
            if (token == beepToken) finishBeep()
        }
    }

    private fun stopBeep() {
        val track = beepTrack
        beepTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        releaseFocus()
    }

    private fun releaseFocus() {
        val req = focusRequest ?: return
        focusRequest = null
        runCatching { audio.abandonAudioFocusRequest(req) }
    }

    private fun finishBeep() {
        val phrase = speakAfterBeep ?: return
        speakAfterBeep = null
        releaseFocus()
        ttsSpeakTries = 0
        main.postDelayed({ speakClassName(phrase) }, VOICE_AFTER_TRACK_MS)
    }

    private fun writeSine(dest: ShortArray, offset: Int, n: Int, sampleRate: Int, freqHz: Double) {
        val step = 2.0 * Math.PI * freqHz / sampleRate
        val attack = (sampleRate * 0.004).toInt().coerceAtLeast(1)
        val release = (sampleRate * 0.008).toInt().coerceAtLeast(1)
        val peak = Short.MAX_VALUE * 0.62
        for (i in 0 until n) {
            val env = when {
                i < attack -> i.toFloat() / attack
                i > n - release -> (n - i).toFloat() / release
                else -> 1f
            }.coerceIn(0f, 1f)
            dest[offset + i] = (sin(step * i) * peak * env).toInt().toShort()
        }
    }

    private fun playHuntClick() {
        runCatching {
            stopHuntClick()
            val sampleRate = 22_050
            val n = sampleRate * HUNT_PIP_MS / 1000
            val pcm = ShortArray(n)
            writeSine(pcm, 0, n, sampleRate, HUNT_FREQ_HZ)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(n * 2, minBuf))
                .build()
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                track.release()
                return@runCatching
            }
            huntTrack = track
            track.play()
            val playMs = n * 1000L / sampleRate
            main.postDelayed({
                if (huntTrack === track) stopHuntClick()
            }, playMs + 20L)
        }
    }

    private fun stopHuntClick() {
        val track = huntTrack
        huntTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun huntPulse() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        val amp = if (vibrator.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = VibrationEffect.createOneShot(HUNT_PULSE_MS, amp)
        if (Build.VERSION.SDK_INT >= 33) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            vibrator.vibrate(effect)
        }
    }

    /** Speak now, interrupting a phrase already in progress. The engine keeps the line if it is still starting. */
    private fun speakOver(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        pendingSpeak = phrase
        ensureTts()
        if (!ttsReady) return
        if (speaking) {
            speaking = false
            utteranceStarted = false
        }
        startSpeak(phrase)
    }

    private fun speakClassName(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        pendingSpeak = phrase
        ensureTts()
        if (!ttsReady) return
        startSpeak(phrase)
    }

    private fun ensureTts() {
        if (tts != null && !ttsFailed) return
        if (tts != null) releaseTts()
        val generation = ++ttsGeneration
        var created: TextToSpeech? = null
        created = TextToSpeech(context) { status ->
            main.post {
            val engine = created ?: return@post
            if (generation != ttsGeneration || tts !== engine) return@post
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false
                ttsFailed = true
                val phrase = pendingSpeak
                if (phrase != null && ttsSpeakTries < 2) {
                    ttsSpeakTries++
                    main.post { ensureTts() }
                }
                return@post
            }
            ttsReady = true
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            runCatching {
                val lang = engine.setLanguage(Locale.getDefault())
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.language = Locale.US
                }
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    utteranceStarted = true
                    speaking = true
                    main.removeCallbacks(speakWatchdog)
                    main.postDelayed(speakWatchdog, SPEAK_TIMEOUT_MS)
                }
                override fun onDone(utteranceId: String?) {
                    speaking = false
                    utteranceStarted = false
                    pendingSpeak = null
                    ttsSpeakTries = 0
                    main.removeCallbacks(speakWatchdog)
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking = false
                    utteranceStarted = false
                    main.removeCallbacks(speakWatchdog)
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    speaking = false
                    utteranceStarted = false
                    main.removeCallbacks(speakWatchdog)
                }
            })
            // Samsung often drops the first speak() if it runs inside onInit.
            main.postDelayed({
                pendingSpeak?.let { startSpeak(it) }
            }, TTS_AFTER_INIT_MS)
            }
        }
        tts = created
    }

    /** Drop a speech engine that failed to start or is no longer bound. */
    private fun releaseTts() {
        val dead = tts
        tts = null
        ttsReady = false
        ttsFailed = false
        speaking = false
        utteranceStarted = false
        if (dead != null) runCatching { dead.shutdown() }
    }

    private fun startSpeak(text: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeak = text
            if (ttsFailed || engine == null) ensureTts()
            return
        }
        if (speaking && utteranceStarted) return
        pendingSpeak = text
        ttsSpeakTries++
        speaking = true
        utteranceStarted = false
        val ok = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
        if (ok != TextToSpeech.SUCCESS) {
            speaking = false
            // speak() does not rebind after the speech process dies.
            if (ttsSpeakTries < 2) {
                ttsFailed = true
                pendingSpeak = text
                ensureTts()
            } else {
                pendingSpeak = null
                ttsSpeakTries = 0
            }
            return
        }
        main.removeCallbacks(speakWatchdog)
        main.postDelayed({
            if (speaking && !utteranceStarted && ttsSpeakTries < 2) {
                speaking = false
                startSpeak(text)
            } else if (speaking && !utteranceStarted) {
                speaking = false
                pendingSpeak = null
                ttsSpeakTries = 0
            }
        }, TTS_START_WAIT_MS)
    }

    private fun notifyQuiet(name: String, edge: QuietEdge) {
        val title = LanguageText.text("alert.quiet.voice", mapOf("name" to name))
        val body = LanguageText.text(
            "alert.quiet.body",
            mapOf(
                "time" to QuietAlert.heardClock(edge.lastSeen),
                "rssi" to QuietAlert.rssiText(edge.rssi),
            ),
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEVICE_KEY, edge.key)
        }
        val pending = PendingIntent.getActivity(
            context,
            ("q:" + edge.key).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_fieldwatch)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
        manager.notify(("q:" + edge.key).hashCode() and 0x7FFFFFFF, note)
    }

    /** One sine. Separate from the arrival double-pip so it does not cancel that beep. */
    private fun playQuietPip() {
        runCatching {
            stopQuietPip()
            val sampleRate = 22_050
            val n = sampleRate * QUIET_PIP_MS / 1000
            val pcm = ShortArray(n)
            writeSine(pcm, 0, n, sampleRate, QUIET_FREQ_HZ)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(maxOf(n * 2, minBuf))
                .build()
            val written = track.write(pcm, 0, pcm.size)
            if (written <= 0) {
                track.release()
                return@runCatching
            }
            quietTrack = track
            track.play()
            val playMs = n * 1000L / sampleRate
            main.postDelayed({
                if (quietTrack === track) stopQuietPip()
            }, playMs + 20L)
        }
    }

    private fun stopQuietPip() {
        val track = quietTrack
        quietTrack = null
        if (track != null) {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
    }

    private fun vibrateOnce() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching { manager.deleteNotificationChannel("fieldwatch_watch_v2") }
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Watchlist", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = LanguageText.text("ui.appearing.signatures.devices.watchlist")
                    enableVibration(true)
                    enableLights(true)
                    setSound(null, null)
                },
            )
        }
    }

    private fun ensureDroneNearChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(NEAR_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NEAR_CHANNEL,
                LanguageText.text("ui.near.section"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = LanguageText.text("ui.near.note")
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun ensureEmergencyChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(EMERGENCY_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                EMERGENCY_CHANNEL,
                LanguageText.text("ui.rid.emergency.row"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = LanguageText.text("ui.rid.emergency.note")
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun ensureSwarmChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(SWARM_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                SWARM_CHANNEL,
                LanguageText.text("ui.swarm.section"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = LanguageText.text("ui.swarm.note")
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun ensureGnssChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(GNSS_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(GNSS_CHANNEL, LanguageText.text("settings.topic.gnss"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = LanguageText.text("ui.possible.gnss.interference.spoofing")
                setSound(null, null)
            },
        )
    }

    private fun ensureTrustedChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(TRUSTED_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(TRUSTED_CHANNEL, LanguageText.text("settings.topic.trusted"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = LanguageText.text("trusted.help")
                setSound(null, null)
            },
        )
    }

    private fun ensureCellChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(CELL_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CELL_CHANNEL, LanguageText.text("settings.topic.cell"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = LanguageText.text("cell.intro")
                setSound(null, null)
            },
        )
    }

    private fun ensureTrackerChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(TRACKER_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                TRACKER_CHANNEL,
                LanguageText.text("settings.topic.tracker"),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = LanguageText.text("tracker.follow.note")
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun ensureFlockChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        if (manager.getNotificationChannel(FLOCK_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                FLOCK_CHANNEL,
                LanguageText.text("settings.layer.channel"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = LanguageText.text("settings.layer.channel.body")
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun flockPulse() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        const val CHANNEL = "fieldwatch_watch_v3"
        const val GNSS_CHANNEL = "fieldwatch_gnss"
        const val FLOCK_CHANNEL = "fieldwatch_map_layers"
        const val TRUSTED_CHANNEL = "fieldwatch_trusted"
        const val CELL_CHANNEL = "fieldwatch_cell"
        const val TRACKER_CHANNEL = "fieldwatch_tracker"
        private const val GNSS_NOTE_ID = 71_001
        private const val SWARM_NOTE_ID = 71_005
        const val SWARM_CHANNEL = "fieldwatch_swarm"
        const val NEAR_CHANNEL = "fieldwatch_drone_near"
        const val EMERGENCY_CHANNEL = "fieldwatch_rid_emergency"
        private const val TRUSTED_NOTE_ID = 71_002
        private const val CELL_NOTE_ID = 71_003
        private const val TRACKER_NOTE_ID = 71_004
        private const val FLOCK_NOTE_ID = 72_010
        const val EXTRA_DEVICE_KEY = "device_key"
        private const val FREQ_HZ = 1050.0
        private const val PIP_MS = 85
        private const val GAP_MS = 55
        private const val NEW_WINDOW_MS = 20_000L
        private const val HUNT_FREQ_HZ = 1680.0
        private const val HUNT_PIP_MS = 22
        private const val QUIET_FREQ_HZ = 520.0
        private const val QUIET_PIP_MS = 140
        private const val HUNT_PULSE_MS = 60L
        private const val VOICE_AFTER_TRACK_MS = 220L
        private const val TTS_AFTER_INIT_MS = 250L
        private const val TTS_START_WAIT_MS = 1_500L
        private const val SPEAK_TIMEOUT_MS = 8_000L
        private const val UTTERANCE = "fieldwatch-class"
    }
}

/**
 * Mine or mute skips beep, voice, flash, vibrate, and the shade card for that device key.
 * A fleet watch still matches every other radio. Mute does not mark the radio as yours.
 * Alert on the row is left as set.
 */
internal fun shouldRaiseWatch(
    target: WatchTarget,
    device: Sighting,
    mineKeys: Set<String>,
    muteKeys: Set<String> = emptySet(),
): Boolean {
    if (device.key in mineKeys || device.key in muteKeys) return false
    if (target.deviceKey != null && !target.alert) return false
    return when {
        target.deviceKey != null -> device.key == target.deviceKey
        target.fleetId != null -> target.fleetId in device.fleetIds
        else -> false
    }
}
