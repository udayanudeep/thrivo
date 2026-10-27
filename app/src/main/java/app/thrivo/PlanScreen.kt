package app.thrivo

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PlanContent(d: Today, goal: Int, name: String, walk: WalkSlot?, todayWorkout: Day?, onLocate: () -> Unit, onSetGoal: () -> Unit, onStreaks: () -> Unit, onPlan: () -> Unit,
               onWorkout: () -> Unit, onHistory: () -> Unit) {
    val ctx = LocalContext.current
    val (cur, best) = streaks(d.history, goal)
    val left = (goal - d.steps).coerceAtLeast(0)
    run {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { Spacer(Modifier.weight(1f)); CircleBtn(Icons.Default.LocalFireDepartment, onClick = onStreaks) }
            HeroArt(Modifier.fillMaxWidth().height(250.dp).padding(horizontal = 4.dp))
            Spacer(Modifier.height(18.dp)); DottedLine(); Spacer(Modifier.height(26.dp))
            T(if (left == 0L) "Goal crushed\ntoday!" else "Your plan\nfor today", 44.sp, FontWeight.Bold, lineHeight = 46.sp)
            Spacer(Modifier.height(10.dp))
            T(if (left == 0L) "You walked ${fmtInt(d.steps)} steps. Keep the streak alive tomorrow — same time, same energy."
              else "Walk ${fmtInt(left)} more steps to hit your goal. A brisk 30‑minute walk is about 3,000 steps — you've got this.",
                12.sp, FontWeight.Medium, lineHeight = 16.sp)
            Spacer(Modifier.height(14.dp))
            // Golden hour walk slot
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFFF8A00), Color(0xFFFFC21A))))
                .clickable(onClick = onLocate).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WbSunny, null, tint = Ink, modifier = Modifier.size(16.dp))
                    T(if (walk?.golden == true) "  YOUR GOLDEN HOUR" else "  BEST TIME TO WALK", 11.sp, FontWeight.Bold, Ink)
                }
                if (walk == null) T("Tap to find your best walking time today from local weather and sunlight.", 13.sp, FontWeight.Medium, Ink, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp))
                else {
                    T(walk.time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), 40.sp, FontWeight.Bold, Ink)
                    T("~3,000-step walk · %.0f°C · %d%% rain · UV %.0f".format(walk.tempC, walk.rainPct, walk.uv), 12.sp, FontWeight.Medium, Ink)
                    T("Sunrise ${walk.sunrise.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))} · Sunset ${walk.sunset.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))} · Weather: Open-Meteo",
                        10.sp, color = Ink.copy(.7f), modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            todayWorkout?.let { w ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.18f)).clickable(onClick = onPlan).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        T("TODAY'S WORKOUT · ~${w.minutes} MIN", 11.sp, FontWeight.Bold, Accent); T(w.title, 16.sp, FontWeight.Bold)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
                }
            }
            // workouts live on the Plan tab
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple(Icons.Default.PlayArrow, "Start workout", onWorkout), Triple(Icons.Default.EventNote, "5-day plan", onPlan),
                    Triple(Icons.Default.History, "History", onHistory)).forEach { (ic, l, go) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (l == "Start workout") Color.White else Color.White.copy(.18f))
                        .clickable(onClick = go).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, tint = if (l == "Start workout") Brand else Color.White, modifier = Modifier.size(22.dp))
                        T(l, 11.sp, FontWeight.Bold, if (l == "Start workout") Brand else Color.White)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onSetGoal).padding(horizontal = 36.dp, vertical = 14.dp)) {
                T("Set goal", 14.sp, FontWeight.Bold, Brand)
            }
            Spacer(Modifier.height(26.dp)); DottedLine(); Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Streaks", 46.sp, FontWeight.Bold, modifier = Modifier.weight(1f))
                CircleBtn(Icons.AutoMirrored.Filled.KeyboardArrowRight, 46.dp, bg = Color.White.copy(.3f), onClick = onStreaks)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                T("  Daily Step Goal:  ${fmtInt(goal)}", 12.sp, FontWeight.Medium)
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { T(fmtInt(left), 30.sp, FontWeight.Bold); T("Steps to keep streak", 12.sp, FontWeight.Medium) }
                StreakDial((d.steps.toFloat() / goal).coerceIn(0f, 1f), Modifier.size(130.dp, 70.dp))
            }
            Spacer(Modifier.height(22.dp))
            Row {
                Column(Modifier.weight(1f)) { T("${cur}d", 40.sp, FontWeight.Bold); T("Current Streak", 12.sp, FontWeight.Medium) }
                Column(Modifier.weight(1f)) { T("${best}d", 40.sp, FontWeight.Bold); T("Longest Streak", 12.sp, FontWeight.Medium) }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(78.dp, 64.dp).clip(RoundedCornerShape(50)).background(Ink), contentAlignment = Alignment.Center) { BrandMark(30.dp) }
                Column(Modifier.padding(start = 12.dp)) {
                    T("DAYS ON GOAL · LAST 30", 10.sp, FontWeight.Bold, Ink)
                    T("${d.history.takeLast(30).count { it >= goal }} OF 30 DAYS", 26.sp, FontWeight.Bold, Ink)
                }
            }
            Spacer(Modifier.height(20.dp)); DottedLine(); Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.White.copy(.15f)).clickable {
                shareStreakCard(ctx, name, d.steps, cur, best)
            }.padding(18.dp)) {
                Row { listOf(Icons.Default.Favorite, Icons.Default.DirectionsWalk, Icons.Default.EmojiEvents).forEach {
                    Icon(it, null, tint = Color.White.copy(.5f), modifier = Modifier.padding(end = 4.dp).size(20.dp)) } }
                T("Share your streak", 26.sp, FontWeight.Bold, Color.White.copy(.55f))
                T("Share a streak card image with friends.", 11.sp, color = Color.White.copy(.5f))
            }
        }
    }
}

