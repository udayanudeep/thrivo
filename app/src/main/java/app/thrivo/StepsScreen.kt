package app.thrivo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

@Composable
fun StepsScreen(d: Today, goal: Int, metric: Boolean, weekMon: Boolean, onStats: () -> Unit, onAdd: (Long) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(50)).background(Glass).padding(3.dp)) {
                listOf("D", "W", "M").forEachIndexed { i, t ->
                    T(t, 14.sp, FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (mode == i) Color.White.copy(.3f) else Color.Transparent)
                        .clickable { mode = i }.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            CircleBtn(Icons.Default.BarChart, label = "Reports", onClick = onStats)
        }
        val bars: List<Pair<String, Long?>> = when (mode) {
            0 -> d.hourly.mapIndexed { h, v ->
                (when (h) { 0 -> "12AM"; 6 -> "6"; 12 -> "12PM"; 18 -> "6"; else -> "" }) to (if (h <= LocalTime.now().hour) v else null) }
            1 -> {
                val off = if (weekMon) today.dayOfWeek.value - 1 else today.dayOfWeek.value % 7
                (0..6).map { i -> val dt = today.minusDays((off - i).toLong())
                    dt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) to (if (dt.isAfter(today)) null else d.history[59 - (off - i)]) }
            }
            else -> {
                val len = today.lengthOfMonth()
                (1..len).map { day -> val back = today.dayOfMonth - day
                    (if ((day - 1) % 5 == 0) "%02d".format(day) else "") to (if (back < 0 || back > 59) null else d.history[59 - back]) }
            }
        }
        val peak = maxOf(bars.maxOf { it.second ?: 0 }, if (mode == 0) 0 else goal.toLong())
        val top = niceTop(peak)
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp, bottom = 8.dp)
            .semantics { contentDescription = "Step chart: " + bars.filter { it.second != null }.joinToString { "${it.first} ${it.second}" } }) {
            Column(Modifier.fillMaxHeight().padding(bottom = 22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                (10 downTo 1).forEach { T(axis(top * it / 10), 9.sp, color = Color.White.copy(.55f)) }
                Spacer(Modifier.height(0.dp))
            }
            Row(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (bars.size <= 7) 10.dp else 3.dp)) {
                bars.forEach { (label, v) ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            if (v != null && v > 0) Box(Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / top).coerceIn(.02f, 1f))
                                .clip(RoundedCornerShape(50)).background(if (mode != 0 && v >= goal && StepsBar == Color.White) Accent else StepsBar))
                        }
                        Box(Modifier.height(22.dp), contentAlignment = Alignment.BottomCenter) {
                            T(label, 9.sp, color = Color.White.copy(.6f), maxLines = 1, modifier = Modifier.wrapContentWidth(unbounded = true))
                        }
                    }
                }
            }
        }
        val pct = (d.steps * 100 / goal.coerceAtLeast(1)).toInt()
        Motto(if (d.steps == 0L) "Let's Move!" else if (pct >= 100) "Goal crushed!" else "Keep Going!",
            if (d.steps == 0L) "Your first step awaits." else if (pct >= 100) "You hit ${fmtInt(goal)} steps today." else "$pct% of today's goal.")
        Spacer(Modifier.height(10.dp))
        val (steps, meters, kcal, floors, sub) = when (mode) {
            0 -> Stats(d.steps, d.meters, d.stepKcal, d.floors, "Today")
            1 -> { val wk = d.history.takeLast(today.dayOfWeek.value % 7 + 1).sum()
                Stats(wk, d.meters * wk / d.steps.coerceAtLeast(1), d.stepKcal * wk / d.steps.coerceAtLeast(1), null, "W${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}") }
            else -> { val m = d.history.takeLast(today.dayOfMonth).sum()
                Stats(m, d.meters * m / d.steps.coerceAtLeast(1), d.stepKcal * m / d.steps.coerceAtLeast(1), null,
                    today.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " '" + (today.year % 100)) }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = 96.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                T(fmtInt(steps), 46.sp, FontWeight.Bold, Ink, Modifier.weight(1f))
                if (mode == 0) Row(Modifier.clip(RoundedCornerShape(50)).clickable { adding = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Ink, modifier = Modifier.size(18.dp)); T("Add", 15.sp, FontWeight.Bold, Ink)
                }
            }
            T("Steps • $sub", 13.sp, FontWeight.Medium, Ink)
            if (mode == 0 && d.rideSteps > 0) T("${fmtInt(d.rideSteps)} steps during rides not counted", 11.sp, FontWeight.Medium, Muted)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    val dv = dist(meters).split(" "); NumUnit(dv[0].removeSuffix(".0"), dv[1], 22.sp, Ink)
                    T("Distance", 10.sp, color = Muted)
                }
                Column(Modifier.weight(1f)) { NumUnit(fmtInt(kcal), "kcal", 22.sp, Ink); T("Walking calories", 10.sp, color = Muted) }
                Spacer(Modifier.weight(.6f))
                Column(horizontalAlignment = Alignment.End) { T(floors?.let { fmtInt(it) } ?: "–", 22.sp, FontWeight.Bold, Ink); T("Floors", 10.sp, color = Muted) }
            }
        }
    }
    if (adding) AddStepsDialog({ adding = false }) { onAdd(it); adding = false }
}

@Composable
fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier) =
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.1f)).padding(12.dp)) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(16.dp))
        T(value, 16.sp, FontWeight.Bold, maxLines = 1); T(label, 10.sp, color = Color.White.copy(.65f))
    }

private data class Stats(val steps: Long, val meters: Double, val kcal: Double, val floors: Double?, val sub: String)

fun niceTop(peak: Long): Long {
    val steps = listOf(1000L, 2000, 5000, 10000, 15000, 20000, 30000, 50000)
    return steps.firstOrNull { it >= peak } ?: ((peak / 10000 + 1) * 10000)
}

fun axis(v: Long) = if (v >= 1000) (if (v % 1000 == 0L) "${v / 1000}K" else "%.1fK".format(v / 1000.0)) else v.toString()

@Composable
fun AddStepsDialog(dismiss: () -> Unit, add: (Long) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(dismiss, containerColor = Color.White,
        title = { T("Add steps", 22.sp, FontWeight.Bold, Ink) },
        text = { OutlinedTextField(text, { text = it.filter(Char::isDigit).take(6) }, singleLine = true, placeholder = { Text("e.g. 1500") }) },
        confirmButton = { TextButton({ text.toLongOrNull()?.takeIf { it > 0 }?.let(add) }) { T("Add", 16.sp, FontWeight.Bold, Brand) } },
        dismissButton = { TextButton(dismiss) { T("Cancel", 16.sp, color = Ink) } })
}
