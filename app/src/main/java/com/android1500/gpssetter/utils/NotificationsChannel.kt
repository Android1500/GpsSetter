package com.android1500.gpssetter.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.android1500.gpssetter.R
 class NotificationsChannel{

    private fun createChannelIfNeeded(context: Context) {
        NotificationChannelCompat.Builder("set.location", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setName(context.getString(R.string.title))
            setDescription(context.getString(R.string.des))
        }.build().also {
            NotificationManagerCompat.from(context).createNotificationChannel(it)
        }
    }

    private fun createNotification(context: Context, options: (NotificationCompat.Builder) -> Unit): Notification {
        createChannelIfNeeded(context)
        return NotificationCompat.Builder(context, "set.location").apply { options(this) }.build()
    }

    fun showNotification(context: Context, options: (NotificationCompat.Builder) -> Unit): Notification {
        val notification = createNotification(context, options)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(123, notification)
        return notification
    }

    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }


}