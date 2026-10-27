package app.thrivo

import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.exp
import kotlin.math.ln

// ======================= Guided breathing (6 breaths/min, Lehrer 2014) =======================
@Composable
fun BreathingScreen(back: () -> Unit) = SubScreen("Breathe", back) {
    var minutes by remember { mutableIntStateOf(2) }
    var running by remember { mutableStateOf(false) }
    var left by remember { mutableIntStateOf(0) }
    var inhale by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (running && inhale) 1f else .45f, tween(5000, easing = FastOutSlowInEasing), label = "breath")
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        left = minutes * 60
        while (left > 0 && running) {
            inhale = (left / 5) % 2 == (minutes * 60 / 5) % 2   // flips every 5 s: 5 in, 5 out = 6/min
            if (left % 5 == 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1000); left--
        }
        running = false
    }
    T("Resonance breathing", 30.sp, FontWeight.Bold)
    T("Breathe in for 5 seconds, out for 5 — six breaths a minute. This pace raises HRV and calms your nervous system (Lehrer & Gevirtz 2014).",
        12.sp, color = Color.White.copy(.8f), lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(280.dp).scale(scale).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFFFF200), Color(0xFF3D6BFF), Color(0xFF2B1EF5)))))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(if (!running) "Ready" else if (inhale) "Breathe in" else "Breathe out", 26.sp, FontWeight.Bold)
            if (running) T("%d:%02d".format(left / 60, left % 60), 16.sp, color = Color.White.copy(.8f))
        }
    }
    if (!running) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        listOf(1, 2, 5).forEach { m -> T("$m min", 14.sp, FontWeight.Bold, if (minutes == m) Brand else Color.White, Modifier.padding(4.dp).clip(RoundedCornerShape(50))
            .background(if (minutes == m) Color.White else Glass).clickable { minutes = m }.padding(horizontal = 18.dp, vertical = 8.dp)) }
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable { running = !running }, contentAlignment = Alignment.Center) {
        T(if (running) "Stop" else "Start", 16.sp, FontWeight.Bold, Brand) }
}

// ======================= Quick Settings tile: log a glass =======================
class WaterTileService : TileService() {
    override fun onStartListening() { qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Log water"; subtitle = "+${Repo(this@WaterTileService).glassMl} ml"; updateTile() } }
    override fun onClick() {
        val repo = Repo(this)
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching { repo.addWater(repo.glassMl) }.isSuccess
            qsTile?.apply { state = if (ok) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; subtitle = if (ok) "Logged ${repo.glassMl} ml" else "Open app to connect"; updateTile() }
            runCatching { repo.loadQuick() }.getOrNull()?.let { saveSnapshot(this@WaterTileService, Snapshot(it.steps, repo.stepGoal, it.waterMl, repo.waterGoal, it.meters / 1000)) }
        }
    }
}

// ======================= Caffeine =======================
/** mg per typical serving (USDA FoodData Central typical values). */
val DRINKS = listOf("Espresso" to 63, "Coffee (240 ml)" to 95, "Instant coffee" to 62, "Black tea" to 47, "Green tea" to 28,
    "Cola (330 ml)" to 34, "Energy drink (250 ml)" to 80, "Filter coffee (large)" to 190)
private const val HALF_LIFE_H = 5.0   // EFSA 2015: ~4–6 h in adults

data class Dose(val at: Instant, val mg: Int, val label: String)

fun doses(ctx: Context): List<Dose> = runCatching {
    val a = JSONArray(ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).getString("caffeine", "[]"))
    (0 until a.length()).map { a.getJSONObject(it).let { o -> Dose(Instant.ofEpochMilli(o.getLong("t")), o.getInt("mg"), o.getString("l")) } }
}.getOrDefault(emptyList()).filter { Duration.between(it.at, Instant.now()).toHours() < 48 }

fun addDose(ctx: Context, mg: Int, label: String) {
    val l = doses(ctx) + Dose(Instant.now(), mg, label)
    ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).edit().putString("caffeine",
        JSONArray(l.map { JSONObject().put("t", it.at.toEpochMilli()).put("mg", it.mg).put("l", it.label) }).toString()).apply()
}

fun caffeineAt(ds: List<Dose>, t: Instant) = ds.filter { !it.at.isAfter(t) }.sumOf { d ->
    d.mg * exp(-ln(2.0) * Duration.between(d.at, t).toMinutes() / 60.0 / HALF_LIFE_H) }

