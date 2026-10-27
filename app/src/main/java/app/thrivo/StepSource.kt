package app.thrivo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun appLabel(ctx: android.content.Context, pkg: String) = when (pkg) {
    "" -> "All sources (Health Connect priority)"
    FITBIT -> "Fitbit"
    "com.sec.android.app.shealth" -> "Samsung Health"
    else -> if (pkg.startsWith("com.android.healthconnect.phone")) "Phone step counter (Health Connect)" else
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}
@Composable
fun StepSourceScreen(repo: Repo, today: Today, requestActivity: () -> Unit, requestSpeed: () -> Unit, changed: () -> Unit, back: () -> Unit) = SubScreen("Data source", back) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf(repo.stepSource) }
    var excl by remember { mutableStateOf(repo.excludeRides) }
    var speed by remember { mutableStateOf(repo.speedRides) }
    var loaded by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    LaunchedEffect(Unit) { sources = runCatching { repo.stepsBySource() }.getOrDefault(emptyList()); loaded = true }
    T("Where your data comes from", 30.sp, FontWeight.Bold, lineHeight = 34.sp)
    T("Samsung Health, Google Fit and Fitbit can all write the same steps, sleep and heart data to Health Connect, which double-counts. " +
      "Pick one app and ${APP_NAME} uses only that app's data — for steps, sleep, heart rate and everything else — plus what you log here.",
        12.sp, color = Color.White.copy(.8f), lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
    WhiteCard {
        T("STEPS TODAY BY APP", 10.sp, FontWeight.Bold, Brand)
        if (sources.isEmpty()) T(if (loaded) "No steps recorded yet today" else "Loading…", 12.sp, color = Muted)
        sources.forEach { (pkg, n) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                T(appLabel(ctx, pkg), 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f)); T(fmtInt(n), 14.sp, FontWeight.Bold, Ink)
            }
        }
    }
    T("Use data from", 18.sp, FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
    WhiteCard {
        (listOf(FITBIT) + sources.map { it.first }.filter { it != FITBIT && it != ctx.packageName } + "").distinct().forEach { pkg ->
            Row(Modifier.fillMaxWidth().clickable { sel = pkg; repo.stepSource = pkg; changed() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(appLabel(ctx, pkg) + if (pkg == FITBIT) " (recommended)" else "", 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f))
                Box(Modifier.size(22.dp).clip(CircleShape).background(if (sel == pkg) Brand else Color(0xFFE6E8F0)), contentAlignment = Alignment.Center) {
                    if (sel == pkg) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
    }
    T("Rides", 18.sp, FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
    WhiteCard {
        Row(Modifier.fillMaxWidth().clickable { excl = !excl; repo.excludeRides = excl; if (excl && !Rides.granted(ctx)) requestActivity(); changed() },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("Don't count steps during rides", 14.sp, FontWeight.Bold, Ink)
                T("Trackers count road bumps and handlebar vibration as steps. Car, bus and bike rides are detected by your phone; bike workouts from your tracker count too.",
                    11.sp, color = Muted, lineHeight = 14.sp)
            }
            Switch(excl, { excl = it; repo.excludeRides = it; if (it && !Rides.granted(ctx)) requestActivity(); changed() }, colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF2F6BFF), checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE3E4EC),
                uncheckedThumbColor = Color(0xFF8A8A95), uncheckedBorderColor = Color(0xFFC9CAD6)))
        }
        if (excl && !Rides.granted(ctx)) T("Allow “Physical activity” so your phone can detect rides →", 12.sp, FontWeight.Bold, Coral,
            Modifier.padding(top = 8.dp).clickable { requestActivity() })
        if (excl) {
            Spacer(Modifier.height(10.dp)); DottedLine(Ink.copy(.12f)); Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clickable { speed = !speed; repo.speedRides = speed; if (speed) requestSpeed() else SpeedRides.register(ctx, false) },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Also detect rides by speed", 14.sp, FontWeight.Bold, Ink)
                    T("Anything sustained at ${SpeedRides.RIDE_KMH.toInt()} km/h or faster isn't walking (walking is ~5 km/h, easy running 8–12). " +
                      "Uses low-power location every 1–2 min; needs location “Allow all the time”.", 11.sp, color = Muted, lineHeight = 14.sp)
                }
                Switch(speed, { speed = it; repo.speedRides = it; if (it) requestSpeed() else SpeedRides.register(ctx, false) }, colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF2F6BFF), checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFE3E4EC),
                    uncheckedThumbColor = Color(0xFF8A8A95), uncheckedBorderColor = Color(0xFFC9CAD6)))
            }
            if (speed && !SpeedRides.granted(ctx)) T("Set location to “Allow all the time” →", 12.sp, FontWeight.Bold, Coral, Modifier.padding(top = 6.dp).clickable { requestSpeed() })
            Spacer(Modifier.height(10.dp)); DottedLine(Ink.copy(.12f)); Spacer(Modifier.height(10.dp))
            T(if (today.rideSteps > 0) "Today: ${fmtInt(today.rideSteps)} steps not counted from ${today.rideMinutes} min of riding"
              else "No rides detected today", 13.sp, FontWeight.Medium, Ink)
            val zone = java.time.ZoneId.systemDefault(); val fmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
            Rides.list(ctx).filter { it.start.atZone(zone).toLocalDate() == java.time.LocalDate.now() }.forEach { r ->
                T("${when (r.type) { "bike" -> "🚲"; "speed" -> "⚡"; else -> "🚗" }} ${r.start.atZone(zone).format(fmt)} – ${r.end?.atZone(zone)?.format(fmt) ?: "now"}", 12.sp, color = Muted,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    T("Steps you add with “+ Add” in ${APP_NAME} always count. Rides are detected only from now on, while your phone is with you.",
        10.sp, color = Color.White.copy(.6f), lineHeight = 13.sp)
}
