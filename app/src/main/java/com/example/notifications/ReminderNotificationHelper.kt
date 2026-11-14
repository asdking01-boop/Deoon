package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object ReminderNotificationHelper {
    const val CHANNEL_ID = "payment_reminders_channel"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تذكيرات الدفع - Payment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات مواعيد استحقاق الديون والدفعات"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, notificationId: Int, title: String, message: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun scheduleReminder(context: Context, reminderId: String, triggerAtMillis: Long, title: String, message: String) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
        }
        val reqCode = reminderId.hashCode()
        val pending = PendingIntent.getBroadcast(
            context,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val fireAt = maxOf(triggerAtMillis, System.currentTimeMillis() + 5_000L)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending)
    }
}

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderNotificationHelper.EXTRA_REMINDER_ID) ?: "rem"
        val title = intent.getStringExtra(ReminderNotificationHelper.EXTRA_TITLE) ?: "تذكير دفعة مستحقة"
        val message = intent.getStringExtra(ReminderNotificationHelper.EXTRA_MESSAGE) ?: ""
        ReminderNotificationHelper.showNotification(context, id.hashCode(), title, message)
    }
}
