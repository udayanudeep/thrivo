package app.thrivo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.format.TextStyle
import java.util.Locale

/** A metric with whatever history we have: [days] is oldest→newest ending today; [hourly] is today by hour (null = not available). */
data class MetricSpec(val key: String, val title: String, val unit: String, val color: Color, val days: List<Double?>, val hourly: List<Double?>? = null,
                  val sum: Boolean = false, val goal: Double? = null, val decimals: Int = 0, val note: String? = null, val explain: List<Pair<String, String>> = emptyList())

fun metricFor(key: String, d: Today, v: Vitals, c: Coach, goals: Pair<Int, Int>, kgWeight: (Double) -> Double = { it }): MetricSpec? {
    val nights = v.nights.associateBy { it.date }
    fun nightSeries(f: (Night) -> Double) = (0 until 30).map { i -> nights[LocalDate.now().minusDays(29L - i)]?.let(f) }
    return when (key) {
        "steps" -> MetricSpec(key, "Steps", "steps", Color.White, d.yearSteps.map { it.toDouble() }, d.hourly.map { it.toDouble() }, true, goals.first.toDouble())
        "distance" -> MetricSpec(key, "Distance", if (Metric) "km" else "mi", Cyan, d.yearMeters.map { (if (Metric) it / 1000 else it / 1609.344) }, null, true, null, 1)
        "calories" -> MetricSpec(key, "Calories burned", "kcal", Coral, d.burned7, null, true,
            note = "Total calories (resting + active) from your tracker. Only the last 7 days are kept by Health Connect here.")
        "water" -> MetricSpec(key, "Water", volUnit, Cyan, d.water30.map { if (Metric) it.toDouble() else it / 29.5735 }, null, true, goals.second.toDouble().let { if (Metric) it else it / 29.5735 })
        "heart" -> MetricSpec(key, "Heart rate", "bpm", Coral, v.rhrDaily.map { it?.toDouble() }, v.hrHourly.map { it?.toDouble() }, false,
            note = "Today by hour is your average heart rate; daily is your resting heart rate.")
        "rhr" -> MetricSpec(key, "Resting heart rate", "bpm", Coral, v.rhrDaily.map { it?.toDouble() }, null, false, explain = listOf("Normal adult range" to "60–100 bpm (AHA)", "Lower is generally fitter" to "athletes 40–60"))
        "hrv" -> MetricSpec(key, "Heart rate variability", "ms", Rem, v.hrvDaily, null, false, explain = c.recoveryParts + listOf("Typical adult rMSSD" to "19–75 ms (Nunan 2010)"),
            note = "Higher than your own baseline means better recovered.")
        "spo2" -> MetricSpec(key, "Blood oxygen (SpO₂)", "%", Color(0xFF7CD4FF), v.spo2Daily, null, false, explain = listOf("Normal" to "95–100% (WHO)"), decimals = 1)
        "resp" -> MetricSpec(key, "Breathing rate", "br/min", Cyan, v.respDaily, null, false, explain = listOf("Normal adult range" to "12–20 per minute"), decimals = 1)
        "weight" -> MetricSpec(key, "Weight", if (Metric) "kg" else "lb", Orange, v.weightDaily.map { it?.let(kgWeight) }, null, false, decimals = 1)
        "sleep" -> MetricSpec(key, "Sleep", "h", Color(0xFF9D8CFF), nightSeries { it.asleep.toMinutes() / 60.0 }, null, false, 8.0, 1, explain = c.sleepParts)
        else -> null
    }
}

private enum class Period(val label: String) { HOUR("Hourly"), DAY("Daily"), WEEK("Weekly"), MONTH("Monthly") }
private data class Bar(val label: String, val value: Double?)

