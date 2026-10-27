package app.thrivo

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.units.Length
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos

data class WalkState(val running: Boolean = false, val start: Instant? = null, val points: List<Location> = emptyList(), val meters: Double = 0.0,
                     val lastSaved: String? = null, val saveSelf: Boolean = true)

object WalkTracker { val state = MutableStateFlow(WalkState()) }

class WalkService : Service(), LocationListener {
    override fun onBind(i: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finish(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("walk", "Walk tracking", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "walk").setSmallIcon(R.drawable.ic_stat_brand).setContentTitle("Walk in progress").setContentText("${APP_NAME} is tracking your route")
            .setContentIntent(open).setOngoing(true).build()
        startForeground(9, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        WalkTracker.state.value = WalkState(running = true, start = Instant.now(), saveSelf = intent?.getBooleanExtra("saveSelf", true) ?: true)
        getSystemService(LocationManager::class.java).requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, this, Looper.getMainLooper())
        return START_STICKY
    }

    override fun onLocationChanged(l: Location) {
        if (l.accuracy > 30) return                                    // ignore poor fixes
        val s = WalkTracker.state.value
        val add = s.points.lastOrNull()?.distanceTo(l)?.toDouble() ?: 0.0
        WalkTracker.state.value = s.copy(points = s.points + l, meters = s.meters + add)
    }

    private fun finish() {
        getSystemService(LocationManager::class.java).removeUpdates(this)
        val s = WalkTracker.state.value; val end = Instant.now()
        WalkTracker.state.value = s.copy(running = false)
        // workouts save their own session; the standalone Walk screen saves here
        if (s.saveSelf && s.start != null && s.meters > 20) CoroutineScope(Dispatchers.IO).launch {
            val hc = HealthConnectClient.getOrCreate(this@WalkService); val z = ZoneId.systemDefault().rules
            val route = s.points.filter { !it.time.let(Instant::ofEpochMilli).isBefore(s.start) }
                .map { ExerciseRoute.Location(time = Instant.ofEpochMilli(it.time), latitude = it.latitude, longitude = it.longitude) }
            val saved = runCatching {
                hc.insertRecords(listOf(
                    ExerciseSessionRecord(startTime = s.start, startZoneOffset = z.getOffset(s.start), endTime = end, endZoneOffset = z.getOffset(end),
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_WALKING, title = "${APP_NAME} walk",
                        exerciseRoute = if (route.size >= 2) ExerciseRoute(route) else null),
                    DistanceRecord(s.start, z.getOffset(s.start), end, z.getOffset(end), Length.meters(s.meters))))
            }.isSuccess
            WalkTracker.state.value = WalkTracker.state.value.copy(lastSaved = if (saved) "Saved to Health Connect" else "Couldn't save — check Health Connect permissions")
        }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
}

@Composable
fun WalkScreen(requestStart: () -> Unit, back: () -> Unit) = SubScreen("Walk", back) {
    val ctx = LocalContext.current
    val s by WalkTracker.state.collectAsState()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(s.running) { while (s.running) { now = Instant.now(); delay(1000) } }
    val secs = s.start?.let { Duration.between(it, if (s.running) now else (s.points.lastOrNull()?.time?.let(Instant::ofEpochMilli) ?: it)).seconds } ?: 0
    val km = s.meters / 1000
    T(if (s.running) "Walking…" else "Track a walk", 32.sp, FontWeight.Bold)
    WhiteCard {
        Row {
            CoachStat("Time", "%d:%02d:%02d".format(secs / 3600, secs / 60 % 60, secs % 60), Modifier.weight(1f))
            CoachStat("Distance", dist(s.meters, 2), Modifier.weight(1f))
            CoachStat("Pace", if (km > .05) { val u = if (Metric) km else km / 1.609344; "%d:%02d /%s".format((secs / u / 60).toInt(), (secs / u % 60).toInt(), if (Metric) "km" else "mi") } else "–", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        RouteCanvas(s.points, Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFEFF1FA)))
        // per-km splits
        if (s.points.size > 2) {
            var acc = 0.0; var lastT = s.start?.toEpochMilli() ?: s.points.first().time; var k = 1
            s.points.zipWithNext().forEach { (a, b) -> acc += a.distanceTo(b); if (acc >= k * 1000) {
                T("Km $k · %d:%02d".format(((b.time - lastT) / 60000), ((b.time - lastT) / 1000 % 60)), 12.sp, FontWeight.Medium, Ink, modifier = Modifier.padding(top = 4.dp))
                lastT = b.time; k++ } }
        }
    }
    s.lastSaved?.let { T(it, 12.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp)) }
    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50)).background(if (s.running) Coral else Color.White).clickable {
        if (s.running) ctx.startService(Intent(ctx, WalkService::class.java).setAction("stop")) else requestStart()
    }, contentAlignment = Alignment.Center) { T(if (s.running) "Finish walk" else "Start walk", 16.sp, FontWeight.Bold, if (s.running) Color.White else Brand) }
    T("Uses GPS while the walk runs (a notification shows it's active). The finished walk and route are saved to Health Connect.",
        10.sp, color = Color.White.copy(.6f), lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

fun startWalk(ctx: Context) = ctx.startForegroundService(Intent(ctx, WalkService::class.java))

@Composable
fun RouteCanvas(points: List<Location>, modifier: Modifier) = Canvas(modifier) {
    if (points.size < 2) return@Canvas
    val lat0 = points.map { it.latitude }.average(); val k = cos(Math.toRadians(lat0))
    val xs = points.map { it.longitude * k }; val ys = points.map { it.latitude }
    val w = (xs.max() - xs.min()).coerceAtLeast(1e-5); val h = (ys.max() - ys.min()).coerceAtLeast(1e-5)
    val sc = minOf((size.width - 40) / w, (size.height - 40) / h)
    fun p(i: Int) = Offset((20 + (xs[i] - xs.min()) * sc + (size.width - 40 - w * sc) / 2).toFloat(), (size.height - 20 - (ys[i] - ys.min()) * sc - (size.height - 40 - h * sc) / 2).toFloat())
    val path = Path().apply { moveTo(p(0).x, p(0).y); for (i in 1 until points.size) lineTo(p(i).x, p(i).y) }
    drawPath(path, Brand, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color(0xFF0FA958), 12f, p(0)); drawCircle(Coral, 12f, p(points.lastIndex))
}
