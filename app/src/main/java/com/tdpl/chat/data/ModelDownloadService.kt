package com.tdpl.chat.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tdpl.chat.MainActivity
import com.tdpl.chat.R
import com.tdpl.chat.TDPLApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs the one-time model download as a foreground service so it survives the
 * user backgrounding the app on a slow connection. Not started again once
 * ModelDownloadManager reports hasLocalModel() == true.
 */
class ModelDownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val channelId = "model_download"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, buildNotification("Preparing model…", 0))

        val manifestUrl = intent?.getStringExtra(EXTRA_MANIFEST_URL) ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        val app = application as TDPLApp
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Observe state concurrently with the actual work, not after it —
        // otherwise progress updates are missed entirely since ensureModelReady
        // runs to completion before a sequential .collect() would ever start.
        scope.launch {
            app.modelManager.state.collect { st ->
                val text = when (st) {
                    is ModelState.RestoringFromBackup -> st.message
                    is ModelState.Downloading -> if (st.bytesTotal > 0) {
                        "Downloading model… ${(st.bytesDone * 100 / st.bytesTotal)}%"
                    } else "Downloading model…"
                    else -> null
                }
                val progress = (st as? ModelState.Downloading)
                    ?.takeIf { it.bytesTotal > 0 }
                    ?.let { (it.bytesDone * 100 / it.bytesTotal).toInt() } ?: 0

                if (text != null) nm.notify(1, buildNotification(text, progress))

                if (st is ModelState.Ready || st is ModelState.Error) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        scope.launch {
            app.modelManager.ensureModelReady(manifestUrl)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Model setup", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Setting up your assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        const val EXTRA_MANIFEST_URL = "manifest_url"
        fun start(context: Context, manifestUrl: String) {
            val intent = Intent(context, ModelDownloadService::class.java)
                .putExtra(EXTRA_MANIFEST_URL, manifestUrl)
            context.startForegroundService(intent)
        }
    }
}
