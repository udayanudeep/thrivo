package app.thrivo

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ======================= exercise library (bundled public-domain images) =======================
data class ExInfo(val id: String, val name: String, val primary: List<String>, val secondary: List<String>, val equipment: String, val steps: List<String>, val url: String)

object ExLib {
    private var cache: Map<String, ExInfo>? = null
    private val imgs = HashMap<String, ImageBitmap?>()
    fun all(ctx: Context): Map<String, ExInfo> = cache ?: runCatching {
        val o = JSONObject(ctx.assets.open("exercises/index.json").bufferedReader().readText())
        o.keys().asSequence().associateWith { k -> val e = o.getJSONObject(k)
            fun l(n: String) = e.optJSONArray(n)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            ExInfo(k, e.getString("name"), l("primary"), l("secondary"), e.optString("equipment"), l("steps"), e.optString("url")) }
    }.getOrDefault(emptyMap()).also { cache = it }
    fun image(ctx: Context, id: String, frame: Int): ImageBitmap? = imgs.getOrPut("$id/$frame") {
        runCatching { ctx.assets.open("exercises/$id/$frame.jpg").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull() }
}

/** Shows the start and end position, alternating like a flip-book. */
@Composable
fun ExerciseImage(id: String, modifier: Modifier) {
    val ctx = LocalContext.current
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(id) { while (true) { delay(1100); frame = 1 - frame } }
    Crossfade(frame, label = "ex") { f ->
        ExLib.image(ctx, id, f)?.let { Image(it, ExLib.all(ctx)[id]?.name, modifier, contentScale = ContentScale.Crop) }
            ?: Box(modifier.background(Color(0xFFE6E8F0)))
    }
}

// ======================= voice cues =======================
class Voice(ctx: Context) {
    private var ready = false
    private val tts = TextToSpeech(ctx.applicationContext) { ready = it == TextToSpeech.SUCCESS }
    fun say(s: String) { if (ready) tts.speak(s, TextToSpeech.QUEUE_ADD, null, s.hashCode().toString()) }
    fun close() = tts.shutdown()
}

/** Treadmill incline limit (the owner's machine goes to 20%). */
const val MAX_INCLINE = 20.0

fun spd(kmh: Double) = if (Metric) "%.1f".format(kmh) else "%.1f".format(kmh / 1.609344)
val spdUnit get() = if (Metric) "km/h" else "mph"
fun paceStr(sec: Long, meters: Double): String {
    val unit = if (Metric) meters / 1000 else meters / 1609.344; if (unit < 0.05) return "–"
    val p = sec / unit; return "%d:%02d".format((p / 60).toInt(), (p % 60).toInt())
}
fun hms(sec: Long) = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

// ======================= picker =======================
@Composable
fun WorkoutPickerScreen(kg: Double, setKg: (Double) -> Unit, start: (Act, Program?) -> Unit, history: () -> Unit, plan: () -> Unit, back: () -> Unit) =
    SubScreen("Workout", back) {
        var kgEdit by remember { mutableStateOf(false) }
        T("Start a workout", 32.sp, FontWeight.Bold)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("My 5-day plan", plan); Chip("History", history); Chip("Weight ${weight(kg)}") { kgEdit = true }
        }
        T("Treadmill programs", 18.sp, FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PROGRAMS.forEach { p ->
                Column(Modifier.width(200.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).clickable { start(Act.TREADMILL, p) }.padding(14.dp)) {
                    T(p.name, 18.sp, FontWeight.Bold, Ink)
                    T("${p.steps.sumOf { it.first }.toInt()} min", 12.sp, FontWeight.Bold, Brand)
                    T(p.about, 11.sp, color = Muted, lineHeight = 14.sp, maxLines = 3)
                }
            }
        }
        T("Choose an activity", 18.sp, FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Act.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Glass).clickable { start(a, null) }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(a.icon, null, tint = Color.White, modifier = Modifier.size(26.dp)); T(a.label, 11.sp, FontWeight.Bold, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        T("Calories use the ACSM metabolic equations (speed + incline, cycling power) and the Compendium of Physical Activities. Your Fitbit's heart rate is added to each workout after it syncs.",
            10.sp, color = Color.White.copy(.6f), lineHeight = 13.sp)
        if (kgEdit) {
            var t by remember { mutableStateOf("%.1f".format(kg)) }
            AlertDialog({ kgEdit = false }, containerColor = Color.White, title = { T("Body weight (kg)", 20.sp, FontWeight.Bold, Ink) },
                text = { OutlinedTextField(t, { t = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true) },
                confirmButton = { TextButton({ t.toDoubleOrNull()?.takeIf { it in 30.0..300.0 }?.let(setKg); kgEdit = false }) { T("Save", 16.sp, FontWeight.Bold, Brand) } })
        }
    }

// ======================= live workout =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveWorkoutScreen(kg: Double, planDay: Day?, onFinish: () -> Unit, onDiscard: () -> Unit, minimize: () -> Unit) {
    val ctx = LocalContext.current
    val l = WorkoutTracker.live.collectAsState().value ?: return
    val gps by WalkTracker.state.collectAsState()
    var now by remember { mutableStateOf(Instant.now()) }
    var confirm by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val voice = remember { Voice(ctx) }
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false; voice.close() } }
    // tick + spoken cues at segment changes and each km
    var lastSeg by remember { mutableStateOf<Seg?>(null) }; var lastKm by remember { mutableIntStateOf(0) }
    LaunchedEffect(l.start) {
        while (true) {
            now = Instant.now()
            val cur = WorkoutTracker.live.value?.let { WorkoutTracker.current(it, now) }
            val lv = WorkoutTracker.live.value
            if (lv != null && lv.pausedAt == null && cur != null && lastSeg != null && cur.start != lastSeg!!.start && lv.program != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                voice.say("${cur.label.ifEmpty { "Next" }}. ${spd(cur.kmh)} ${if (Metric) "kilometres" else "miles"} per hour, ${"%.0f".format(cur.incline)} percent incline.")
            }
            lastSeg = cur
            if (lv != null) {
                val (m, _, _) = WorkoutTracker.totals(lv, kg, WalkTracker.state.value.meters, 0.0, now)
                val km = (if (Metric) m / 1000 else m / 1609.344).toInt()
                if (km > lastKm && lv.act.mode != Mode.STRENGTH && lv.act.mode != Mode.MET) {
                    voice.say("$km ${if (Metric) "kilometre" else "mile"}${if (km > 1) "s" else ""}. Pace ${paceStr(WorkoutTracker.activeSec(lv, now), m).replace(":", " minutes ")} seconds.")
                }
                lastKm = km
            }
            delay(1000)
        }
    }
    val sec = WorkoutTracker.activeSec(l, now)
    val cur = WorkoutTracker.current(l, now)
    val next = WorkoutTracker.nextSeg(l, now)
    val gpsKmh = gps.points.lastOrNull()?.let { if (it.hasSpeed()) it.speed * 3.6 else null }?.toDouble() ?: 0.0
    val (meters, elev, kcal) = WorkoutTracker.totals(l, kg, gps.meters, gpsKmh, now)
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleBtn(Icons.Default.KeyboardArrowDown, 36.dp, label = "Minimise", onClick = minimize)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                T(l.program ?: l.act.label, 16.sp, FontWeight.Bold); T(if (l.pausedAt != null) "Paused" else "In progress", 11.sp, color = if (l.pausedAt != null) Accent else Color.White.copy(.7f))
            }
            Icon(l.act.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            T(hms(sec), 72.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), lineHeight = 74.sp)
            // program banner
            if (l.program != null && cur != null && cur.end != null && l.act.mode == Mode.TREADMILL) {
                val left = Duration.between(now, cur.end).seconds.coerceAtLeast(0)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(20.dp)).background(Accent).padding(14.dp)) {
                    T("NOW · ${cur.label.uppercase()}", 11.sp, FontWeight.Bold, Ink)
                    T("${spd(cur.kmh)} $spdUnit · ${"%.1f".format(cur.incline)}% · ${hms(left)} left", 18.sp, FontWeight.Bold, Ink)
                    next?.let { T("Next: ${it.label} — ${spd(it.kmh)} $spdUnit, ${"%.1f".format(it.incline)}%", 12.sp, FontWeight.Medium, Ink.copy(.75f)) }
                }
            }
            when (l.act.mode) {
                Mode.TREADMILL -> {
                    AdjustCard("Speed", spd(cur?.kmh ?: 0.0), spdUnit, listOf(-.5, -.1, .1, .5), listOf(3.0, 4.5, 5.5, 6.5, 8.0, 10.0, 12.0)) { d, abs ->
                        WorkoutTracker.set(ctx, kmh = (abs ?: ((cur?.kmh ?: 0.0) + d * (if (Metric) 1.0 else 1.609344))).coerceIn(0.0, 25.0)) }
                    AdjustCard("Incline", "%.1f".format(cur?.incline ?: 0.0), "%", listOf(-1.0, -.5, .5, 1.0), listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 15.0, 18.0, 20.0)) { d, abs ->
                        WorkoutTracker.set(ctx, incline = (abs ?: ((cur?.incline ?: 0.0) + d)).coerceIn(-3.0, MAX_INCLINE)) }
                }
                Mode.BIKE -> AdjustCard("Power", "${cur?.watts ?: 0}", "W", listOf(-25.0, -10.0, 10.0, 25.0), listOf(50.0, 75.0, 100.0, 125.0, 150.0, 200.0)) { d, abs ->
                    WorkoutTracker.set(ctx, watts = (abs ?: ((cur?.watts ?: 0) + d)).toInt().coerceIn(0, 600)) }
                Mode.GPS -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveStat("Speed", spd(gpsKmh), spdUnit, Modifier.weight(1f)); LiveStat("Avg speed", spd(if (sec > 0) meters / 1000 / (sec / 3600.0) else 0.0), spdUnit, Modifier.weight(1f))
                    }
                    RouteCanvas(gps.points, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.1f)))
                    if (gps.points.isEmpty()) T("Waiting for GPS…", 12.sp, color = Color.White.copy(.7f), modifier = Modifier.padding(top = 6.dp))
                }
                Mode.STRENGTH -> StrengthLogger(l, planDay)
                Mode.MET -> T("Tracking time and estimated calories for ${l.act.label.lowercase()}.", 13.sp, color = Color.White.copy(.8f), modifier = Modifier.padding(vertical = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (l.act.mode == Mode.TREADMILL || l.act.mode == Mode.GPS) {
                    LiveStat("Distance", dist(meters, 2).substringBefore(" "), if (Metric) "km" else "mi", Modifier.weight(1f))
                    LiveStat("Pace", paceStr(sec, meters), if (Metric) "/km" else "/mi", Modifier.weight(1f))
                }
                LiveStat("Active", fmtInt(kcal), "kcal", Modifier.weight(1f))
            }
            if (l.act.mode == Mode.TREADMILL) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveStat("Elevation", "%.0f".format(if (Metric) elev else elev * 3.281), if (Metric) "m" else "ft", Modifier.weight(1f))
                LiveStat("Laps", "${l.laps.size}", "", Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigBtn("Lap", Glass, Color.White, Modifier.weight(1f)) { WorkoutTracker.lap(ctx); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            BigBtn(if (l.pausedAt != null) "Resume" else "Pause", Color.White, Brand, Modifier.weight(1.3f)) {
                if (l.pausedAt != null) WorkoutTracker.resume(ctx) else WorkoutTracker.pause(ctx) }
            BigBtn("Finish", Coral, Color.White, Modifier.weight(1f)) { confirm = true }
        }
    }
    if (confirm) AlertDialog({ confirm = false }, containerColor = Color.White, title = { T("Finish workout?", 20.sp, FontWeight.Bold, Ink) },
        text = { T("${hms(sec)} · ${fmtInt(kcal)} kcal${if (meters > 0) " · " + dist(meters, 2) else ""}", 14.sp, color = Ink) },
        confirmButton = { TextButton({ confirm = false; onFinish() }) { T("Save", 16.sp, FontWeight.Bold, Brand) } },
        dismissButton = { Row {
            TextButton({ confirm = false; onDiscard() }) { T("Discard", 16.sp, color = Coral) }
            TextButton({ confirm = false }) { T("Keep going", 16.sp, color = Ink) } } })
}

