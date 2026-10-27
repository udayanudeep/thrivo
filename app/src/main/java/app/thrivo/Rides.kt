package app.thrivo

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * Wrist trackers count road bumps and handlebar vibration as steps. We record when the phone's activity
 * recognition says you're in a vehicle or on a bike, and the app then removes steps logged during those rides.
 */
data class Ride(val start: Instant, val end: Instant?, val type: String)

object Rides {
    private fun p(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)
    fun list(ctx: Context): List<Ride> = runCatching {
        val a = JSONArray(p(ctx).getString("rides", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Ride(Instant.ofEpochMilli(o.getLong("s")), o.optLong("e", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli), o.getString("t")) } }
    }.getOrDefault(emptyList())
    private fun save(ctx: Context, l: List<Ride>) = p(ctx).edit().putString("rides", JSONArray(l.takeLast(3000).map {
        JSONObject().put("s", it.start.toEpochMilli()).put("e", it.end?.toEpochMilli() ?: 0).put("t", it.type) }).toString()).apply()

    fun begin(ctx: Context, at: Instant, type: String) {
        val l = list(ctx); if (l.lastOrNull()?.end == null && l.isNotEmpty()) return     // already in a ride (any detector)
        save(ctx, l + Ride(at, null, type))
    }
    fun finish(ctx: Context, at: Instant) {
        val l = list(ctx).toMutableList(); val last = l.lastOrNull() ?: return
        if (last.end != null) return
        // a ride left open for hours is a missed exit event — cap it at 3 h
        l[l.lastIndex] = last.copy(end = minOf(at, last.start.plus(Duration.ofHours(3))))
        save(ctx, l)
    }
    fun granted(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        if (!granted(ctx)) return
        val types = listOf(DetectedActivity.IN_VEHICLE, DetectedActivity.ON_BICYCLE, DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.STILL)
        val transitions = types.flatMap { t -> listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
            ActivityTransition.Builder().setActivityType(t).setActivityTransition(it).build() } }
        val pi = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, RideReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        runCatching { ActivityRecognition.getClient(ctx).requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi) }
    }
}

class RideReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { e ->
            // event time is in elapsed-realtime; convert to wall clock
            val at = Instant.ofEpochMilli(System.currentTimeMillis() - (SystemClock.elapsedRealtimeNanos() - e.elapsedRealTimeNanos) / 1_000_000)
            val riding = e.activityType == DetectedActivity.IN_VEHICLE || e.activityType == DetectedActivity.ON_BICYCLE
            when {
                riding && e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER ->
                    Rides.begin(ctx, at, if (e.activityType == DetectedActivity.IN_VEHICLE) "vehicle" else "bike")
                riding && e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_EXIT -> Rides.finish(ctx, at)
                !riding && e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER -> Rides.finish(ctx, at)
            }
        }
    }
}

/** Merge overlapping [start, end) intervals. */
fun mergeIntervals(l: List<Pair<Instant, Instant>>): List<Pair<Instant, Instant>> {
    val out = mutableListOf<Pair<Instant, Instant>>()
    l.sortedBy { it.first }.forEach { iv -> val last = out.lastOrNull()
        if (last != null && !iv.first.isAfter(last.second)) out[out.lastIndex] = last.first to maxOf(last.second, iv.second) else out += iv }
    return out
}
