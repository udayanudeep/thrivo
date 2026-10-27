package app.thrivo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SubScreen(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            CircleBtn(Icons.AutoMirrored.Filled.ArrowBack, 36.dp, label = "Back", onClick = back)
            T(title, 14.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
        content()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun WhiteCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp), content = content)

// ======================= Journal =======================
val BEHAVIORS = listOf(
    "caffeine" to "Caffeine after 2 PM", "alcohol" to "Alcohol", "late_meal" to "Late meal (< 2 h before bed)",
    "screens" to "Screens in bed", "stretch" to "Stretched / meditated", "sun" to "Morning sunlight",
    "hydrated" to "Hit water goal", "stress" to "Stressful day", "sick" to "Feeling sick", "travel" to "Travel")

class Journal(private val repo: Repo) {
    private fun all(): JSONObject = runCatching { JSONObject(repo.getString("journal", "{}")) }.getOrDefault(JSONObject())
    fun get(d: LocalDate): Set<String> = all().optJSONArray(d.toString())?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
    fun set(d: LocalDate, v: Set<String>) { val o = all(); o.put(d.toString(), JSONArray(v.toList())); repo.putString("journal", o.toString()) }
    fun logged(): Map<LocalDate, Set<String>> = all().let { o -> o.keys().asSequence().associate { LocalDate.parse(it) to get(LocalDate.parse(it)) } }
}

data class Insight(val label: String, val sleepDeltaMin: Int?, val hrvDelta: Double?, val yes: Int, val no: Int)

fun insights(j: Journal, v: Vitals): List<Insight> {
    val log = j.logged(); if (log.isEmpty()) return emptyList()
    val nightBy = v.nights.associateBy { it.date }
    val hrvBy = v.hrvDaily.mapIndexedNotNull { i, x -> x?.let { LocalDate.now().minusDays(29L - i) to it } }.toMap()
    return BEHAVIORS.mapNotNull { (key, label) ->
        val yes = log.filter { key in it.value }.keys; val no = log.filter { key !in it.value }.keys
        if (yes.size < 3 || no.size < 3) return@mapNotNull Insight(label, null, null, yes.size, no.size)
        fun sleepAvg(ds: Set<LocalDate>) = ds.mapNotNull { nightBy[it.plusDays(1)]?.asleep?.toMinutes() }.takeIf { it.isNotEmpty() }?.average()
        fun hrvAvg(ds: Set<LocalDate>) = ds.mapNotNull { hrvBy[it.plusDays(1)] }.takeIf { it.isNotEmpty() }?.average()
        val sd = sleepAvg(yes)?.let { a -> sleepAvg(no)?.let { b -> (a - b).roundToInt() } }
        val hd = hrvAvg(yes)?.let { a -> hrvAvg(no)?.let { b -> a - b } }
        Insight(label, sd, hd, yes.size, no.size)
    }
}

