package app.thrivo

import androidx.compose.foundation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.records.SleepSessionRecord
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val Deep = Color(0xFF5B3CFF); val Rem = Color(0xFF3FE6FF); val Light = Color(0xFF9DB8FF); private val Awake = Coral
private val HM = DateTimeFormatter.ofPattern("h:mm a")
fun fmt(d: Duration) = "${d.toHours()}h ${d.toMinutes() % 60}m"

@Composable
fun HealthScreen(v: Vitals, age: Int, female: Boolean, onScience: () -> Unit, open: (String) -> Unit, metric: (String) -> Unit, connect: () -> Unit) {
    val c = remember(v, age, female) { coach(v, age, female) }
    val zone = ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f)); CircleBtn(Icons.Default.Sync, onClick = connect)
        }
        T("Health", 46.sp, FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("Sleep, heart and vitals from your Fitbit", 12.sp, FontWeight.Medium, Color.White.copy(.8f), Modifier.weight(1f))
            Chip("The science", onScience)
        }
        Spacer(Modifier.height(18.dp))

        // ---- Whoop-style dials ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dial("Recovery", c.recovery?.let { "$it%" } ?: "–", (c.recovery ?: 0) / 100f, recoveryColor(c.recovery), Modifier.weight(1f).clickable { metric("hrv") })
            Dial("Strain", "%.1f".format(c.strain), (c.strain / 21).toFloat(), Color(0xFF2F9BFF), Modifier.weight(1f).clickable { metric("heart") })
            Dial("Sleep", c.sleepScore?.toString() ?: "–", (c.sleepScore ?: 0) / 100f, Color(0xFF9D8CFF), Modifier.weight(1f).clickable { metric("sleep") })
        }
        Spacer(Modifier.height(14.dp))

        // ---- Hub: deeper features ----
        val hub = listOfNotNull(
            Triple(Icons.Default.Speed, "Cardio Fitness", "fitness"), Triple(Icons.Default.Psychology, "Stress", "stress"),
            Triple(Icons.Default.Bedtime, "Sleep Profile", "sleepProfile"),
            Triple(Icons.Default.Assessment, "Reports", "reports"),
            Triple(Icons.Default.EmojiEvents, "Badges", "badges"), Triple(Icons.Default.AutoAwesome, "Thrivo Coach AI", "coach"),
            Triple(Icons.Default.Groups, "Challenges", "challenges"))
        hub.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (ic, l, k) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.15f)).clickable { open(k) }.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        T(l, 10.sp, FontWeight.Bold, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))

        // ---- Coach card ----
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp)) {
            T("TODAY'S COACH", 11.sp, FontWeight.Bold, Brand)
            Spacer(Modifier.height(6.dp))
            T(when {
                c.recovery == null -> "Wear your Fitbit for a few nights so we can learn your HRV baseline."
                c.recovery >= 67 -> "You're recovered. Your body is primed — a great day to push."
                c.recovery >= 34 -> "Moderate recovery. Train, but keep the intensity in check."
                else -> "Low recovery. Prioritise rest, an easy walk and an early night."
            }, 18.sp, FontWeight.Bold, Ink, lineHeight = 22.sp)
            Spacer(Modifier.height(14.dp))
            Row {
                CoachStat("Strain target", "%.0f–%.0f".format(c.strainTarget.start, c.strainTarget.endInclusive), Modifier.weight(1f))
                CoachStat("Sleep need", fmt(c.sleepNeed), Modifier.weight(1f))
                CoachStat("Bedtime", c.bedtime.format(DateTimeFormatter.ofPattern("h:mm a")), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            // strain progress toward target band
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE6E8F0))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((c.strainTarget.endInclusive / 21).toFloat()).padding(start = 0.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xFF2F9BFF).copy(.18f)))
                Box(Modifier.fillMaxHeight().fillMaxWidth((c.strain / 21).toFloat().coerceAtLeast(.02f)).clip(RoundedCornerShape(50)).background(Color(0xFF2F9BFF)))
            }
            T("Strain %.1f of 21 · TRIMP %.0f · max HR %d · HRV base %s · RHR base %s".format(c.strain, c.trimp, c.maxHr,
                c.hrvBaseline?.let { "%.0f ms".format(it) } ?: "–", c.rhrBaseline?.let { "%.0f bpm".format(it) } ?: "–"),
                10.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            if (c.recoveryParts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp)); DottedLine(Ink.copy(.15f)); Spacer(Modifier.height(10.dp))
                T("RECOVERY BREAKDOWN", 10.sp, FontWeight.Bold, Muted)
                c.recoveryParts.forEach { (k, v2) -> Breakdown(k, v2, Ink) }
            }
            if (c.sleepDebt.toMinutes() > 0) T("7-night sleep debt: ${fmt(c.sleepDebt)} — added to tonight's need (Van Dongen 2003).",
                10.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(14.dp))

        // ---- Fitbit Active Zone Minutes ----
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(.15f))
            .border(1.dp, Color.White.copy(.2f), RoundedCornerShape(28.dp)).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = Accent, modifier = Modifier.size(16.dp))
                T("  Active Zone Minutes", 14.sp, FontWeight.Bold, Accent, Modifier.weight(1f))
                T("Week ${c.weeklyAzm}/${Science.WEEKLY_AZM}", 11.sp, color = Color.White.copy(.7f))
            }
            NumUnit("${c.azm}", " min today", 44.sp)
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.15f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((c.weeklyAzm / Science.WEEKLY_AZM.toFloat()).coerceIn(0f, 1f)).clip(RoundedCornerShape(50)).background(Accent)) }
            T("WHO: 150 moderate-minute equivalents a week. Vigorous minutes count double.", 10.sp, color = Color.White.copy(.65f),
                modifier = Modifier.padding(bottom = 10.dp))
            val zc = listOf(Color.White.copy(.25f), Color(0xFFFFC21A), Orange, Coral)
            val total = c.zoneMinutes.sum().coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50))) {
                c.zoneMinutes.forEachIndexed { i, m -> if (m > 0) Box(Modifier.weight(m.toFloat() / total).fillMaxHeight().background(zc[i])) }
                if (c.zoneMinutes.sum() == 0) Box(Modifier.fillMaxSize().background(Color.White.copy(.15f)))
            }
            Spacer(Modifier.height(10.dp))
            Row {
                listOf("Below 40%", "Moderate", "Vigorous", "Peak 90%+").forEachIndexed { i, l ->
                    Column(Modifier.weight(1f)) { T(l, 10.sp, FontWeight.Bold, zc[i].copy(alpha = 1f)); T("${c.zoneMinutes[i]} min", 12.sp) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Sleep ----
        val n = v.nights.lastOrNull()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Color(0xFF0B0830), Color(0xFF1A137A)))).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bedtime, null, tint = Rem, modifier = Modifier.size(16.dp))
                T("  Sleep", 14.sp, FontWeight.Bold, Rem, Modifier.weight(1f))
                if (n != null) T(n.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH), 12.sp, color = Color.White.copy(.6f))
            }
            if (n == null) {
                T("No sleep yet", 30.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                T("Wear your Fitbit to bed. Sleep stages show up here after it syncs.", 12.sp, color = Color.White.copy(.7f))
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    NumUnit("${n.asleep.toHours()}", "h", 52.sp); Spacer(Modifier.width(6.dp)); NumUnit("${n.asleep.toMinutes() % 60}", "m", 52.sp)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        T("Sleep score", 10.sp, color = Color.White.copy(.6f)); T("${c.sleepScore ?: "–"}", 30.sp, FontWeight.Bold, Accent)
                    }
                }
                T("${n.start.atZone(zone).format(HM)} – ${n.end.atZone(zone).format(HM)}", 12.sp, color = Color.White.copy(.7f))
                Spacer(Modifier.height(16.dp))
                Hypnogram(n, Modifier.fillMaxWidth().height(120.dp))
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    T(n.start.atZone(zone).format(HM), 9.sp, color = Color.White.copy(.5f), modifier = Modifier.weight(1f))
                    T(n.end.atZone(zone).format(HM), 9.sp, color = Color.White.copy(.5f))
                }
                Spacer(Modifier.height(14.dp))
                val total = (n.deep + n.rem + n.light + n.awake).seconds.coerceAtLeast(1)
                listOf(Triple("Awake", n.awake, Awake), Triple("REM", n.rem, Rem), Triple("Light", n.light, Light), Triple("Deep", n.deep, Deep)).forEach { (l, d, c) ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(l, 12.sp, FontWeight.Medium, modifier = Modifier.width(52.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.1f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(d.seconds.toFloat() / total).clip(RoundedCornerShape(50)).background(c)) }
                        T(fmt(d), 12.sp, modifier = Modifier.width(64.dp).padding(start = 8.dp))
                        T("${d.seconds * 100 / total}%", 11.sp, color = Color.White.copy(.6f), modifier = Modifier.width(34.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                c.sleepParts.forEach { (k, v2) -> Breakdown(k, v2, Color.White) }
                Spacer(Modifier.height(14.dp)); DottedLine(); Spacer(Modifier.height(12.dp))
                T("Last 7 nights", 12.sp, FontWeight.Medium, Color.White.copy(.7f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    v.nights.takeLast(7).forEach { x ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            T("%.1f".format(x.asleep.toMinutes() / 60f), 9.sp, color = Color.White.copy(.7f))
                            Box(Modifier.fillMaxWidth().height((60 * (x.asleep.toMinutes() / 540f).coerceIn(.05f, 1f)).dp)
                                .clip(RoundedCornerShape(50)).background(if (x.asleep.toMinutes() >= 420) Accent else Color.White.copy(.35f)))
                            T(x.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), 10.sp, color = Color.White.copy(.6f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Heart rate today ----
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).clickable { metric("heart") }.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Coral, modifier = Modifier.size(16.dp))
                T("  Heart rate", 14.sp, FontWeight.Bold, Coral, Modifier.weight(1f))
                T("Today", 12.sp, color = Muted)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                NumUnit(v.bpmAvg?.toString() ?: "–", " bpm avg", 44.sp, Ink)
                Spacer(Modifier.weight(1f))
                if (v.bpmMin != null) T("${v.bpmMin}–${v.bpmMax} bpm", 13.sp, FontWeight.Medium, Muted)
            }
            Spacer(Modifier.height(10.dp))
            HeartChart(v.hrHourly, Modifier.fillMaxWidth().height(110.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf("12AM", "6AM", "12PM", "6PM", "").forEach { T(it, 9.sp, color = Muted, modifier = Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(14.dp))

        // ---- Vitals grid ----
        val tiles = listOf(
            Tile(Icons.Default.MonitorHeart, "Resting HR", v.resting?.toString(), "bpm", Coral,
                status(v.resting, Science.RHR_NORMAL, low = "Athletic")?.let { "$it · 60–100 (AHA)" }, key = "rhr"),
            Tile(Icons.Default.Timeline, "HRV", v.hrv?.let { "%.0f".format(it) }, "ms", Rem,
                status(v.hrv, Science.HRV_TYPICAL)?.let { "$it · 19–75 (Nunan)" }, key = "hrv"),
            Tile(Icons.Default.Air, "SpO₂", v.spo2?.let { "%.0f".format(it) }, "%", Color(0xFF2F6BFF),
                status(v.spo2, Science.SPO2_NORMAL)?.let { "$it · 95–100 (WHO)" }, key = "spo2"),
            Tile(Icons.Default.Waves, "Breathing", v.resp?.let { "%.1f".format(it) }, "br/min", Deep,
                status(v.resp, Science.RESP_NORMAL)?.let { "$it · 12–20" }, key = "resp"),
            Tile(Icons.Default.MonitorWeight, "Weight", v.weightKg?.let { weight(it).substringBefore(" ") }, if (Metric) "kg" else "lb", Orange, key = "weight"),
            Tile(Icons.Default.Bedtime, "Avg sleep", v.nights.takeIf { it.isNotEmpty() }?.let { "%.1f".format(it.map { n -> n.asleep.toMinutes() }.average() / 60) }, "h", Accent, key = "sleep"),
        )
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(.15f)).clickable { if (t.key.isNotEmpty()) metric(t.key) }
                        .border(1.dp, Color.White.copy(.2f), RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(t.icon, null, tint = t.color, modifier = Modifier.size(14.dp)); T("  ${t.label}", 12.sp, FontWeight.Medium, Color.White.copy(.85f))
                        }
                        Spacer(Modifier.height(6.dp))
                        if (t.value == null) T("–", 28.sp, FontWeight.Bold) else NumUnit(t.value, " ${t.unit}", 28.sp)
                        t.status?.let { T(it, 10.sp, FontWeight.Medium, if (it.startsWith("Normal") || it.startsWith("Athletic")) Color(0xFF7CFFB2) else Accent) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---- Workouts ----
        T("Workouts", 28.sp, FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (v.workouts.isEmpty()) T("No workouts in the last 8 days.", 13.sp, color = Color.White.copy(.75f))
        v.workouts.forEach { w ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Brand), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DirectionsRun, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    T(w.name, 15.sp, FontWeight.Bold, Ink)
                    T(w.start.atZone(zone).format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a")), 11.sp, color = Muted)
                }
                NumUnit("${w.minutes}", "min", 22.sp, Ink)
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

private data class Tile(val icon: ImageVector, val label: String, val value: String?, val unit: String, val color: Color, val status: String? = null, val key: String = "")

private fun <T : Comparable<T>> status(x: T?, r: ClosedRange<T>, low: String = "Low", high: String = "High") =
    x?.let { if (it < r.start) low else if (it > r.endInclusive) high else "Normal" }


@Composable
fun Hypnogram(n: Night, modifier: Modifier) = Canvas(modifier) {
    val span = Duration.between(n.start, n.end).seconds.toFloat().coerceAtLeast(1f)
    val rowH = size.height / 4
    fun row(t: Int) = when (t) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> 0
        SleepSessionRecord.STAGE_TYPE_REM -> 1; SleepSessionRecord.STAGE_TYPE_DEEP -> 3; else -> 2
    }
    val colors = listOf(Awake, Rem, Light, Deep)
    for (i in 0..3) drawLine(Color.White.copy(.08f), Offset(0f, rowH * i + rowH / 2), Offset(size.width, rowH * i + rowH / 2), 1f)
    if (n.stages.isEmpty()) { // no stage data: one light block
        drawRoundRect(Light, Offset(0f, rowH * 2 + rowH * .2f), Size(size.width, rowH * .6f), CornerRadius(8f)); return@Canvas
    }
    var prev: Pair<Float, Int>? = null
    n.stages.forEach { s ->
        val x0 = Duration.between(n.start, s.start).seconds / span * size.width
        val x1 = Duration.between(n.start, s.end).seconds / span * size.width
        val r = row(s.type)
        prev?.let { (px, pr) -> drawLine(Color.White.copy(.25f), Offset(x0, rowH * pr + rowH / 2), Offset(x0, rowH * r + rowH / 2), 2f) }
        drawRoundRect(colors[r], Offset(x0, rowH * r + rowH * .2f), Size((x1 - x0).coerceAtLeast(3f), rowH * .6f), CornerRadius(6f))
        prev = x1 to r
    }
}

@Composable
fun HeartChart(hourly: List<Long?>, modifier: Modifier) = Canvas(modifier) {
    val pts = hourly.mapIndexedNotNull { h, v -> v?.let { h to it } }
    if (pts.isEmpty()) {
        drawLine(Muted.copy(.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))); return@Canvas
    }
    val lo = (pts.minOf { it.second } - 10).coerceAtLeast(30); val hi = pts.maxOf { it.second } + 10
    fun x(h: Int) = (h + .5f) / 24 * size.width
    fun y(v: Long) = size.height - (v - lo).toFloat() / (hi - lo) * size.height
    val line = Path(); val area = Path()
    pts.forEachIndexed { i, (h, v) ->
        if (i == 0) { line.moveTo(x(h), y(v)); area.moveTo(x(h), size.height); area.lineTo(x(h), y(v)) }
        else { line.lineTo(x(h), y(v)); area.lineTo(x(h), y(v)) }
    }
    area.lineTo(x(pts.last().first), size.height); area.close()
    drawPath(area, Brush.verticalGradient(listOf(Coral.copy(.35f), Coral.copy(0f))))
    drawPath(line, Coral, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    pts.forEach { (h, v) -> drawCircle(Color.White, 3.dp.toPx(), Offset(x(h), y(v))); drawCircle(Coral, 2.dp.toPx(), Offset(x(h), y(v))) }
}

@Composable
fun Dial(label: String, value: String, frac: Float, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF0B0830)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(Color.White.copy(.1f), 135f, 270f, false, style = st)
                drawArc(color, 135f, 270f * frac.coerceIn(0f, 1f), false, style = st)
            }
            T(value, 20.sp, FontWeight.Bold)
        }
        T(label.uppercase(), 10.sp, FontWeight.Bold, color)
    }
}

@Composable
fun CoachStat(label: String, value: String, modifier: Modifier) =
    Column(modifier) { T(label, 10.sp, color = Muted); T(value, 16.sp, FontWeight.Bold, Ink) }

@Composable
fun Breakdown(k: String, v: String, color: Color) = Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
    T(k, 12.sp, FontWeight.Medium, color, Modifier.weight(1f)); T(v, 12.sp, color = color.copy(.7f))
}

@Composable
fun ScienceScreen(back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Box(Modifier.fillMaxWidth()) {
            CircleBtn(Icons.AutoMirrored.Filled.ArrowBack, 36.dp, label = "Back", onClick = back)
            T("The Science", 14.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(18.dp))
        T("Built on research", 36.sp, FontWeight.Bold, lineHeight = 38.sp)
        T("Every score and range in ${APP_NAME} comes from peer-reviewed studies and health guidelines. Recovery, Strain and Sleep scores are our " +
          "own estimates using these methods. They are not Whoop's or Fitbit's proprietary algorithms, and not medical advice.",
            12.sp, color = Color.White.copy(.8f), lineHeight = 16.sp, modifier = Modifier.padding(vertical = 10.dp))
        REFERENCES.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) {
                T(r.usedFor, 13.sp, FontWeight.Bold, Ink, lineHeight = 17.sp)
                T(r.cite, 11.sp, color = Muted, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