@Composable
fun CaffeineScreen(bedtime: LocalTime, back: () -> Unit) = SubScreen("Caffeine", back) {
    val ctx = LocalContext.current
    var ds by remember { mutableStateOf(doses(ctx)) }
    val now = Instant.now(); val zone = java.time.ZoneId.systemDefault()
    val bed = java.time.LocalDate.now().atTime(bedtime).atZone(zone).toInstant().let { if (it.isBefore(now)) it.plus(Duration.ofDays(1)) else it }
    val today = ds.filter { it.at.atZone(zone).toLocalDate() == java.time.LocalDate.now() }.sumOf { it.mg }
    val cutoff = bedtime.minusMinutes((8.8 * 60).toLong())   // Gardiner 2023
    T("Caffeine", 32.sp, FontWeight.Bold)
    WhiteCard {
        T("IN YOUR BODY NOW", 10.sp, FontWeight.Bold, Brand)
        NumUnit("%.0f".format(caffeineAt(ds, now)), " mg", 44.sp, Ink)
        T("≈ %.0f mg left at bedtime (%s)".format(caffeineAt(ds, bed), bedtime.format(DateTimeFormatter.ofPattern("h:mm a"))), 13.sp, FontWeight.Medium, Muted)
        Spacer(Modifier.height(10.dp))
        Row { CoachStat("Today", "$today / 400 mg", Modifier.weight(1f)); CoachStat("Cutoff", cutoff.format(DateTimeFormatter.ofPattern("h:mm a")), Modifier.weight(1f)) }
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE6E8F0))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((today / 400f).coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(if (today > 400) Coral else Brand)) }
        if (LocalTime.now().isAfter(cutoff)) T("Past your cutoff — caffeine now is likely to cut tonight's sleep.", 12.sp, FontWeight.Bold, Coral, modifier = Modifier.padding(top = 8.dp))
        T("Safe limit 400 mg/day for adults, 200 mg if pregnant (EFSA 2015). Stop ~8.8 h before bed (Gardiner 2023). Half-life ~5 h.",
            10.sp, color = Muted, lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
    T("Log a drink", 20.sp, FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
    DRINKS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (l, mg) -> Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Glass).clickable { addDose(ctx, mg, l); ds = doses(ctx) }.padding(12.dp)) {
                T(l, 13.sp, FontWeight.Bold); T("$mg mg", 11.sp, color = Color.White.copy(.7f)) } }
        }
    }
    ds.filter { it.at.atZone(zone).toLocalDate() == java.time.LocalDate.now() }.reversed().forEach {
        T("${it.at.atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))} · ${it.label} · ${it.mg} mg", 12.sp, modifier = Modifier.padding(top = 4.dp)) }
}

