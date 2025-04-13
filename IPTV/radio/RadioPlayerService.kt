package com.example.nesmat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class RadioPlayerService : Service() {

    private lateinit var libVLC: LibVLC
    private var vlcMediaPlayer: MediaPlayer? = null
    private var isMuted = false
    private var currentUrl: String? = null
    private var currentChannelName: String = "غير معروف"
    private var currentChannelIndex: Int = 0
    private var wasPlaying = false

    // Audio Focus
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlaying = vlcMediaPlayer?.isPlaying == true
                pauseStream()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlaying = vlcMediaPlayer?.isPlaying == true
                pauseStream()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasPlaying) {
                    playStream(currentUrl)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocus()
        libVLC = LibVLC(this)
        vlcMediaPlayer = MediaPlayer(libVLC).apply {
            setAudioOutput("android_audiotrack")
        }
    }

    private fun setupAudioFocus() {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupModernAudioFocus()
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "لم يتم منح صلاحية التركيز الصوتي")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupModernAudioFocus(): Int {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()

        return audioFocusRequest?.let { audioManager.requestAudioFocus(it) } ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            try {
                when (it.action) {
                    "PLAY" -> handlePlayAction(it) // تشغيل القناة
                    "PAUSE" -> pauseStream() // إيقاف مؤقت
                    "STOP" -> stopStream() // إيقاف القناة
                    "MUTE" -> toggleMute() // كتم الصوت
                    "SET_VOLUME" -> setVolume(it.getIntExtra("VOLUME_LEVEL", 100)) // ضبط مستوى الصوت
                    "SET_CURRENT_CHANNEL" -> updateCurrentChannel(it) // تحديث القناة الحالية
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ أثناء معالجة الأمر: ${e.message}")
                showToast("حدث خطأ أثناء تنفيذ الأمر!")
            }
        }
        return START_STICKY
    }

    private fun handlePlayAction(intent: Intent) {
        currentChannelName = intent.getStringExtra("CHANNEL_NAME") ?: "غير معروف"
        currentChannelIndex = intent.getIntExtra("CHANNEL_INDEX", 0)
        playStream(intent.getStringExtra("STREAM_URL"))
    }

    private fun updateCurrentChannel(intent: Intent) {
        currentChannelName = intent.getStringExtra("CHANNEL_NAME") ?: "غير معروف"
        currentChannelIndex = intent.getIntExtra("CHANNEL_INDEX", 0)
        showNotification("يتم الاستماع الآن إلى: $currentChannelName")
    }

    private fun playStream(url: String?) {
        currentUrl = url?.trim()
        if (currentUrl.isNullOrEmpty() || !currentUrl!!.startsWith("http")) {
            Log.e(TAG, "رابط غير صالح: $currentUrl")
            showToast("رابط البث غير صالح!")
            return
        }

        try {
            vlcMediaPlayer?.apply {
                stop()
                media?.release()
                media = Media(libVLC, android.net.Uri.parse(currentUrl)).apply {
                    addOption(":network-caching=3000")
                }
                setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            Log.d(TAG, "تم تشغيل القناة: $currentChannelName")
                            showNotification("يتم الاستماع الآن إلى: $currentChannelName")
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            Log.e(TAG, "تعذر تشغيل البث!")
                            showToast("تعذر تشغيل البث!")
                        }
                    }
                }
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تشغيل البث: ${e.message}")
            showToast("تعذر تشغيل البث!")
        }
    }

    private fun pauseStream() {
        vlcMediaPlayer?.apply {
            if (isPlaying) {
                pause()
                showNotification("مؤقت: $currentChannelName")
            } else {
                play()
                showNotification("يتم الاستماع الآن إلى: $currentChannelName")
            }
        }
    }

    private fun stopStream() {
        try {
            vlcMediaPlayer?.stop() // إيقاف تشغيل القناة
            vlcMediaPlayer?.release() // تحرير الموارد
            vlcMediaPlayer = null // إعادة تعيين المشغل
            stopSelf() // إنهاء الخدمة
            Log.i(TAG, "تم إيقاف تشغيل القناة بنجاح.")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ أثناء إيقاف تشغيل القناة: ${e.message}")
        }
    }


    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        vlcMediaPlayer?.volume = if (isMuted) 0 else 100
        showNotification(if (isMuted) "مكتوم: $currentChannelName" else "يعمل: $currentChannelName")
    }

    private fun setVolume(level: Int) {
        vlcMediaPlayer?.volume = level.coerceIn(0, 100)
    }

    private fun showNotification(contentText: String) {
        val channelId = createNotificationChannel()
        val notification = buildNotification(channelId, contentText)
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(): String {
        val channelId = "radio_channel_$currentChannelIndex"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                channelId,
                "راديو نسمات - $currentChannelName",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات الراديو المباشر"
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(this)
            }
        }
        return channelId
    }

    private fun buildNotification(channelId: String, contentText: String): Notification {
        // إنشاء Intent لإيقاف تشغيل القناة
        val stopIntent = Intent(this, RadioPlayerService::class.java).apply {
            action = "STOP" // تعريف الإجراء المطلوب
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("راديو نسمات") // عنوان الإشعار
            .setContentText(contentText) // النص الظاهر في الإشعار
            .setSmallIcon(R.drawable.ic_radio) // أيقونة صغيرة للإشعار
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_radio)) // أيقونة كبيرة
            .setContentIntent(createPendingIntent()) // الإجراء عند الضغط على الإشعار
            .setOngoing(true) // الإشعار مستمر
            .addAction(
                R.drawable.ic_stop, // الأيقونة الخاصة بزر "Stop"
                "إيقاف", // النص الظاهر بجانب الأيقونة
                stopPendingIntent // الإجراء المرتبط بزر "Stop"
            )
            .build()
    }
    private fun createPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("CURRENT_CHANNEL_NAME", currentChannelName)
                putExtra("CURRENT_CHANNEL_INDEX", currentChannelIndex)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        abandonAudioFocus()
        vlcMediaPlayer?.release()
        libVLC.release()
        Log.d(TAG, "تم تحرير الموارد وإيقاف الخدمة")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "RadioPlayerService"
        private const val NOTIFICATION_ID = 1
    }
}