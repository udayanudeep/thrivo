package app.thrivo

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

/** Every 2 hours between 8 AM and 10 PM, nudge the user to drink water. */
fun scheduleReminders(ctx: Context, on: Boolean) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, WaterReminder::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    am.cancel(pi)
    if (on) am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR * 2, AlarmManager.INTERVAL_HOUR * 2, pi)
}

class WaterReminder : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            scheduleReminders(ctx, ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).getBoolean("reminders", false)); return
        }
        if (LocalTime.now().hour !in 8..21) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("water", "Water reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("tab", 1), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(7, android.app.Notification.Builder(ctx, "water").setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Time for a sip 💧").setContentText("Keep the water flowing — log a glass in ${APP_NAME}.")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