@Composable
fun JournalScreen(j: Journal, v: Vitals, back: () -> Unit) = SubScreen("Journal", back) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    var sel by remember(day) { mutableStateOf(j.get(day)) }
    T("How was your day?", 32.sp, FontWeight.Bold)
    T("Log what you did. After 3+ days with and without a habit, ${APP_NAME} shows how it changes your next night's sleep and HRV.", 12.sp,
        color = Color.White.copy(.8f), lineHeight = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleBtn(Icons.Default.ChevronLeft, 32.dp) { day = day.minusDays(1) }
        T(if (day == LocalDate.now()) "Today" else day.toString(), 15.sp, FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
        if (day.isBefore(LocalDate.now())) CircleBtn(Icons.Default.ChevronRight, 32.dp) { day = day.plusDays(1) }
    }
    Spacer(Modifier.height(12.dp))
    WhiteCard {
        BEHAVIORS.forEach { (k, label) ->
            Row(Modifier.fillMaxWidth().clickable { sel = if (k in sel) sel - k else sel + k; j.set(day, sel) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                T(label, 14.sp, FontWeight.Medium, Ink, Modifier.weight(1f))
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (k in sel) Brand else Color(0xFFE6E8F0)), contentAlignment = Alignment.Center) {
                    if (k in sel) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
    }
    T("Insights", 24.sp, FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    insights(j, v).forEach { i ->
        WhiteCard {
            T(i.label, 14.sp, FontWeight.Bold, Ink)
            if (i.sleepDeltaMin == null && i.hrvDelta == null) T("Need 3+ days with and without (have ${i.yes} / ${i.no})", 11.sp, color = Muted)
            else Row(Modifier.padding(top = 6.dp)) {
                i.sleepDeltaMin?.let { T("${if (it >= 0) "+" else ""}$it min sleep", 13.sp, FontWeight.Bold, if (it >= 0) Color(0xFF0FA958) else Coral, Modifier.weight(1f)) }
                i.hrvDelta?.let { T("${if (it >= 0) "+" else ""}${"%.0f".format(it)} ms HRV", 13.sp, FontWeight.Bold, if (it >= 0) Color(0xFF0FA958) else Coral) }
            }
        }
    }
    T("Associations from your own data, not proof of cause.", 10.sp, color = Color.White.copy(.6f))
}

// ======================= Reports =======================
@Composable
fun ReportsScreen(d: Today, v: Vitals, goal: Int, back: () -> Unit) = SubScreen("Reports", back) {
    var month by remember { mutableStateOf(false) }
    Row(Modifier.clip(RoundedCornerShape(50)).background(Glass).padding(3.dp)) {
        listOf("Weekly", "Monthly").forEachIndexed { i, t ->
            T(t, 14.sp, FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(if (month == (i == 1)) Color.White.copy(.3f) else Color.Transparent)
                .clickable { month = i == 1 }.padding(horizontal = 16.dp, vertical = 6.dp))
        }
    }
    val n = if (month) 30 else 7
    fun <T> cur(l: List<T>) = l.takeLast(n)
    fun <T> prev(l: List<T>) = l.dropLast(n).takeLast(n)
    val steps = cur(d.yearSteps); val pSteps = prev(d.yearSteps)
    val sleepBy = v.nights.associateBy { it.date }
    fun sleepAvg(days: IntRange) = days.mapNotNull { sleepBy[LocalDate.now().minusDays(it.toLong())]?.asleep?.toMinutes() }.takeIf { it.isNotEmpty() }?.average()
    val rows = listOf(
        Triple("Avg steps", fmtInt(steps.average()), pSteps.average().let { if (it > 0) (steps.average() - it) / it * 100 else null }),
        Triple("Distance", dist(cur(d.yearMeters).sum()), prev(d.yearMeters).sum().let { if (it > 0) (cur(d.yearMeters).sum() - it) / it * 100 else null }),
        Triple("Days on goal", "${steps.count { it >= goal }} / $n", null),
        Triple("Avg sleep", sleepAvg(0 until n)?.let { fmt(java.time.Duration.ofMinutes(it.toLong())) } ?: "–",
            sleepAvg(0 until n)?.let { a -> sleepAvg(n until 2 * n)?.let { b -> (a - b) / b * 100 } }),
        Triple("Avg HRV", v.hrvDaily.takeLast(minOf(n, 30)).filterNotNull().takeIf { it.isNotEmpty() }?.average()?.let { "%.0f ms".format(it) } ?: "–", null),
        Triple("Avg resting HR", v.rhrDaily.takeLast(minOf(n, 30)).filterNotNull().takeIf { it.isNotEmpty() }?.average()?.let { "%.0f bpm".format(it) } ?: "–", null),
        Triple("Avg water", vol(d.water30.takeLast(minOf(n, 30)).average()), null),
    )
    Spacer(Modifier.height(14.dp))
    WhiteCard {
        rows.forEach { (k, value, delta) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T(k, 13.sp, FontWeight.Medium, Muted, Modifier.weight(1f)); T(value, 16.sp, FontWeight.Bold, Ink)
                delta?.let { T("  ${if (it >= 0) "▲" else "▼"} ${"%.0f".format(kotlin.math.abs(it))}%", 11.sp, FontWeight.Bold, if (it >= 0) Color(0xFF0FA958) else Coral) }
            }
            DottedLine(Ink.copy(.1f))
        }
    }
    val best = steps.indices.maxByOrNull { steps[it] }
    if (best != null && steps[best] > 0) WhiteCard {
        T("BEST DAY", 10.sp, FontWeight.Bold, Brand)
        T("${fmtInt(steps[best])} steps", 26.sp, FontWeight.Bold, Ink)
        T(LocalDate.now().minusDays((n - 1 - best).toLong()).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } + ", " +
          LocalDate.now().minusDays((n - 1 - best).toLong()), 12.sp, color = Muted)
    }
    T("▲▼ vs the previous ${if (month) "30 days" else "7 days"}.", 10.sp, color = Color.White.copy(.6f))
}

// ======================= Badges =======================
data class Badge(val icon: ImageVector, val title: String, val sub: String, val earned: Boolean)

fun badges(d: Today, goal: Int): List<Badge> {
    val best = d.yearSteps.maxOrNull() ?: 0; val km = d.yearMeters.sum() / 1000
    val (_, longest) = streaks(d.yearSteps, goal)
    return listOf(5000, 10000, 15000, 20000, 25000, 30000).map { Badge(Icons.Default.DirectionsWalk, "${it / 1000}K day", "${fmtInt(it)} steps in a day", best >= it) } +
        listOf(42.2 to "Marathon", 100.0 to "Century", 250.0 to "Explorer", 500.0 to "Trailblazer", 1000.0 to "1,000 km", 2000.0 to "Globetrotter").map { (k, n) ->
            Badge(Icons.Default.Public, n, "${fmtInt(k)} km lifetime", km >= k) } +
        listOf(3, 7, 14, 30, 60, 100).map { Badge(Icons.Default.LocalFireDepartment, "$it-day streak", "Hit your goal $it days in a row", longest >= it) }
}

@Composable
fun BadgesScreen(d: Today, goal: Int, back: () -> Unit) = SubScreen("Badges", back) {
    val all = badges(d, goal)
    T("${all.count { it.earned }} of ${all.size}", 40.sp, FontWeight.Bold)
    T("badges earned in the last year · ${"%.0f".format(d.yearMeters.sum() / 1000)} km walked", 12.sp, color = Color.White.copy(.8f))
    Spacer(Modifier.height(16.dp))
    all.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { b ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(if (b.earned) Accent else Color.White.copy(.12f)).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(b.icon, null, tint = if (b.earned) Ink else Color.White.copy(.4f), modifier = Modifier.size(28.dp))
                    T(b.title, 12.sp, FontWeight.Bold, if (b.earned) Ink else Color.White.copy(.6f), maxLines = 1)
                    T(b.sub, 8.sp, color = if (b.earned) Ink.copy(.7f) else Color.White.copy(.4f), lineHeight = 10.sp)
                }
            }
        }
    }
}

