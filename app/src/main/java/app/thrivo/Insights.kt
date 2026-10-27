package app.thrivo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HMA = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun FitnessScreen(v: Vitals, age: Int, female: Boolean, back: () -> Unit) = SubScreen("Cardio Fitness & Load", back) {
    val f = remember(v, age, female) { fitness(v, age, female) }
    val l = remember(v, age, female) { cardioLoad(v, age, female) }
    T("Cardio Fitness", 32.sp, FontWeight.Bold)
    WhiteCard {
        T(if (f.fromDevice) "VO₂MAX · FROM YOUR WATCH" else "VO₂MAX · ESTIMATED (UTH 2004)", 10.sp, FontWeight.Bold, Brand)
        Row(verticalAlignment = Alignment.Bottom) {
            NumUnit(f.vo2?.let { "%.1f".format(it) } ?: "–", " ml/kg/min", 44.sp, Ink)
        }
        T("${f.rating} for your age and sex · median %.1f (FRIEND registry)".format(f.median), 12.sp, FontWeight.Medium, Muted)
        Spacer(Modifier.height(10.dp))
        // scale bar poor→excellent with marker
        val stops = listOf(.8, .95, 1.05, 1.2); val colors = listOf(Coral, Orange, Accent, Color(0xFF7CD4FF), Color(0xFF16C60C))
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))) { colors.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) } }
        f.vo2?.let { x ->
            val r = x / f.median; val pos = when { r < .8 -> r / .8 * .2; r < .95 -> .2 + (r - .8) / .15 * .2; r < 1.05 -> .4 + (r - .95) / .1 * .2
                r < 1.2 -> .6 + (r - 1.05) / .15 * .2; else -> (.8 + (r - 1.2) / .3 * .2).coerceAtMost(1.0) }
            BoxWithConstraints(Modifier.fillMaxWidth()) { Box(Modifier.offset(x = maxWidth * pos.toFloat() - 6.dp).size(12.dp).clip(RoundedCornerShape(50)).background(Ink)) }
        }
        Row(Modifier.fillMaxWidth()) { listOf("Poor", "Fair", "Average", "Good", "Excellent").forEach { T(it, 9.sp, color = Muted, modifier = Modifier.weight(1f)) } }
    }
    WhiteCard {
        T("FITNESS AGE (NES 2014)", 10.sp, FontWeight.Bold, Brand)
        T(f.fitnessAge?.toString() ?: "–", 44.sp, FontWeight.Bold, Ink)
        T(f.fitnessAge?.let { if (it < age) "${age - it} years younger than your age — keep it up." else if (it > age) "${it - age} years older. More vigorous minutes lower it." else "Right on your age." }
            ?: "Needs resting heart rate data.", 12.sp, color = Muted)
    }
    T("Cardio Load", 32.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(l.status.uppercase(), 12.sp, FontWeight.Bold, when (l.status) { "Optimal" -> Color(0xFF0FA958); "High", "Overreaching" -> Coral; else -> Brand }, Modifier.weight(1f))
            T(l.ratio?.let { "ratio %.2f".format(it) } ?: "", 12.sp, FontWeight.Bold, Ink)
        }
        Row { CoachStat("This week (avg/day)", "%.0f".format(l.acute), Modifier.weight(1f)); CoachStat("4-week (avg/day)", "%.0f".format(l.chronic), Modifier.weight(1f)) }
        Spacer(Modifier.height(10.dp))
        val mx = (l.daily.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            l.daily.forEachIndexed { i, x -> Box(Modifier.weight(1f).fillMaxHeight((x / mx).toFloat().coerceAtLeast(.02f)).clip(RoundedCornerShape(50))
                .background(if (i >= 21) Brand else Brand.copy(.3f))) }
        }
        T("Daily training load (TRIMP), last 28 days. Gabbett 2016: keep this week within 0.8–1.3× your 4-week average; above 1.5× raises injury risk.",
            10.sp, color = Muted, lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun StressScreen(v: Vitals, t: Today, age: Int, onBreathe: () -> Unit, back: () -> Unit) = SubScreen("Stress", back) {
    val s = remember(v, t, age) { stressToday(v, t, age) }
    val latest = s.lastOrNull { it != null }
    T("Stress monitor", 32.sp, FontWeight.Bold)
    WhiteCard {
        T("RIGHT NOW (0–3)", 10.sp, FontWeight.Bold, Brand)
        T(latest?.let { "%.1f".format(it) } ?: "–", 52.sp, FontWeight.Bold, Ink)
        T(latest?.let { when { it < 1 -> "Calm"; it < 2 -> "Moderate"; else -> "High — try 5 slow breaths (≈6/min)" } } ?: "Waiting for heart-rate data", 13.sp, FontWeight.Medium, Muted)
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val bw = size.width / 48
            for (i in 1..2) drawLine(Color(0xFFE6E8F0), Offset(0f, size.height * (1 - i / 3f)), Offset(size.width, size.height * (1 - i / 3f)), 2f)
            s.forEachIndexed { i, x -> if (x != null) {
                val h = (x / 3 * size.height).toFloat().coerceAtLeast(4f)
                drawRoundRect(when { x < 1 -> Color(0xFF7CD4FF); x < 2 -> Accent; else -> Coral }, Offset(i * bw + 1, size.height - h), Size(bw - 2, h),
                    androidx.compose.ui.geometry.CornerRadius(4f))
            } }
        }
        Row(Modifier.fillMaxWidth()) { listOf("12AM", "6AM", "12PM", "6PM").forEach { T(it, 9.sp, color = Muted, modifier = Modifier.weight(1f)) } }
        T("Heart rate above resting while you're not moving, in 30-min blocks. Movement is skipped so workouts don't count as stress (Thayer 2012). An estimate, not a diagnosis.",
            10.sp, color = Muted, lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onBreathe), contentAlignment = Alignment.Center) {
        T("Start a breathing session", 15.sp, FontWeight.Bold, Brand) }
}