@Composable
fun MetricDetailScreen(m: MetricSpec, back: () -> Unit) = SubScreen(m.title, back) {
    val periods = remember(m) { Period.entries.filter { it != Period.HOUR || m.hourly != null } }
    var p by remember(m) { mutableStateOf(if (m.hourly != null) Period.HOUR else Period.DAY) }
    val today = LocalDate.now()
    fun avgOrSum(l: List<Double?>): Double? { val x = l.filterNotNull(); return if (x.isEmpty()) null else if (m.sum) x.sum() / x.size else x.average() }   // per-day average either way
    val bars: List<Bar> = when (p) {
        Period.HOUR -> m.hourly!!.mapIndexed { h, v -> Bar(when (h) { 0 -> "12AM"; 6 -> "6"; 12 -> "12PM"; 18 -> "6"; else -> "" }, if (h <= java.time.LocalTime.now().hour) v else null) }
        Period.DAY -> m.days.takeLast(14).let { l -> l.mapIndexed { i, v -> val d = today.minusDays((l.size - 1 - i).toLong())
            Bar(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) + "\n${d.dayOfMonth}", v) } }
        Period.WEEK -> {
            val n = m.days.size; val dated = m.days.mapIndexed { i, v -> today.minusDays((n - 1 - i).toLong()) to v }
            dated.groupBy { it.first.get(IsoFields.WEEK_BASED_YEAR) * 100 + it.first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.entries.sortedBy { it.key }.takeLast(12)
                .map { (_, l) -> Bar("W${l.first().first.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}", avgOrSum(l.map { it.second })) }
        }
        Period.MONTH -> {
            val n = m.days.size; val dated = m.days.mapIndexed { i, v -> today.minusDays((n - 1 - i).toLong()) to v }
            dated.groupBy { it.first.year * 100 + it.first.monthValue }.entries.sortedBy { it.key }.takeLast(12)
                .map { (_, l) -> Bar(l.first().first.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(3), avgOrSum(l.map { it.second })) }
        }
    }
    val vals = bars.mapNotNull { it.value }
    fun fmtV(x: Double) = if (m.decimals == 0) fmtInt(x) else "%.${m.decimals}f".format(x)
    Row(Modifier.clip(RoundedCornerShape(50)).background(Glass).padding(3.dp)) {
        periods.forEach { q -> T(q.label, 13.sp, FontWeight.Bold, if (p == q) Brand else Color.White, Modifier.clip(RoundedCornerShape(50))
            .background(if (p == q) Color.White else Color.Transparent).clickable { p = q }.padding(horizontal = 14.dp, vertical = 7.dp)) }
    }
    Spacer(Modifier.height(14.dp))
    val latest = vals.lastOrNull()
    Row(verticalAlignment = Alignment.Bottom) {
        T(latest?.let(::fmtV) ?: "–", 52.sp, FontWeight.Bold, lineHeight = 52.sp)
        T(" ${m.unit}${if ((p == Period.WEEK || p == Period.MONTH) && m.sum) " /day avg" else ""}", 15.sp, FontWeight.Medium, Color.White.copy(.8f), Modifier.padding(bottom = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
    if (vals.isEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.12f)).padding(20.dp)) {
            T("No ${m.title.lowercase()} data for this period yet.", 14.sp, FontWeight.Bold); T("It appears here once your Fitbit syncs it to Health Connect.", 12.sp, color = Color.White.copy(.75f)) }
    } else {
        val top = maxOf(vals.max(), m.goal ?: 0.0).let { if (it <= 0) 1.0 else it * 1.1 }
        val low = if (m.sum || p == Period.HOUR && m.key == "steps") 0.0 else (vals.min() * .85).coerceAtLeast(0.0)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.12f)).padding(14.dp)) {
            Row(Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 16) 2.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
                bars.forEach { b -> Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (b.value != null && bars.size <= 14) T(if (b.value >= 1000) "%.1fk".format(b.value / 1000) else fmtV(b.value), 8.sp, color = Color.White.copy(.8f), maxLines = 1,
                        modifier = Modifier.wrapContentWidth(unbounded = true))
                    Box(Modifier.fillMaxWidth().weight(1f, fill = false).height(if (b.value == null) 0.dp else (150 * ((b.value - low) / (top - low)).coerceIn(.02, 1.0)).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (m.goal != null && b.value != null && b.value >= m.goal) Accent else m.color))
                    T(b.label, 8.sp, color = Color.White.copy(.65f), maxLines = 2, lineHeight = 10.sp, modifier = Modifier.wrapContentWidth(unbounded = true))
                } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChipText("Average", fmtV(vals.average()), Modifier.weight(1f)); StatChipText("Lowest", fmtV(vals.min()), Modifier.weight(1f)); StatChipText("Highest", fmtV(vals.max()), Modifier.weight(1f))
        }
        m.goal?.let { g -> T("Goal ${fmtV(g)} ${m.unit} · ${bars.count { (it.value ?: 0.0) >= g }} of ${vals.size} reached", 12.sp, FontWeight.Medium, Color.White.copy(.85f), Modifier.padding(top = 8.dp)) }
    }
    if (m.explain.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        WhiteCard { m.explain.forEach { (k, v) -> Breakdown(k, v, Ink) } }
    }
    m.note?.let { T(it, 11.sp, color = Color.White.copy(.7f), lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
    if (m.days.size < 365 && p != Period.HOUR) T("Showing the ${m.days.size} days of history available for this metric.", 10.sp, color = Color.White.copy(.55f), modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun StatChipText(label: String, value: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.14f)).padding(12.dp)) { T(label, 10.sp, color = Color.White.copy(.7f)); T(value, 18.sp, FontWeight.Bold, maxLines = 1) }