@Composable
fun AdjustCard(label: String, value: String, unit: String, steps: List<Double>, presets: List<Double>, change: (Double, Double?) -> Unit) =
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp)) {
        T(label.uppercase(), 11.sp, FontWeight.Bold, Brand)
        Row(verticalAlignment = Alignment.CenterVertically) {
            steps.take(2).forEach { s -> StepBtn(if (s <= -1 || s <= -10) "−${fmtStep(-s)}" else "−${fmtStep(-s)}") { change(s, null) } }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NumUnit(value, " $unit", 40.sp, Ink) }
            steps.drop(2).forEach { s -> StepBtn("+${fmtStep(s)}") { change(s, null) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.forEach { p -> T(if (unit == "km/h" || unit == "mph") spd(p) else fmtStep(p), 13.sp, FontWeight.Bold, Ink,
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE9EBF5)).clickable { change(0.0, p) }.padding(horizontal = 12.dp, vertical = 6.dp)) }
        }
    }
private fun fmtStep(v: Double) = if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)

@Composable
fun StepBtn(t: String, onClick: () -> Unit) = Box(Modifier.padding(2.dp).size(48.dp).clip(CircleShape).background(Brand).clickable(onClick = onClick),
    contentAlignment = Alignment.Center) { T(t, 13.sp, FontWeight.Bold) }