// ======================= Shoes =======================
data class Shoe(val id: Long, val name: String, val limitKm: Int, val baseKm: Double, val activeSince: LocalDate?)

class ShoeStore(private val repo: Repo) {
    fun list(): List<Shoe> = runCatching {
        val a = JSONArray(repo.getString("shoes", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Shoe(o.getLong("id"), o.getString("name"), o.getInt("limit"), o.getDouble("base"),
            o.optString("since").takeIf { s -> s.isNotEmpty() }?.let(LocalDate::parse)) } }
    }.getOrDefault(emptyList())
    private fun save(l: List<Shoe>) = repo.putString("shoes", JSONArray(l.map { JSONObject().put("id", it.id).put("name", it.name)
        .put("limit", it.limitKm).put("base", it.baseKm).put("since", it.activeSince?.toString() ?: "") }).toString())
    fun km(s: Shoe, d: Today): Double = s.baseKm + (s.activeSince?.let { since ->
        d.yearMeters.withIndex().filter { !LocalDate.now().minusDays(364L - it.index).isBefore(since) }.sumOf { it.value } / 1000 } ?: 0.0)
    fun add(name: String, limit: Int, d: Today) { val l = list(); save(l.map { freeze(it, d) } + Shoe(System.currentTimeMillis(), name, limit, 0.0, LocalDate.now())) }
    fun activate(id: Long, d: Today) = save(list().map { if (it.id == id) it.copy(activeSince = LocalDate.now()) else freeze(it, d) })
    fun remove(id: Long) = save(list().filter { it.id != id })
    private fun freeze(s: Shoe, d: Today) = if (s.activeSince == null) s else s.copy(baseKm = km(s, d), activeSince = null)
}

