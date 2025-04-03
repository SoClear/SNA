package cool.cmg.sna.capture

//import cool.cmg.sna.accessibility.AccessibilityGesture
import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cool.cmg.sna.DisplayConfig
import cool.cmg.sna.Game
import cool.cmg.sna.MainActivity
import cool.cmg.sna.opencv.use
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.lang.ref.WeakReference

class MediaProjectionService : Service(), AutoCloseable {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastDisplayConfig: DisplayConfig? = null
    private val coroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private val configurationChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            coroutineScope.launch {
                if (lastDisplayConfig == null || imageReader == null || virtualDisplay == null) return@launch
                val currentDisplayConfig = DisplayConfig.get(context)
                if (currentDisplayConfig == lastDisplayConfig) return@launch
                // 1. 保存旧的 surface
                val oldSurface = imageReader?.surface

                // 2. 创建新的 ImageReader
                val (width, height, densityDpi) = currentDisplayConfig
                val newImageReader = ImageReader.newInstance(
                    width, height,
                    PixelFormat.RGBA_8888,
                    2
                )

                // 3. 更新 virtualDisplay 的 surface
                virtualDisplay?.surface = newImageReader.surface

                // 4. 重设 virtualDisplay 尺寸
                virtualDisplay?.resize(width, height, densityDpi)

                // 5. 等待 virtualDisplay 更新完成
                kotlinx.coroutines.delay(100)

                // 6. 释放旧资源
                oldSurface?.release()
                imageReader?.close()

                // 7. 更新引用
                imageReader = newImageReader
                lastDisplayConfig = currentDisplayConfig
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            configurationChangedReceiver,
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED)
        )
        createNotificationChannel()
        synchronized(MediaProjectionService) {
            INSTANCE = WeakReference(this).get()
            _projectionState.value = INSTANCE
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("onStartCommand", "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(TOKEN_INTENT_EXTRA_NAME, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(TOKEN_INTENT_EXTRA_NAME)
                } ?: return START_STICKY

                startCapturing(token)
                val game = Game(this)
                coroutineScope.launch { game.start() }
            }

            ACTION_STOP -> {
//                close()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startCapturing(token: Intent) {
        startForeground(NOTIFICATION_ID, createNotification())

        val (width, height, densityDpi) = DisplayConfig.get(this).also { lastDisplayConfig = it }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mediaProjectionManager.getMediaProjection(RESULT_OK, token).apply {
            registerCallback(object : MediaProjection.Callback() {
                override fun onStop() = close()
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecording",
            width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    fun <R> takeScreenshot(action: (Mat) -> R): R? {
        return imageReader?.acquireLatestImage()?.use {
            val plane = it.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride.toLong()
            // Buffer memory isn't copied by OpenCV
            Mat(it.height, it.width, CvType.CV_8UC4, buffer, rowStride).use {
                Imgproc.cvtColor(it, it, Imgproc.COLOR_RGBA2BGR)
                action(it)
            }
        }
    }

    // 创建通知渠道
    private fun createNotificationChannel() {
        // 从 Android 8.0（API 级别 26）开始，所有通知必须分配到一个渠道。
        // 你需要在创建通知前创建通知渠道。
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Foreground Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        NotificationManagerCompat.from(this).createNotificationChannel(serviceChannel)
    }


    // 创建通知
    // 注意：创建通知渠道后才能创建通知
    // 注意：创建渠道的 channelId 必须和创建通知的 channelId 一致
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        // 创建停止录制的 PendingIntent
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaProjectionService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SNA")
            .setContentText("服务正在运行...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    override fun close() {
        coroutineScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // 先关 virtualDisplay，再关 imageReader，否则报错 BufferQueue has been abandoned
        virtualDisplay?.surface = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        synchronized(MediaProjectionService) {
            _projectionState.value = null
            INSTANCE = null
        }
        unregisterReceiver(configurationChangedReceiver)
        close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "MyForegroundServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val TOKEN_INTENT_EXTRA_NAME = "MEDIA_PROJECTION_TOKEN"
        private const val ACTION_START = "START"
        private const val ACTION_STOP = "STOP"

        init {
            OpenCVLoader.initLocal()
        }

        fun start(context: Context, mediaProjectionToken: Intent) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_START
                putExtra(TOKEN_INTENT_EXTRA_NAME, mediaProjectionToken)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        @Volatile
        var INSTANCE: MediaProjectionService? = null
            private set

        // 使用 MutableStateFlow 来持有当前服务实例（或 null）
        private val _projectionState = MutableStateFlow<MediaProjectionService?>(INSTANCE)
        // 对外暴露为不可变的 StateFlow
        val projectionState: StateFlow<MediaProjectionService?> = _projectionState.asStateFlow()
    }
}