@Composable
fun LiveStat(label: String, value: String, unit: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.14f)).padding(12.dp)) {
        T(label, 11.sp, color = Color.White.copy(.7f)); NumUnit(value, if (unit.isEmpty()) "" else " $unit", 24.sp)
    }

@Composable
fun BigBtn(t: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.height(56.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) { T(t, 16.sp, FontWeight.Bold, fg) }

@Composable
fun StrengthLogger(l: Live, planDay: Day?) {
    val ctx = LocalContext.current
    val list = planDay?.let { it.main + it.core }?.map { ExLib.all(ctx)[it.id]?.name ?: it.id } ?: STRENGTH_EXERCISES
    var ex by remember { mutableStateOf(list.firstOrNull { name -> l.sets.none { it.exercise == name } } ?: list.first()) }
    var reps by remember { mutableIntStateOf(12) }; var kgW by remember { mutableDoubleStateOf(10.0) }
    var restUntil by remember { mutableStateOf<Instant?>(null) }; var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(restUntil) { while (restUntil != null) { now = Instant.now(); if (now.isAfter(restUntil)) restUntil = null; delay(500) } }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp)) {
        T("EXERCISE", 11.sp, FontWeight.Bold, Brand)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { n -> val done = l.sets.count { it.exercise == n }
                T(if (done > 0) "$n ·$done" else n, 13.sp, FontWeight.Bold, if (ex == n) Color.White else Ink, Modifier.clip(RoundedCornerShape(50))
                    .background(if (ex == n) Brand else Color(0xFFE9EBF5)).clickable { ex = n }.padding(horizontal = 12.dp, vertical = 6.dp)) }
        }
        val id = ExLib.all(ctx).values.firstOrNull { it.name == ex }?.id
        if (id != null) ExerciseImage(id, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(16.dp)))
        planDay?.let { d -> (d.main + d.core).firstOrNull { ExLib.all(ctx)[it.id]?.name == ex }?.let { rx ->
            T("Target: ${rx.sets} × ${rx.reps}${if (rx.restSec > 0) " · rest ${rx.restSec}s" else ""} — ${rx.cue}", 12.sp, color = Muted, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp)) } }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T("Reps", 11.sp, color = Muted); Row(verticalAlignment = Alignment.CenterVertically) {
                StepBtn("−") { reps = (reps - 1).coerceAtLeast(1) }; T("$reps", 22.sp, FontWeight.Bold, Ink, Modifier.padding(horizontal = 6.dp)); StepBtn("+") { reps++ } } }
            Column(Modifier.weight(1f)) { T("Weight (kg)", 11.sp, color = Muted); Row(verticalAlignment = Alignment.CenterVertically) {
                StepBtn("−") { kgW = (kgW - 2.5).coerceAtLeast(0.0) }; T(fmtStep(kgW), 22.sp, FontWeight.Bold, Ink, Modifier.padding(horizontal = 6.dp)); StepBtn("+") { kgW += 2.5 } } }
        }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp).clip(RoundedCornerShape(50)).background(Brand).clickable {
            WorkoutTracker.addSet(ctx, SetLog(ex, reps, kgW, Instant.now()))
            val rest = planDay?.let { d -> (d.main + d.core).firstOrNull { ExLib.all(ctx)[it.id]?.name == ex }?.restSec } ?: 90
            if (rest > 0) restUntil = Instant.now().plusSeconds(rest.toLong())
        }, contentAlignment = Alignment.Center) { T("Log set", 15.sp, FontWeight.Bold) }
        restUntil?.let { T("Rest ${Duration.between(now, it).seconds.coerceAtLeast(0)} s", 16.sp, FontWeight.Bold, Coral, Modifier.padding(top = 8.dp)) }
        if (l.sets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp)); DottedLine(Ink.copy(.12f))
            T("Volume ${fmtInt(l.sets.sumOf { it.reps * it.kg })} kg · ${l.sets.size} sets", 12.sp, FontWeight.Bold, Ink, Modifier.padding(top = 6.dp))
            l.sets.takeLast(6).reversed().forEach { T("${it.exercise}: ${it.reps} × ${fmtStep(it.kg)} kg", 12.sp, color = Muted) }
        }
    }
}

