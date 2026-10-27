package app.thrivo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate

@Composable
fun TrackSummary(d: Today, v: Vitals, kg: Double, age: Int, female: Boolean, stepGoal: Int, waterGoal: Int, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val cm = remember { LabStore.get(ctx).heightCm ?: 170.0 }
    val rest24 = bmr(kg, cm, age, female)
    val dayFrac = java.time.LocalTime.now().toSecondOfDay() / 86400.0
    val estimated = d.totalKcal == null
    val total = d.totalKcal ?: (rest24 * dayFrac + d.kcal)                    // tracker total, else resting-so-far + active
    val active = d.kcal.coerceAtMost(total); val resting = d.restingKcal ?: (total - active)
    val c = remember(v, age, female) { coach(v, age, female) }
    val today = LocalDate.now(); val zone = java.time.ZoneId.systemDefault()
    val myWorkouts = remember(d) { WorkoutTracker.history(ctx).filter { it.start.atZone(zone).toLocalDate() == today } }
    val wMin = myWorkouts.sumOf { it.activeSec } / 60 + v.workouts.filter { it.start.atZone(zone).toLocalDate() == today && myWorkouts.none { m -> kotlin.math.abs(m.start.epochSecond - it.start.epochSecond) < 300 } }.sumOf { it.minutes }
    val night = v.nights.lastOrNull()?.takeIf { it.date == today }
    Column(Modifier.fillMaxWidth()) {
        T("Today", 40.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        T(today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")), 13.sp, color = Color.White.copy(.8f))
        // calories burned
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).clickable { open("metric:calories") }.padding(16.dp)) {
            T("CALORIES BURNED", 11.sp, FontWeight.Bold, Brand)
            Row(verticalAlignment = Alignment.Bottom) {
                T(fmtInt(total), 52.sp, FontWeight.Bold, Ink); T(" kcal", 16.sp, FontWeight.Medium, Ink, Modifier.padding(bottom = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(12.dp).clip(RoundedCornerShape(50))) {
                if (resting > 0) Box(Modifier.weight(resting.toFloat()).fillMaxHeight().background(Brand))
                if (active > 0) Box(Modifier.weight(active.toFloat()).fillMaxHeight().background(Coral))
                if (total <= 0) Box(Modifier.fillMaxSize().background(Color(0xFFE6E8F0)))
            }
            Row {
                Column(Modifier.weight(1f)) { NumUnit(fmtInt(resting), "kcal", 22.sp, Ink); T("Resting (BMR)", 10.sp, color = Brand) }
                Column(horizontalAlignment = Alignment.End) { NumUnit(fmtInt(active), "kcal", 22.sp, Ink); T("Active", 10.sp, color = Coral) }
            }
            T(if (estimated) "Estimated: resting energy so far today (Mifflin-St Jeor, ${fmtInt(rest24)} kcal/day) + active calories from your tracker."
              else "From your tracker via Health Connect. Active = calories above your BMR (${fmtInt(d.bmrDay ?: rest24)} kcal/day, measured overnight) during higher-effort activity (≥4× BMR). Walking alone: ${fmtInt(d.stepKcal)} kcal.", 10.sp, color = Muted, lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        // daily stats
        val stats = listOf(
            listOf(Icons.Default.DirectionsWalk, fmtInt(d.steps), "Steps · ${(d.steps * 100 / stepGoal.coerceAtLeast(1))}% of goal", "steps"),
            listOf(Icons.Default.Route, dist(d.meters), "Distance", "distance"),
            listOf(Icons.Default.Stairs, fmtInt(d.floors), "Floors", ""),
            listOf(Icons.Default.Bolt, "${c.azm} min", "Active zone minutes", "heart"),
            listOf(Icons.Default.FitnessCenter, if (wMin > 0) "$wMin min" else "–", "Workouts", ""),
            listOf(Icons.Default.WaterDrop, vol(d.waterMl), "Water · ${(d.waterMl * 100 / waterGoal.coerceAtLeast(1))}% of goal", "water"),
            listOf(Icons.Default.Bedtime, night?.let { fmt(it.asleep) } ?: "–", "Sleep last night", "sleep"),
            listOf(Icons.Default.Favorite, v.resting?.let { "$it bpm" } ?: "–", "Resting heart rate", "rhr"),
        )
        stats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { st ->
                    val ic = st[0] as ImageVector; val value = st[1] as String; val label = st[2] as String; val key = st[3] as String
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.14f)).clickable { if (key.isNotEmpty()) open("metric:$key") }.padding(14.dp)) {
                        Icon(ic, null, tint = Accent, modifier = Modifier.size(18.dp))
                        T(value, 22.sp, FontWeight.Bold, maxLines = 1); T(label, 10.sp, color = Color.White.copy(.75f), maxLines = 1)
                    }
                }
            }
        }
        if (d.rideSteps > 0) T("${fmtInt(d.rideSteps)} steps during rides not counted.", 10.sp, color = Color.White.copy(.7f), modifier = Modifier.padding(top = 6.dp))
        // 7-day history
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.12f)).padding(14.dp)) {
            T("Last 7 days", 14.sp, FontWeight.Bold)
            WeekBars("Calories burned", d.burned7.map { it ?: 0.0 }, Coral)
            WeekBars("Steps", d.history.takeLast(7).map { it.toDouble() }, Color.White)
        }
    }
}

@Composable
fun TrackerTiles(female: Boolean, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        val tiles = listOfNotNull(
            Triple(Icons.Default.WaterDrop, "Water", "waterTab"), Triple(Icons.Default.Coffee, "Caffeine", "caffeine"),
            Triple(Icons.Default.MonitorWeight, "Body", "body"), Triple(Icons.Default.Medication, "Meds", "meds"),
            Triple(Icons.Default.EditNote, "Journal", "journal"), Triple(Icons.Default.Air, "Breathe", "breathe"),
            if (female) Triple(Icons.Default.Favorite, "Cycle", "cycle") else null, Triple(Icons.Default.Science, "Health profile", "labs"))
        tiles.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (ic, l, k) -> TrackTile(ic, l, Modifier.weight(1f)) { open(k) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun WeekBars(label: String, values: List<Double>, color: Color) {
    T(label, 11.sp, color = Color.White.copy(.75f), modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        val mx = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        values.forEachIndexed { i, x -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            T(if (x >= 1000) "%.1fk".format(x / 1000) else fmtInt(x), 8.sp, color = Color.White.copy(.7f), maxLines = 1)
            Box(Modifier.fillMaxWidth(.6f).height((44 * (x / mx)).coerceAtLeast(2.0).dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(color))
            T(LocalDate.now().minusDays(6L - i).dayOfWeek.name.take(1), 9.sp, color = Color.White.copy(.7f))
        } }
    }
}

@Composable
fun TrackTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) =
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.15f)).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)); T(label, 10.sp, FontWeight.Bold, maxLines = 1)
    }