@Composable
fun ShoesScreen(store: ShoeStore, d: Today, back: () -> Unit) = SubScreen("My Shoes", back) {
    var shoes by remember { mutableStateOf(store.list()) }
    var adding by remember { mutableStateOf(false) }
    T("My Shoes", 34.sp, FontWeight.Bold)
    T("Your walking distance is added to the active pair. Replace running shoes around 500–800 km.", 12.sp, color = Color.White.copy(.8f), lineHeight = 16.sp)
    Spacer(Modifier.height(14.dp))
    shoes.forEach { s ->
        val km = store.km(s, d); val frac = (km / s.limitKm).toFloat()
        WhiteCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hiking, null, tint = Brand)
                T("  ${s.name}", 16.sp, FontWeight.Bold, Ink, Modifier.weight(1f))
                if (s.activeSince != null) T("ACTIVE", 10.sp, FontWeight.Bold, Brand)
                else TextButton({ store.activate(s.id, d); shoes = store.list() }) { T("Use", 13.sp, FontWeight.Bold, Brand) }
                IconButton({ store.remove(s.id); shoes = store.list() }) { Icon(Icons.Default.Delete, null, tint = Muted) }
            }
            NumUnit(if (Metric) "%.1f".format(km) else "%.1f".format(km / 1.609344), if (Metric) " / ${s.limitKm} km" else " / ${(s.limitKm / 1.609344).toInt()} mi", 26.sp, Ink)
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE6E8F0))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).clip(RoundedCornerShape(50))
                    .background(when { frac >= 1 -> Coral; frac >= .8 -> Orange; else -> Brand })) }
            if (frac >= .8) T(if (frac >= 1) "Time for a new pair." else "Getting close to replacement.", 11.sp, FontWeight.Bold, Coral, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable { adding = true }, contentAlignment = Alignment.Center) {
        T("+ Add shoes", 15.sp, FontWeight.Bold, Brand) }
    if (adding) {
        var name by remember { mutableStateOf("") }; var limit by remember { mutableStateOf("650") }
        AlertDialog({ adding = false }, containerColor = Color.White, title = { T("New shoes", 20.sp, FontWeight.Bold, Ink) },
            text = { Column {
                OutlinedTextField(name, { name = it.take(30) }, singleLine = true, label = { Text("Name") })
                OutlinedTextField(limit, { limit = it.filter(Char::isDigit).take(4) }, singleLine = true, label = { Text("Replace at (km)") })
            } },
            confirmButton = { TextButton({ if (name.isNotBlank()) { store.add(name.trim(), limit.toIntOrNull() ?: 650, d); shoes = store.list() }; adding = false }) {
                T("Add", 16.sp, FontWeight.Bold, Brand) } })
    }
}

// ======================= Share card =======================
fun shareStreakCard(ctx: Context, name: String, steps: Long, streak: Int, best: Int) {
    val w = 1080; val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = ACanvas(bmp)
    c.drawColor(0xFF2B1EF5.toInt())
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 18f; color = 0xFF3D6BFF.toInt() }
    for (i in 0 until 6) { val inset = 60f + i * 44f; c.drawRoundRect(RectF(inset, inset, w - inset, 620f - inset + 60f), 400f, 400f, ring) }
    val bold = ResourcesCompat.getFont(ctx, R.font.space_grotesk_700); val med = ResourcesCompat.getFont(ctx, R.font.space_grotesk_500)
    fun p(size: Float, color: Int, tf: android.graphics.Typeface?) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = tf }
    val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 34f; strokeCap = Paint.Cap.ROUND; color = 0xFF3D6BFF.toInt() }
    c.drawArc(RectF(w / 2f - 120f, 230f, w / 2f + 120f, 470f), 120f, 300f, false, mark)
    c.drawCircle(w / 2f + 60f, 350f + 104f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3D6BFF.toInt() })
    c.drawText("${streak}d", 80f, 860f, p(260f, 0xFFFFF200.toInt(), bold))
    c.drawText("current streak", 90f, 930f, p(52f, -1, med))
    c.drawText("%,d steps today".format(steps), 90f, 1060f, p(72f, -1, bold))
    c.drawText("longest streak ${best}d", 90f, 1140f, p(48f, 0xCCFFFFFF.toInt(), med))
    c.drawText("$name · ${APP_NAME}", 90f, 1270f, p(40f, 0x99FFFFFF.toInt(), med))
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }; val f = File(dir, "streak.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
}

/** Generic file share (CSV / backup). */
fun shareFile(ctx: Context, name: String, mime: String, text: String) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }; val f = File(dir, name); f.writeText(text)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
}