/** Stadium with radial hatch lines; yellow fill shows today's progress. */
@Composable
fun StreakDial(progress: Float, modifier: Modifier) = Canvas(modifier) {
    val h = size.height; val w = size.width; val r = h / 2
    val n = 64
    for (i in 0 until n) {
        val t = i / n.toFloat(); val on = t <= progress
        // walk the stadium perimeter: top straight, right arc, bottom straight, left arc
        val straight = w - h; val per = 2 * straight + 2 * Math.PI.toFloat() * r; var s = t * per
        val (p, a) = when {
            s < straight -> Offset(r + s, 0f) to -Math.PI.toFloat() / 2
            s < straight + Math.PI * r -> { val ang = -Math.PI.toFloat() / 2 + (s - straight) / r; Offset(w - r + r * cos(ang), r + r * sin(ang)) to ang }
            s < 2 * straight + Math.PI * r -> { s -= straight + Math.PI.toFloat() * r; Offset(w - r - s, h) to Math.PI.toFloat() / 2 }
            else -> { val ang = Math.PI.toFloat() / 2 + (s - 2 * straight - Math.PI.toFloat() * r) / r; Offset(r + r * cos(ang), r + r * sin(ang)) to ang }
        }
        val inner = Offset(p.x - cos(a) * h * .22f, p.y - sin(a) * h * .22f)
        drawLine(if (on) Accent else Color.White.copy(.35f), p, inner, 2f)
    }
    drawRoundRect(if (progress > 0) Accent.copy(.9f) else Color.White.copy(.12f), Offset(h * .3f, h * .3f),
        androidx.compose.ui.geometry.Size(w - h * .6f, h * .4f), androidx.compose.ui.geometry.CornerRadius(h * .2f))
}

/** Streak history: last 30 days as a dot calendar. */
@Composable
fun StreaksScreen(d: Today, goal: Int, back: () -> Unit) {
    val (cur, best) = streaks(d.history, goal)
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Box(Modifier.fillMaxWidth()) {
            CircleBtn(Icons.AutoMirrored.Filled.ArrowBack, 36.dp, label = "Back", onClick = back)
            T("Streaks", 14.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(24.dp))
        Row {
            Column(Modifier.weight(1f)) { T("${cur}d", 56.sp, FontWeight.Bold); T("Current Streak", 12.sp) }
            Column(Modifier.weight(1f)) { T("${best}d", 56.sp, FontWeight.Bold); T("Longest Streak", 12.sp) }
        }
        Spacer(Modifier.height(24.dp)); DottedLine(); Spacer(Modifier.height(20.dp))
        T("Last 30 days", 20.sp, FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val days = d.history.takeLast(30)
        days.chunked(7).forEachIndexed { row, week ->
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                week.forEachIndexed { i, v ->
                    val date = LocalDate.now().minusDays((29 - (row * 7 + i)).toLong())
                    Box(Modifier.size(38.dp).clip(CircleShape).background(when { v >= goal -> Accent; v > 0 -> Color.White.copy(.3f); else -> Color.White.copy(.1f) }),
                        contentAlignment = Alignment.Center) { T("${date.dayOfMonth}", 12.sp, FontWeight.Bold, if (v >= goal) Ink else Color.White) }
                }
            }
        }
    }
}

/** The merged Plan tab: today's numbers (calories, steps, sleep…), then the plan, streaks and the other trackers. */
@Composable
fun PlanTab(d: Today, v: Vitals, kg: Double, age: Int, female: Boolean, stepGoal: Int, waterGoal: Int, name: String, walk: WalkSlot?, todayWorkout: Day?,
            onLocate: () -> Unit, onSetGoal: () -> Unit, onStreaks: () -> Unit, onPlan: () -> Unit, onWorkout: () -> Unit, onHistory: () -> Unit,
            open: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Orange.copy(.55f), Color.Transparent), Offset(0f, 2000f), 800f)))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            TrackSummary(d, v, kg, age, female, stepGoal, waterGoal, open)
            Spacer(Modifier.height(18.dp)); DottedLine(); Spacer(Modifier.height(10.dp))
            PlanContent(d, stepGoal, name, walk, todayWorkout, onLocate, onSetGoal, onStreaks, onPlan, onWorkout, onHistory)
            T("Trackers", 22.sp, FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            TrackerTiles(female, open)
            Spacer(Modifier.height(120.dp))
        }
    }
}