// ======================= finish → Done =======================
fun finishWorkout(l: Live, kg: Double): Done {
    val end = l.pausedAt ?: Instant.now()
    val g = WalkTracker.state.value
    val (m, elev, kcal) = WorkoutTracker.totals(l, kg, g.meters, 0.0, end)
    val segs = l.segs.filter { it.start.isBefore(end) }.map { it.copy(end = minOf(it.end ?: end, end)) }
    return Done(System.currentTimeMillis(), l.act, l.start, end, WorkoutTracker.activeSec(l, end), m, kcal, elev, segs, l.sets, l.program, false)
}

// ======================= summary / detail =======================
@Composable
fun WorkoutDetailScreen(d: Done, repo: Repo, age: Int, resting: Long?, delete: () -> Unit, back: () -> Unit) = SubScreen(d.act.label, back) {
    var hr by remember { mutableStateOf<List<Pair<Instant, Long>>?>(null) }
    LaunchedEffect(d.id) { hr = runCatching { repo.heartRateBetween(d.start, d.end) }.getOrDefault(emptyList()) }
    val zone = ZoneId.systemDefault()
    T(d.program ?: d.act.label, 30.sp, FontWeight.Bold)
    T(d.start.atZone(zone).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")) + if (d.savedToHc) " · saved to Health Connect ✓" else "", 12.sp, color = Color.White.copy(.8f))
    Spacer(Modifier.height(12.dp))
    WhiteCard {
        Row { CoachStat("Time", hms(d.activeSec), Modifier.weight(1f)); CoachStat("Active", "${fmtInt(d.kcal)} kcal", Modifier.weight(1f))
            if (d.meters > 0) CoachStat("Distance", dist(d.meters, 2), Modifier.weight(1f)) }
        if (d.meters > 0 || d.elevM > 0) { Spacer(Modifier.height(10.dp)); Row {
            CoachStat("Avg pace", paceStr(d.activeSec, d.meters) + if (Metric) " /km" else " /mi", Modifier.weight(1f))
            CoachStat("Avg speed", "${spd(d.meters / 1000 / (d.activeSec / 3600.0).coerceAtLeast(1e-6))} $spdUnit", Modifier.weight(1f))
            if (d.elevM > 0) CoachStat("Climb", "%.0f %s".format(if (Metric) d.elevM else d.elevM * 3.281, if (Metric) "m" else "ft"), Modifier.weight(1f)) } }
    }
    if (d.act.mode == Mode.TREADMILL && d.segs.isNotEmpty()) WhiteCard {
        T("SPEED & INCLINE", 10.sp, FontWeight.Bold, Brand)
        SegChart(d, Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp))
        Row(Modifier.padding(top = 6.dp)) { T("■ speed  ", 11.sp, FontWeight.Bold, Brand); T("■ incline", 11.sp, FontWeight.Bold, Coral) }
        val avgInc = d.segs.sumOf { it.incline * Duration.between(it.start, it.end ?: d.end).seconds } / d.segs.sumOf { Duration.between(it.start, it.end ?: d.end).seconds }.coerceAtLeast(1)
        T("Avg incline %.1f%% · max %.1f%% · top speed %s %s".format(avgInc, d.segs.maxOf { it.incline }, spd(d.segs.maxOf { it.kmh }), spdUnit), 12.sp, color = Muted)
    }
    WhiteCard {
        T("HEART RATE", 10.sp, FontWeight.Bold, Coral)
        val h = hr
        when {
            h == null -> T("Loading…", 12.sp, color = Muted)
            h.isEmpty() -> T("Appears here after your Fitbit syncs to Health Connect.", 12.sp, color = Muted)
            else -> {
                val rest = (resting ?: 65L).toDouble(); val max = Science.maxHr(age); val res = (max - rest).coerceAtLeast(30.0)
                Row { CoachStat("Avg", "${h.map { it.second }.average().toInt()} bpm", Modifier.weight(1f)); CoachStat("Max", "${h.maxOf { it.second }} bpm", Modifier.weight(1f))
                    val trimp = h.zipWithNext().sumOf { (a, b) -> val mins = Duration.between(a.first, b.first).seconds / 60.0; val x = ((a.second - rest) / res).coerceIn(0.0, 1.0)
                        if (mins in 0.0..5.0) mins * x * Science.trimpWeight(x, false) else 0.0 }
                    CoachStat("Strain", "%.1f".format(21 * (1 - kotlin.math.exp(-trimp / 140.0))), Modifier.weight(1f)) }
                HeartChart(List(24) { null }, Modifier.height(0.dp))
                Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
                    val t0 = h.first().first.toEpochMilli().toFloat(); val t1 = h.last().first.toEpochMilli().toFloat().coerceAtLeast(t0 + 1)
                    val lo = h.minOf { it.second } - 5f; val hi = h.maxOf { it.second } + 5f
                    h.zipWithNext().forEach { (a, b) ->
                        fun p(s: Pair<Instant, Long>) = Offset((s.first.toEpochMilli() - t0) / (t1 - t0) * size.width, size.height - (s.second - lo) / (hi - lo) * size.height)
                        drawLine(Coral, p(a), p(b), 4f)
                    }
                }
                val zones = IntArray(5); h.zipWithNext().forEach { (a, b) -> val s = Duration.between(a.first, b.first).seconds; if (s in 1..300) {
                    val x = (a.second - rest) / res; zones[when { x >= .9 -> 4; x >= .8 -> 3; x >= .7 -> 2; x >= .6 -> 1; else -> 0 }] += s.toInt() } }
                listOf("Easy", "Zone 2 (fat burn)", "Aerobic", "Threshold", "Max").forEachIndexed { i, n ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { T(n, 12.sp, color = Ink, modifier = Modifier.weight(1f)); T(hms(zones[i].toLong()), 12.sp, FontWeight.Bold, Ink) } }
            }
        }
    }
    if (d.sets.isNotEmpty()) WhiteCard {
        T("SETS · VOLUME ${fmtInt(d.sets.sumOf { it.reps * it.kg })} KG", 10.sp, FontWeight.Bold, Brand)
        d.sets.groupBy { it.exercise }.forEach { (x, l) -> T("$x — " + l.joinToString(", ") { "${it.reps}×${fmtStep(it.kg)}" }, 13.sp, color = Ink, modifier = Modifier.padding(top = 4.dp)) }
    }
    T("Delete workout", 13.sp, FontWeight.Bold, Coral, Modifier.padding(top = 8.dp).clickable(onClick = delete))
}

