package app.fieldwatch.alert

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
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.AlertVoiceWhat
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
    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var utteranceStarted = false
    private var ttsSpeakTries = 0
    private var pendingSpeak: String? = null
    private var speakAfterBeep: String? = null
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
    ) {
        if (!alertsOn || watchlist.isEmpty()) return
        fireNewAppearances(
            devices, fleets, watchlist, beepOn, voiceOn, voiceWhat, shadeOn,
            visibleOnLive, arrivalsOnly, demoMode,
        )
    }

    fun playTestBeep(
        beepOn: Boolean = true,
        speakClass: Boolean = false,
        voiceWhat: AlertVoiceWhat = AlertVoiceWhat.CLASS,
    ) {
        if (speakClass) prepareVoice()
        if (beepOn) {
            beep(holdFocus = !speakClass)
            vibrate()
        }
        if (speakClass) queueVoice(testWatchPhrase(voiceWhat), afterBeep = beepOn)
    }

    /** Warm the TTS engine on the main thread so the first alert is not silent. */
    fun prepareVoice() {
        if (Looper.myLooper() == Looper.getMainLooper()) ensureTts()
        else main.post { ensureTts() }
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
    ) {
        val now = System.currentTimeMillis()
        val onAir = devices.filter { !it.gone }
        val liveKeys = onAir.map { it.key }.toSet()
        announced.removeAll { token -> token.substringAfter('\t') !in liveKeys }
        val fleetNames = fleets.associateBy { it.id }
        var beepFired = false
        var voicePhrase: String? = null
        if (voiceOn) prepareVoice()
        for (target in watchlist) {
            for (device in onAir) {
                if (target.deviceKey != null && !target.alert) continue
                if (!matches(target, device)) continue
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
                if (beepOn && !beepFired) {
                    beep(holdFocus = !voiceOn)
                    beepFired = true
                }
                if (beepOn || voiceOn) _flashes.tryEmit(device.key)
                if (voiceOn && voicePhrase == null) {
                    voicePhrase = spokenWatchPhrase(device, fleets, voiceWhat, target)
                }
            }
        }
        if (voicePhrase != null) queueVoice(voicePhrase, afterBeep = beepFired)
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

    private fun matches(target: WatchTarget, device: Sighting): Boolean = when {
        target.deviceKey != null -> device.key == target.deviceKey
        target.fleetId != null -> target.fleetId in device.fleetIds
        else -> false
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
            .setContentTitle("Fieldwatch watchlist")
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
        main.post { playSynthBeep(holdFocus) }
    }

    private fun playSynthBeep(holdFocus: Boolean) {
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
                finishBeep()
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
                if (beepTrack === track) stopBeep()
                finishBeep()
            }, playMs + 40L)
        }.onFailure {
            finishBeep()
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

    private fun speakClassName(text: String) {
        val phrase = text.trim()
        if (phrase.isEmpty()) return
        pendingSpeak = phrase
        ensureTts()
        if (!ttsReady) return
        startSpeak(phrase)
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
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

    private fun startSpeak(text: String) {
        val engine = tts ?: return
        if (!ttsReady) {
            pendingSpeak = text
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
            if (ttsSpeakTries < 2) {
                main.postDelayed({ startSpeak(text) }, TTS_AFTER_INIT_MS)
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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching { manager.deleteNotificationChannel("fieldwatch_watch_v2") }
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Watchlist", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Appearing signatures and devices on your watchlist. Beep is played separately."
                enableVibration(true)
                enableLights(true)
                setSound(null, null)
            },
        )
    }

    companion object {
        const val CHANNEL = "fieldwatch_watch_v3"
        const val EXTRA_DEVICE_KEY = "device_key"
        private const val FREQ_HZ = 1050.0
        private const val PIP_MS = 85
        private const val GAP_MS = 55
        private const val NEW_WINDOW_MS = 20_000L
        private const val HUNT_FREQ_HZ = 1680.0
        private const val HUNT_PIP_MS = 22
        private const val HUNT_PULSE_MS = 60L
        private const val VOICE_AFTER_TRACK_MS = 220L
        private const val TTS_AFTER_INIT_MS = 250L
        private const val TTS_START_WAIT_MS = 1_500L
        private const val SPEAK_TIMEOUT_MS = 8_000L
        private const val UTTERANCE = "fieldwatch-class"
    }
}
