package app.thrivo

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.ln

private const val BRIEFING = "app.thrivo.BRIEFING"; private const val WINDDOWN = "app.thrivo.WINDDOWN"
private const val MED = "app.thrivo.MED"; private const val MED_TAKEN = "app.thrivo.MED_TAKEN"

private fun prefs(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)

/** Fire once at the next occurrence of [time]; each receiver reschedules itself for the following day. */
private fun scheduleAt(ctx: Context, code: Int, action: String, time: LocalTime, extra: Long = 0, on: Boolean = true) {
    val pi = PendingIntent.getBroadcast(ctx, code, Intent(ctx, DailyAlarms::class.java).setAction(action).putExtra("id", extra),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val am = ctx.getSystemService(AlarmManager::class.java); am.cancel(pi)
    if (!on) return
    var at = LocalDate.now().atTime(time); if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
    am.setWindow(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 10 * 60_000L, pi)
}

fun scheduleAll(ctx: Context) {
    val p = prefs(ctx)
    scheduleAt(ctx, 10, BRIEFING, LocalTime.ofSecondOfDay(p.getInt("briefingMin", 8 * 60) * 60L), on = p.getBoolean("briefing", true))
    scheduleAt(ctx, 11, WINDDOWN, LocalTime.ofSecondOfDay(p.getInt("windMin", 22 * 60 + 15) * 60L), on = p.getBoolean("winddown", true))
    meds(ctx).forEach { scheduleAt(ctx, (1000 + it.id % 100000).toInt(), MED, it.time, it.id) }
}

fun notify(ctx: Context, id: Int, channel: String, channelName: String, title: String, text: String, actions: List<Notification.Action> = emptyList(), tab: Int = 3) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).putExtra("tab", tab), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val b = Notification.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat_brand).setContentTitle(title).setContentText(text)
        .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true)
    actions.forEach { b.addAction(it) }
    nm.notify(id, b.build())
}

/** Early-warning check (Mishra 2020-style): HRV below baseline by the SWC and resting HR up ≥ 3 bpm, two mornings running. */
fun anomaly(v: Vitals): String? {
    val ln = v.hrvDaily.map { it?.takeIf { x -> x > 0 }?.let(::ln) }
    val base = ln.dropLast(2).filterNotNull(); val rBase = v.rhrDaily.dropLast(2).filterNotNull()
    if (base.size < 7 || rBase.size < 7) return null
    val m = base.average(); val sd = kotlin.math.sqrt(base.sumOf { (it - m) * (it - m) } / (base.size - 1)); val rm = rBase.average()
    val last2 = ln.takeLast(2); val r2 = v.rhrDaily.takeLast(2)
    val low = last2.all { it != null && it < m - Science.SWC * sd }; val high = r2.all { it != null && it >= rm + 3 }
    return if (low && high) "Your HRV has been below normal and resting heart rate up for 2 days. That can mean strain, poor sleep or the start of an illness — take it easy today." else null
}

class DailyAlarms : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> { scheduleAll(ctx); scheduleWidgetRefresh(ctx); Rides.register(ctx); SpeedRides.register(ctx, Repo(ctx).speedRides) }
                    BRIEFING -> {
                        val repo = Repo(ctx)
                        withTimeoutOrNull(15_000) { runCatching { repo.loadVitals() }.getOrNull() }?.let { v ->
                            val c = coach(v, repo.age, repo.female)
                            notify(ctx, 20, "briefing", "Morning briefing",
                                "Recovery ${c.recovery?.let { "$it%" } ?: "–"} · Sleep ${c.sleepScore ?: "–"}",
                                (v.nights.lastOrNull()?.let { "Slept ${fmt(it.asleep)}. " } ?: "") +
                                "Strain target today %.0f–%.0f. Bedtime tonight ~%s.".format(c.strainTarget.start, c.strainTarget.endInclusive,
                                    c.bedtime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))))
                            // move tonight's wind-down to 30 min before the suggested bedtime
                            val wind = c.bedtime.minusMinutes(30); prefs(ctx).edit().putInt("windMin", wind.hour * 60 + wind.minute).apply()
                            anomaly(v)?.let { notify(ctx, 21, "alerts", "Health alerts", "Heads up: recovery signals are off", it) }
                        }
                        scheduleAll(ctx)
                    }
                    WINDDOWN -> {
                        notify(ctx, 22, "winddown", "Wind-down", "Time to wind down 🌙",
                            "Lights low, screens away. Bed in ~30 minutes keeps you on track for your sleep need.", tab = 3)
                        scheduleAll(ctx)
                    }
                    MED -> {
                        val id = intent.getLongExtra("id", 0); val m = meds(ctx).firstOrNull { it.id == id } ?: return@launch
                        val taken = PendingIntent.getBroadcast(ctx, (2000 + id % 100000).toInt(), Intent(ctx, DailyAlarms::class.java).setAction(MED_TAKEN).putExtra("id", id),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        notify(ctx, (3000 + id % 100000).toInt(), "meds", "Medication reminders", "💊 ${m.name}", "Time for ${m.name}.",
                            listOf(Notification.Action.Builder(null, "Taken", taken).build()), tab = 2)
                        scheduleAt(ctx, (1000 + id % 100000).toInt(), MED, m.time, id)
                    }
                    MED_TAKEN -> {
                        val id = intent.getLongExtra("id", 0); markTaken(ctx, id)
                        ctx.getSystemService(NotificationManager::class.java).cancel((3000 + id % 100000).toInt())
                    }
                }
            } finally { pending.finish() }
        }
    }
}

// ---- medication store ----
data class Med(val id: Long, val name: String, val time: LocalTime)

fun meds(ctx: Context): List<Med> = runCatching {
    val a = JSONArray(prefs(ctx).getString("meds", "[]")); (0 until a.length()).map { a.getJSONObject(it).let { o -> Med(o.getLong("id"), o.getString("name"), LocalTime.parse(o.getString("time"))) } }
}.getOrDefault(emptyList())

fun saveMeds(ctx: Context, l: List<Med>) {
    prefs(ctx).edit().putString("meds", JSONArray(l.map { JSONObject().put("id", it.id).put("name", it.name).put("time", it.time.toString()) }).toString()).apply()
    scheduleAll(ctx)
}

fun markTaken(ctx: Context, id: Long) {
    val o = runCatching { JSONObject(prefs(ctx).getString("medTaken", "{}")) }.getOrDefault(JSONObject())
    o.put("$id@${LocalDate.now()}", true); prefs(ctx).edit().putString("medTaken", o.toString()).apply()
}

fun takenToday(ctx: Context, id: Long) = runCatching { JSONObject(prefs(ctx).getString("medTaken", "{}")).optBoolean("$id@${LocalDate.now()}") }.getOrDefault(false)