@Composable
fun SegChart(d: Done, modifier: Modifier) = Canvas(modifier) {
    val t0 = d.start.toEpochMilli().toFloat(); val t1 = d.end.toEpochMilli().toFloat().coerceAtLeast(t0 + 1)
    val maxS = d.segs.maxOf { it.kmh }.coerceAtLeast(1.0).toFloat(); val maxI = d.segs.maxOf { it.incline }.coerceAtLeast(1.0).toFloat()
    d.segs.forEach { s ->
        val x0 = (s.start.toEpochMilli() - t0) / (t1 - t0) * size.width; val x1 = ((s.end ?: d.end).toEpochMilli() - t0) / (t1 - t0) * size.width
        val ys = size.height - (s.kmh.toFloat() / maxS) * size.height * .95f; val yi = size.height - (s.incline.toFloat() / maxI) * size.height * .6f
        drawLine(Brand, Offset(x0, ys), Offset(x1, ys), 6f); drawLine(Coral, Offset(x0, yi), Offset(x1, yi), 4f)
    }
}

@Composable
fun WorkoutHistoryScreen(open: (Done) -> Unit, back: () -> Unit) = SubScreen("Workout history", back) {
    val ctx = LocalContext.current
    val list = remember { WorkoutTracker.history(ctx).sortedByDescending { it.start } }
    val wk = list.filter { it.start.isAfter(Instant.now().minus(Duration.ofDays(7))) }
    T("This week", 30.sp, FontWeight.Bold)
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LiveStat("Workouts", "${wk.size}", "", Modifier.weight(1f)); LiveStat("Time", "${wk.sumOf { it.activeSec } / 60}", "min", Modifier.weight(1f))
        LiveStat("Active", fmtInt(wk.sumOf { it.kcal }), "kcal", Modifier.weight(1f))
    }
    if (list.isEmpty()) T("No workouts yet. Start one from the Workout screen.", 13.sp, color = Color.White.copy(.8f))
    list.forEach { d ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { open(d) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) { Icon(d.act.icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                T(d.program ?: d.act.label, 15.sp, FontWeight.Bold, Ink)
                T(d.start.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a")), 11.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) { T(hms(d.activeSec), 14.sp, FontWeight.Bold, Ink); T("${fmtInt(d.kcal)} kcal", 11.sp, color = Muted) }
        }
    }
}

// ======================= 5-day plan =======================
@Composable
fun FitnessPlanScreen(age: Int, resting: Long?, startCardio: (Program) -> Unit, startStrength: (Day) -> Unit, openLabs: () -> Unit, back: () -> Unit) = SubScreen("My plan", back) {
    val ctx = LocalContext.current
    val labs = remember { LabStore.get(ctx) }
    val pr = remember(labs) { priorities(labs, age) }
    val start = remember { LabStore.planStart(ctx) }
    val phase = phaseOf(start)
    val days = remember(pr, phase) { week(pr, phase) }
    var sel by remember { mutableStateOf(LocalDate.now().dayOfWeek) }
    val day = days.first { it.dow == sel }
    val weekNo = (java.time.temporal.ChronoUnit.WEEKS.between(start, LocalDate.now()) % 12 + 1).toInt()
    T("Fat-loss plan", 32.sp, FontWeight.Bold)
    T("Week $weekNo of 12 · Phase $phase of 3 · ${if (pr.visceral) "priority: visceral & liver fat" else "priority: fat loss + muscle"}", 12.sp, color = Color.White.copy(.85f))
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Health profile", openLabs); Chip("Why this plan") { sel = sel }
    }
    // week strip
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d -> val on = d.dow == sel; val today = d.dow == LocalDate.now().dayOfWeek
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) Color.White else Glass).clickable { sel = d.dow }.padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                T(d.dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), 11.sp, FontWeight.Bold, if (on) Brand else Color.White)
                Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(if (d.rest) Color.Transparent else if (today) Accent else if (on) Brand else Color.White.copy(.5f)))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Accent).padding(16.dp)) {
        T(day.dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase() + " · ~${day.minutes} MIN", 11.sp, FontWeight.Bold, Ink)
        T(day.title, 24.sp, FontWeight.Bold, Ink, lineHeight = 28.sp)
        T(day.focus, 13.sp, FontWeight.Medium, Ink.copy(.8f), lineHeight = 17.sp)
        if (day.warmup.isNotEmpty()) T("Warm-up: ${day.warmup}", 12.sp, color = Ink.copy(.75f), modifier = Modifier.padding(top = 6.dp))
        if (day.main.isNotEmpty() || day.core.isNotEmpty()) Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(50)).background(Ink)
            .clickable { startStrength(day) }, contentAlignment = Alignment.Center) { T(if (day.main.isEmpty()) "Start core session" else "Start strength session", 15.sp, FontWeight.Bold) }
    }
    if (day.main.isNotEmpty()) { T("Strength", 20.sp, FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)); day.main.forEach { RxCard(it) } }
    if (day.core.isNotEmpty()) { T("Core", 20.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)); day.core.forEach { RxCard(it) } }
    if (day.cardio.isNotEmpty()) {
        T("Cardio", 20.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        val z = zone2(age, resting)
        day.cardio.forEach { c ->
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsRun, null, tint = Brand); T("  ${c.label}", 16.sp, FontWeight.Bold, Ink, Modifier.weight(1f)); T("${c.minutes} min", 13.sp, FontWeight.Bold, Brand)
                }
                T(c.note, 12.sp, color = Muted, lineHeight = 15.sp)
                T("Zone 2 heart rate: ${z.first}–${z.last} bpm", 12.sp, FontWeight.Bold, Coral, Modifier.padding(top = 4.dp))
                c.program?.let { programByName(it) }?.let { p -> Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).background(Brand)
                    .clickable { startCardio(p) }, contentAlignment = Alignment.Center) { T("Start ${p.name} on treadmill", 14.sp, FontWeight.Bold) } }
            }
        }
    }
    if (day.cooldown.isNotEmpty()) { T("Cool-down & stretch", 20.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)); day.cooldown.forEach { RxCard(it) } }
    // why: findings from the health profile
    T("Why this plan", 20.sp, FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
    WhiteCard {
        T("Weekly cardio target: ${pr.weeklyCardioMin} min · Protein: ${pr.proteinG.first}–${pr.proteinG.last} g/day (1.2–1.6 g/kg of a healthy ${"%.0f".format(pr.refKg)} kg)",
            13.sp, FontWeight.Bold, Ink, lineHeight = 17.sp)
        if (pr.hydrate) T("Water: aim for 3+ litres a day on training days.", 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(6.dp)); DottedLine(Ink.copy(.12f))
        pr.findings.forEach { f ->
            Column(Modifier.padding(top = 8.dp)) {
                Row { T(f.label, 13.sp, FontWeight.Bold, Ink, Modifier.weight(1f)); T(f.value, 13.sp, FontWeight.Bold, if (f.flag) Coral else Color(0xFF0FA958)) }
                T(f.response, 11.sp, color = Muted, lineHeight = 14.sp)
            }
        }
        if (pr.findings.size <= 1) T("Add your lab results in Health profile to personalise this plan.", 12.sp, FontWeight.Bold, Brand, Modifier.padding(top = 8.dp).clickable(onClick = openLabs))
    }
    T("Progression: phase 1 (weeks 1–4) 12–15 reps to learn form; phase 2 (5–8) 10–12 reps, 3 sets; phase 3 (9–12) 8–12 reps, 4 sets, heavier. Add weight when you hit the top of the rep range with 2 reps in reserve. " +
      "Not medical advice — check with your doctor before starting, especially with your ECG pending.", 10.sp, color = Color.White.copy(.65f), lineHeight = 13.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun RxCard(rx: Rx) {
    val ctx = LocalContext.current
    val info = ExLib.all(ctx)[rx.id]
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(rx.id, Modifier.size(110.dp, 96.dp).clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)))
            Column(Modifier.padding(12.dp).weight(1f)) {
                T(info?.name ?: rx.id, 15.sp, FontWeight.Bold, Ink, maxLines = 2)
                T("${rx.sets} × ${rx.reps}" + if (rx.restSec > 0) " · rest ${rx.restSec}s" else "", 13.sp, FontWeight.Bold, Brand)
                T((info?.primary ?: emptyList()).joinToString(", ") { it.replaceFirstChar(Char::uppercase) }, 11.sp, color = Muted, maxLines = 1)
            }
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            T(rx.cue, 12.sp, FontWeight.Bold, Ink, lineHeight = 16.sp)
            info?.steps?.forEachIndexed { i, s -> T("${i + 1}. $s", 11.sp, color = Muted, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (info?.secondary?.isNotEmpty() == true) T("Also works: " + info.secondary.joinToString(", "), 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            rx.home?.takeIf { it != rx.id }?.let { h -> T("No gym? Do ${ExLib.all(ctx)[h]?.name ?: h} instead.", 11.sp, FontWeight.Bold, Brand, Modifier.padding(top = 4.dp)) }
            info?.url?.takeIf { it.isNotEmpty() }?.let { u -> T("Image source ↗", 11.sp, FontWeight.Bold, Brand, Modifier.padding(top = 6.dp).clickable {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) } }) }
        }
    }
}

