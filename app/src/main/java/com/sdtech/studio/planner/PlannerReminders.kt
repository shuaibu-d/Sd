package com.sdtech.studio.planner

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sdtech.studio.MainActivity

object PlannerReminders {
    const val CHANNEL_ID = "planner_reminders"
    fun schedule(context: Context, itemId: Long, title: String, atMillis: Long) {
        createChannel(context)
        val intent = Intent(context, PlannerReminderReceiver::class.java).apply {
            putExtra("item_id", itemId); putExtra("title", title)
        }
        val pending = PendingIntent.getBroadcast(context, itemId.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (atMillis > System.currentTimeMillis()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }
    fun cancel(context: Context, itemId: Long) {
        val intent = Intent(context, PlannerReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, itemId.toInt(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
    }
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Planner reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}

class PlannerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlannerReminders.createChannel(context)
        val itemId = intent.getLongExtra("item_id", 0L)
        val notification = NotificationCompat.Builder(context, PlannerReminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(intent.getStringExtra("title") ?: "Your scheduled post")
            .setContentText("It's time to review your content plan.")
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, itemId.toInt(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(itemId.toInt(), notification)
    }
}