// ======================= Body =======================
@Composable
fun BodyScreen(repo: Repo, back: () -> Unit) = SubScreen("Body", back) {
    val scope = rememberCoroutineScope()
    var b by remember { mutableStateOf(Body()) }
    var logging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { b = runCatching { repo.loadBody() }.getOrDefault(Body()) }
    val w = b.weights.lastOrNull()?.second
    val bmi = if (w != null && b.heightM != null) w / (b.heightM!! * b.heightM!!) else null
    T("Body", 32.sp, FontWeight.Bold)
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("WEIGHT", 10.sp, FontWeight.Bold, Brand, Modifier.weight(1f))
            T("+ Log", 13.sp, FontWeight.Bold, Brand, Modifier.clickable { logging = true })
        }
        NumUnit(w?.let { weight(it).substringBefore(" ") } ?: "–", if (Metric) " kg" else " lb", 40.sp, Ink)
        if (b.weights.size >= 2) {
            val delta = b.weights.last().second - b.weights.first().second
            T("%+.1f kg over %d days".format(delta, java.time.temporal.ChronoUnit.DAYS.between(b.weights.first().first, b.weights.last().first)), 12.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            val lo = b.weights.minOf { it.second } - .5; val hi = b.weights.maxOf { it.second } + .5
            Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                val pts = b.weights.mapIndexed { i, (_, kg) -> androidx.compose.ui.geometry.Offset(i * size.width / (b.weights.size - 1).coerceAtLeast(1), (size.height * (1 - (kg - lo) / (hi - lo))).toFloat()) }
                pts.zipWithNext().forEach { (a, c) -> drawLine(Brand, a, c, 5f) }
            }
        }
        bmi?.let {
            Spacer(Modifier.height(8.dp))
            T("BMI %.1f · %s (WHO)".format(it, when { it < 18.5 -> "Underweight"; it < 25 -> "Healthy"; it < 30 -> "Overweight"; else -> "Obesity" }), 14.sp, FontWeight.Bold, Ink)
        } ?: T("Add your height in Health Connect or Fitbit to see BMI.", 11.sp, color = Muted)
    }
    val lastBp = b.bp.lastOrNull()
    WhiteCard {
        T("BLOOD PRESSURE", 10.sp, FontWeight.Bold, Brand)
        T(lastBp?.let { "%.0f/%.0f".format(it.second, it.third) } ?: "–", 36.sp, FontWeight.Bold, Ink)
        lastBp?.let { (_, s, d) -> T(when { s >= 140 || d >= 90 -> "Stage 2 range"; s >= 130 || d >= 80 -> "Stage 1 range"; s >= 120 -> "Elevated"; else -> "Normal" } + " · ACC/AHA 2017",
            13.sp, FontWeight.Bold, if (s >= 130 || d >= 80) Coral else Color(0xFF0FA958)) } ?: T("From a connected cuff via Health Connect", 11.sp, color = Muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { WhiteCard {
            T("GLUCOSE", 10.sp, FontWeight.Bold, Brand); T(b.glucose?.let { "%.0f".format(it) } ?: "–", 28.sp, FontWeight.Bold, Ink)
            T(b.glucose?.let { when { it < 70 -> "Low"; it < 100 -> "Normal (fasting)"; it < 126 -> "Prediabetes range"; else -> "High" } } ?: "mg/dL", 11.sp, color = Muted)
        } }
        Box(Modifier.weight(1f)) { WhiteCard {
            T("BODY TEMP", 10.sp, FontWeight.Bold, Brand); T(b.tempC?.let { "%.1f°".format(it) } ?: "–", 28.sp, FontWeight.Bold, Ink)
            T(b.tempC?.let { if (it >= 38) "Fever range" else "°C" } ?: "°C", 11.sp, color = Muted)
        } }
    }
    T("Ranges are for awareness only. Talk to a doctor about any reading that worries you.", 10.sp, color = Color.White.copy(.6f))
    if (logging) {
        var t by remember { mutableStateOf("") }
        AlertDialog({ logging = false }, containerColor = Color.White, title = { T("Log weight", 20.sp, FontWeight.Bold, Ink) },
            text = { OutlinedTextField(t, { t = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true, label = { Text("kg") }) },
            confirmButton = { TextButton({ t.toDoubleOrNull()?.takeIf { it in 20.0..400.0 }?.let { kg -> scope.launch { runCatching { repo.logWeight(kg) }; b = runCatching { repo.loadBody() }.getOrDefault(b) } }; logging = false }) {
                T("Save", 16.sp, FontWeight.Bold, Brand) } })
    }
}

// ======================= Medications =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedsScreen(back: () -> Unit) = SubScreen("Medications", back) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(meds(ctx)) }
    var adding by remember { mutableStateOf(false) }
    T("Reminders", 32.sp, FontWeight.Bold)
    T("A daily notification at the time you set, with a Taken button.", 12.sp, color = Color.White.copy(.8f))
    Spacer(Modifier.height(12.dp))
    list.forEach { m ->
        WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Medication, null, tint = Brand)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    T(m.name, 16.sp, FontWeight.Bold, Ink); T(m.time.format(DateTimeFormatter.ofPattern("h:mm a")) + if (takenToday(ctx, m.id)) " · taken today ✓" else "", 12.sp, color = Muted)
                }
                if (!takenToday(ctx, m.id)) TextButton({ markTaken(ctx, m.id); list = meds(ctx) }) { T("Taken", 13.sp, FontWeight.Bold, Brand) }
                IconButton({ saveMeds(ctx, list.filter { it.id != m.id }); list = meds(ctx) }) { Icon(Icons.Default.Delete, null, tint = Muted) }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable { adding = true }, contentAlignment = Alignment.Center) {
        T("+ Add reminder", 15.sp, FontWeight.Bold, Brand) }
    if (adding) {
        var name by remember { mutableStateOf("") }
        val tp = rememberTimePickerState(9, 0)
        AlertDialog({ adding = false }, containerColor = Color.White, title = { T("New reminder", 20.sp, FontWeight.Bold, Ink) },
            text = { Column { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text("Medication or supplement") }); Spacer(Modifier.height(10.dp)); TimeInput(tp) } },
            confirmButton = { TextButton({ if (name.isNotBlank()) { saveMeds(ctx, list + Med(System.currentTimeMillis(), name.trim(), LocalTime.of(tp.hour, tp.minute))); list = meds(ctx) }; adding = false }) {
                T("Add", 16.sp, FontWeight.Bold, Brand) } })
    }
}