// ======================= health profile =======================
@Composable
fun LabsScreen(back: () -> Unit) = SubScreen("Health profile", back) {
    val ctx = LocalContext.current
    var l by remember { mutableStateOf(LabStore.get(ctx)) }
    T("Lab results", 32.sp, FontWeight.Bold)
    T("Values from your latest blood report shape the plan. Stored only on this phone.", 12.sp, color = Color.White.copy(.8f))
    Spacer(Modifier.height(12.dp))
    @Composable fun field(label: String, v: Double?, unit: String, set: (Double?) -> Unit) {
        var t by remember(label) { mutableStateOf(v?.let { if (it % 1.0 == 0.0) "%.0f".format(it) else "%.2f".format(it).trimEnd('0') } ?: "") }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f))
            OutlinedTextField(t, { t = it.filter { c -> c.isDigit() || c == '.' }.take(6); set(t.toDoubleOrNull()) }, Modifier.width(110.dp), singleLine = true,
                suffix = { Text(unit, fontSize = 11.sp) })
        }
    }
    WhiteCard {
        field("Height", l.heightCm, "cm") { l = l.copy(heightCm = it) }; field("Weight", l.weightKg, "kg") { l = l.copy(weightKg = it) }
        field("Waist", l.waistCm, "cm") { l = l.copy(waistCm = it) }
        field("LDL", l.ldl, "mg/dL") { l = l.copy(ldl = it) }; field("HDL", l.hdl, "mg/dL") { l = l.copy(hdl = it) }
        field("Total cholesterol", l.totalChol, "mg/dL") { l = l.copy(totalChol = it) }; field("Triglycerides", l.tg, "mg/dL") { l = l.copy(tg = it) }
        field("ALT (SGPT)", l.alt, "U/L") { l = l.copy(alt = it) }; field("ALP", l.alp, "U/L") { l = l.copy(alp = it) }
        field("HbA1c", l.hba1c, "%") { l = l.copy(hba1c = it) }; field("Uric acid", l.uricAcid, "mg/dL") { l = l.copy(uricAcid = it) }
        field("MCV", l.mcv, "fL") { l = l.copy(mcv = it) }; field("Urea", l.urea, "mg/dL") { l = l.copy(urea = it) }; field("TSH", l.tsh, "µIU/mL") { l = l.copy(tsh = it) }
        field("BP systolic", l.sbp?.toDouble(), "mmHg") { l = l.copy(sbp = it?.toInt()) }; field("BP diastolic", l.dbp?.toDouble(), "mmHg") { l = l.copy(dbp = it?.toInt()) }
        Row(Modifier.fillMaxWidth().clickable { l = l.copy(ecgCleared = !l.ecgCleared) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T("ECG done and normal", 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f)); Checkbox(l.ecgCleared, { l = l.copy(ecgCleared = it) })
        }
    }
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable { LabStore.save(ctx, l); back() }, contentAlignment = Alignment.Center) {
        T("Save", 15.sp, FontWeight.Bold, Brand) }
}