@Composable
fun SleepProfileScreen(v: Vitals, back: () -> Unit) = SubScreen("Sleep Profile", back) {
    val p = remember(v) { sleepProfile(v.nights) }
    T("Your sleep profile", 32.sp, FontWeight.Bold)
    T("Based on ${p.nights} nights", 12.sp, color = Color.White.copy(.8f))
    Spacer(Modifier.height(12.dp))
    WhiteCard {
        T("CHRONOTYPE (ROENNEBERG 2003)", 10.sp, FontWeight.Bold, Brand)
        T(p.chronotype, 28.sp, FontWeight.Bold, Ink)
        T("Mid-sleep around ${p.midSleep?.format(HMA) ?: "–"}", 12.sp, color = Muted)
    }
    WhiteCard {
        T("SLEEP CONSISTENCY (SRI, PHILLIPS 2017)", 10.sp, FontWeight.Bold, Brand)
        T(p.sri?.toString() ?: "–", 44.sp, FontWeight.Bold, Ink)
        T(p.sri?.let { when { it >= 87 -> "Very regular"; it >= 80 -> "Regular"; it >= 70 -> "Somewhat irregular"; else -> "Irregular — a steadier schedule helps" } }
            ?: "Needs 3+ nights in the past week", 12.sp, FontWeight.Medium, Muted)
        T("Chance you're in the same state (asleep/awake) at the same time on consecutive days. Regular sleepers had 20–48% lower mortality risk (Windred 2024).",
            10.sp, color = Muted, lineHeight = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
    WhiteCard {
        Row { CoachStat("Avg bedtime", p.avgBed?.format(HMA) ?: "–", Modifier.weight(1f)); CoachStat("Avg wake", p.avgWake?.format(HMA) ?: "–", Modifier.weight(1f)) }
        Spacer(Modifier.height(10.dp))
        Row { CoachStat("Bedtime variation", p.bedSdMin?.let { "± $it min" } ?: "–", Modifier.weight(1f)); CoachStat("Avg sleep", p.avgSleep?.let(::fmt) ?: "–", Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleScreen(v: Vitals, log: (LocalDate, Int) -> Unit, back: () -> Unit) = SubScreen("Cycle", back) {
    val c = remember(v) { cycle(v.periods) }
    var picking by remember { mutableStateOf(false) }
    T("Cycle tracking", 32.sp, FontWeight.Bold)
    WhiteCard {
        T("CYCLE DAY", 10.sp, FontWeight.Bold, Brand)
        T(c.day?.toString() ?: "–", 52.sp, FontWeight.Bold, Ink)
        T(c.phase, 16.sp, FontWeight.Bold, Coral)
        Spacer(Modifier.height(10.dp))
        Row {
            CoachStat("Avg cycle", "%.0f days".format(c.length), Modifier.weight(1f))
            CoachStat("Next period", c.nextPeriod?.format(DateTimeFormatter.ofPattern("MMM d")) ?: "–", Modifier.weight(1f))
        }
        c.fertile?.let { T("Estimated fertile window ${it.start.format(DateTimeFormatter.ofPattern("MMM d"))}–${it.endInclusive.format(DateTimeFormatter.ofPattern("MMM d"))}",
            12.sp, FontWeight.Medium, Muted, modifier = Modifier.padding(top = 8.dp)) }
        T("Predictions use your logged cycles (average of the last 6) and ovulation ~14 days before the next period (Bull 2019). Not contraception.",
            10.sp, color = Muted, lineHeight = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable { picking = true }, contentAlignment = Alignment.Center) {
        T("Log period start", 15.sp, FontWeight.Bold, Brand) }
    if (picking) {
        val state = rememberDatePickerState(System.currentTimeMillis())
        DatePickerDialog({ picking = false }, confirmButton = { TextButton({
            state.selectedDateMillis?.let { log(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate(), 5) }; picking = false }) { Text("Save") } }) {
            DatePicker(state)
        }
    }
    Spacer(Modifier.height(12.dp))
    T("Logged periods", 18.sp, FontWeight.Bold)
    v.periods.reversed().take(6).forEach { (s, e) -> T("${s.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))} – ${e.format(DateTimeFormatter.ofPattern("MMM d"))}", 13.sp,
        modifier = Modifier.padding(top = 6.dp)) }
}
