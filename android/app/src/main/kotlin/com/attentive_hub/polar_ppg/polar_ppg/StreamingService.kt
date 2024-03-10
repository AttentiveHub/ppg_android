package com.attentive_hub.polar_ppg.polar_ppg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class StreamingService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        // Return null as this is a started service, not a bound service.
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Initialize your SDK and other components here

        // Create a notification channel and start foreground service
        startForegroundServiceWithNotification()

        // Return START_NOT_STICKY if you want the service to not restart automatically if it gets terminated.
        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            // Prior to Oreo, no notification channel is needed.
            ""
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlag)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Polar URA")
            .setContentText("Polar URA is running in the background.")
            .setSmallIcon(R.mipmap.ura)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel(): String{
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel("streaming_service", "Streaming Service", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
        return "streaming_service"
    }
}
