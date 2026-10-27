package app.thrivo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.format.DateTimeFormatter

class SettingsState(val repo: Repo) {
    var stepGoal by mutableIntStateOf(repo.stepGoal)
    var waterGoal by mutableIntStateOf(repo.waterGoal)
    var glass by mutableIntStateOf(repo.glassMl)
    var remaining by mutableStateOf(repo.waterRemaining)
    var reminders by mutableStateOf(repo.reminders)
    var metric by mutableStateOf(repo.metric)
    var weekMon by mutableStateOf(repo.weekStartsMonday)
    var bottle by mutableIntStateOf(repo.bottle)
    var age by mutableIntStateOf(repo.age)
    var female by mutableStateOf(repo.female)
    private val p = repo.raw
    /** 0 off, 1 on, 2 follow system (migrates the old on/off Focus Mode flag) */
    var themeMode by mutableIntStateOf(p.getInt("themeMode", if (p.getBoolean("focus", false)) 1 else 0))
    var briefingMin by mutableIntStateOf(p.getInt("briefingMin", 8 * 60))
    var bar by mutableIntStateOf(p.getInt("bar", 0))
    var lang by mutableStateOf(p.getString("lang", "en")!!)
    var icon by mutableStateOf(p.getString("icon", "IconDefault")!!)
    var briefing by mutableStateOf(p.getBoolean("briefing", true))
    var winddown by mutableStateOf(p.getBoolean("winddown", true))
    fun save(k: String, v: Any) { p.edit().apply { when (v) { is Boolean -> putBoolean(k, v); is Int -> putInt(k, v); else -> putString(k, v.toString()) } }.apply() }
    var name by mutableStateOf(repo.name)
}

@Composable
fun SettingsScreen(s: SettingsState, d: Today, healthOk: Boolean, open: (String) -> Unit, connect: () -> Unit) {
    val ctx = LocalContext.current
    var progressMenu by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            HeroArt(Modifier.align(Alignment.TopEnd).size(200.dp, 110.dp).offset(x = 60.dp, y = (-30).dp))
            CircleBtn(Icons.Default.Tune, modifier = Modifier.align(Alignment.TopEnd), label = "More settings") { open("more") }
        }
        Box(Modifier.padding(start = 6.dp).size(86.dp).clip(CircleShape).background(Color(0xFF3F5BFF)).border(4.dp, Color.White.copy(.25f), CircleShape),
            contentAlignment = Alignment.Center) {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsWalk, null, tint = Brand, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.2f)).clickable { editName = true }
            .semantics { contentDescription = "Edit name" }.padding(horizontal = 12.dp, vertical = 6.dp)) { T(s.name, 26.sp, FontWeight.Bold) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column { T("Since", 8.sp, color = Color.White.copy(.7f)); T(s.repo.since.format(DateTimeFormatter.ofPattern("MMM dd, ''yy")), 15.sp, FontWeight.Medium) }
            Box(Modifier.padding(horizontal = 16.dp).size(1.dp, 26.dp).background(Color.White.copy(.3f)))
            Column(Modifier.weight(1f)) { T("Total Steps", 8.sp, color = Color.White.copy(.7f)); T(fmtInt(d.yearSteps.sum()), 15.sp, FontWeight.Medium) }
            Box(Modifier.size(52.dp, 36.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.2f)).clickable {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "I've walked ${fmtInt(d.yearSteps.sum())} steps with ${APP_NAME}!"), null))
            }.semantics { contentDescription = "Share progress" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.IosShare, null, tint = Color.White.copy(.8f), modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(16.dp)); DottedLine(); Spacer(Modifier.height(16.dp))
        // Health Connect card (in the slot of the subscribe card)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(.25f)).padding(5.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFF2F6BFF)).clickable(onClick = connect).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(28.dp)
            Column(Modifier.padding(start = 12.dp)) {
                T(if (healthOk) "Fitbit connected" else "Connect your Fitbit", 15.sp, FontWeight.Bold)
                T(if (healthOk) "Steps, sleep and vitals sync through Health Connect." else "Allow Health Connect access to sync steps, sleep and heart data.", 10.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        T("My Goals", 18.sp, FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoalTile(fmtInt(s.stepGoal), "Daily Steps", Modifier.weight(1f)) { open("stepGoal") }
            GoalTile(vol(s.waterGoal), "Water Intake", Modifier.weight(1f)) { open("waterGoal") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.25f)).padding(4.dp)
            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF2F6BFF)).clickable { open("plan") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Route, null, tint = Color.White, modifier = Modifier.size(16.dp))
            T("Smart Walking Plan", 13.sp, FontWeight.Medium, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
        }
        Spacer(Modifier.height(10.dp))
        // yellow feature card -> Health
        Column(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))
            .background(Brush.radialGradient(listOf(Color(0xFFFFB000), Accent), radius = 500f)).clickable { open("health") }.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Health", 18.sp, FontWeight.Bold, Ink, Modifier.weight(1f))
                Box(Modifier.size(34.dp, 22.dp).clip(RoundedCornerShape(50)).background(Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Icons.Default.Bedtime to "Sleep", Icons.Default.Favorite to "Heart", Icons.Default.Air to "SpO₂", Icons.Default.FitnessCenter to "Workouts").forEach { (ic, l) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.7f)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, tint = Brand, modifier = Modifier.size(22.dp)); T(l, 10.sp, FontWeight.Bold, Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SettingsSection("Steps") {
            SettingRow(Icons.Default.Flag, "Daily Step Goal", fmtInt(s.stepGoal)) { open("stepGoal") }
            SettingRow(Icons.Default.Sensors, "Data source", appLabel(ctx, s.repo.stepSource).take(18)) { open("stepSource") }
            SettingRow(Icons.Default.Hiking, "My Shoes") { open("shoes") }
            SettingRow(Icons.Default.Straighten, "Unit System", if (s.metric) "Metric" else "Imperial", picker = true, last = true) {
                s.metric = !s.metric; s.repo.metric = s.metric; Metric = s.metric }
        }
        SettingsSection("Progress") {
            SettingRow(Icons.Default.EmojiEvents, "Badges") { open("badges") }
            SettingRow(Icons.Default.Assessment, "Weekly & Monthly Reports") { open("reports") }
            SettingRow(Icons.Default.EditNote, "Journal") { open("journal") }
            SettingRow(Icons.Default.AutoAwesome, "Thrivo Coach AI", last = true) { open("coach") }
        }
        SettingsSection("Water") {
            SettingRow(Icons.Default.WaterDrop, "My Bottle", BOTTLES[s.bottle], go = true) { open("bottles") }
            Box {
                SettingRow(Icons.Default.LocalDrink, "Water Progress", if (s.remaining) "Remaining" else "Consumed", picker = true) { progressMenu = true }
                DropdownMenu(progressMenu, { progressMenu = false }, Modifier.background(Color.White), offset = androidx.compose.ui.unit.DpOffset(200.dp, 0.dp)) {
                    listOf(false to ("Consumed" to "Fill up as I drink"), true to ("Remaining" to "Empty as I drink")).forEach { (v, l) ->
                        DropdownMenuItem({ Column { T(l.first, 15.sp, FontWeight.Medium, Ink); T(l.second, 11.sp, color = Muted) } },
                            { s.remaining = v; s.repo.waterRemaining = v; progressMenu = false },
                            leadingIcon = { if (s.remaining == v) Icon(Icons.Default.Check, null, tint = Ink) else Spacer(Modifier.size(24.dp)) })
                    }
                }
            }
            SettingRow(Icons.Default.Alarm, "Water Reminder", if (s.reminders) "On" else "Off", last = true) { open("waterGoal") }
        }
        SettingsSection("App Settings") {
            SettingRow(Icons.Default.Favorite, "Health Connect", if (healthOk) "Connected" else "Connect") { connect() }
            SettingRow(Icons.Default.Download, "Export data (CSV)") { open("exportCsv") }
            SettingRow(Icons.Default.Backup, "Back up settings") { open("backup") }
            SettingRow(Icons.Default.Restore, "Restore settings") { open("restore") }
            SettingRow(Icons.Default.Tune, "More Settings", last = true) { open("more") }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Section).padding(14.dp)) {
            T("Having trouble with the app?", 16.sp, FontWeight.Medium)
            T("Reach out through any of the channels below.", 10.sp, color = Color.White.copy(.8f))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Icons.Default.Email to "mailto:muvsanudeep@gmail.com", Icons.Default.BugReport to "mailto:muvsanudeep@gmail.com?subject=${APP_NAME}%20bug").forEach { (ic, url) ->
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.2f))
                        .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, contentAlignment = Alignment.Center) {
                        Icon(ic, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        T("Important: Data displayed in this app comes from Health Connect and your connected wearables and may not be fully accurate. " +
          "Smartwatches are not medical devices, and this app should not be used as a substitute for medical advice, diagnosis, or treatment.",
            10.sp, color = Color.White.copy(.6f), lineHeight = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(120.dp))
    }
    if (editName) {
        var t by remember { mutableStateOf(s.name) }
        AlertDialog({ editName = false }, containerColor = Color.White, title = { T("Your name", 20.sp, FontWeight.Bold, Ink) },
            text = { OutlinedTextField(t, { t = it.take(20) }, singleLine = true) },
            confirmButton = { TextButton({ if (t.isNotBlank()) { s.name = t.trim(); s.repo.name = s.name }; editName = false }) { T("Save", 16.sp, FontWeight.Bold, Brand) } })
    }
}

@Composable
fun GoalTile(value: String, label: String, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(.25f)).padding(4.dp)
        .clip(RoundedCornerShape(16.dp)).background(Color(0xFF2F6BFF)).clickable(onClick = onClick).padding(14.dp)) {
        Column { T(value, 20.sp, FontWeight.Bold); T(label, 12.sp, FontWeight.Medium, Color.White.copy(.85f)) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).size(18.dp))
    }

@Composable
fun StepGoalSheet(goal: Int, age: Int, onGoal: (Int) -> Unit, close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black, Color.Black, BrandDeep, Brand)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            Row { Spacer(Modifier.weight(1f)); CircleBtn(Icons.Default.Close, 36.dp, label = "Close", onClick = close) }
            Spacer(Modifier.height(10.dp))
            Icon(Icons.Default.DirectionsWalk, null, tint = Color(0xFF3D6BFF), modifier = Modifier.align(Alignment.CenterHorizontally).size(90.dp))
            Spacer(Modifier.height(30.dp))
            T("Daily Steps", 32.sp, FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NumUnit(fmtInt(goal), "steps", 40.sp) }
                Stepper({ onGoal((goal - 500).coerceAtLeast(1000)) }, { onGoal((goal + 500).coerceAtMost(50000)) })
            }
            Spacer(Modifier.height(12.dp))
            T("Research suggests ${fmtInt(Science.stepGoal(age))} for you — mortality benefit levels off around 8,000–10,000 steps under 60 and " +
              "6,000–8,000 at 60+ (Paluch et al., Lancet Public Health 2022).", 12.sp, color = Color.White.copy(.75f), lineHeight = 16.sp)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.15f)).clickable { onGoal(Science.stepGoal(age)) }
                .padding(horizontal = 16.dp, vertical = 8.dp)) { T("Use ${fmtInt(Science.stepGoal(age))}", 13.sp, FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            DoneButton(close, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MoreSettingsScreen(s: SettingsState, healthOk: Boolean, connect: () -> Unit, open: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    if (pickTime) {
        val tp = rememberTimePickerState(s.briefingMin / 60, s.briefingMin % 60)
        AlertDialog({ pickTime = false }, containerColor = Color.White, title = { T("Briefing time", 20.sp, FontWeight.Bold, Ink) },
            text = { TimePicker(tp) },
            confirmButton = { TextButton({ s.briefingMin = tp.hour * 60 + tp.minute; s.save("briefingMin", s.briefingMin); scheduleAll(ctx); pickTime = false }) {
                T("Save", 16.sp, FontWeight.Bold, Brand) } })
    }
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
            CircleBtn(Icons.AutoMirrored.Filled.ArrowBack, 36.dp, label = "Back", onClick = back)
            T("Settings", 14.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
        SettingsSection("App Settings") {
            SettingRow(Icons.Default.Straighten, "Unit System", if (s.metric) "Metric" else "Imperial", picker = true) { s.metric = !s.metric; s.repo.metric = s.metric; Metric = s.metric }
            SettingRow(Icons.Default.CalendarMonth, "Week Starts On", if (s.weekMon) "Monday" else "Sunday", picker = true) { s.weekMon = !s.weekMon; s.repo.weekStartsMonday = s.weekMon }
            SettingRow(Icons.Default.Cake, "Age (for heart-rate zones)", trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Remove, null, tint = Ink, modifier = Modifier.clickable { s.age = (s.age - 1).coerceAtLeast(13); s.repo.age = s.age }.padding(6.dp).size(18.dp))
                    T("${s.age}", 15.sp, FontWeight.Bold, Ink)
                    Icon(Icons.Default.Add, null, tint = Ink, modifier = Modifier.clickable { s.age = (s.age + 1).coerceAtMost(99); s.repo.age = s.age }.padding(6.dp).size(18.dp))
                } })
            SettingRow(Icons.Default.Person, "Sex (for training load & water)", if (s.female) "Female" else "Male", picker = true) {
                s.female = !s.female; s.repo.female = s.female }
            SwitchRow(Icons.Default.Notifications, "Water Reminders", s.reminders, last = true) { s.reminders = it; s.repo.reminders = it; scheduleReminders(ctx, it) }
        }
        SettingsSection("Appearance") {
            SettingRow(Icons.Default.DarkMode, "Focus Mode (dark)", listOf("Off", "On", "Follow system")[s.themeMode], picker = true) {
                s.themeMode = (s.themeMode + 1) % 3; s.save("themeMode", s.themeMode) }
            SettingRow(Icons.Default.BarChart, "Steps Theme", BAR_THEMES[s.bar].first, picker = true) {
                s.bar = (s.bar + 1) % BAR_THEMES.size; s.save("bar", s.bar) }
            SettingRow(Icons.Default.Apps, "App Icon", ICONS.first { it.first == s.icon }.second, picker = true) {
                val next = ICONS[(ICONS.indexOfFirst { it.first == s.icon } + 1) % ICONS.size].first; s.icon = next; setAppIcon(ctx, next) }
            SettingRow(Icons.Default.Translate, "Language", LANGS.first { it.first == s.lang }.second, picker = true, last = true) {
                val next = LANGS[(LANGS.indexOfFirst { it.first == s.lang } + 1) % LANGS.size].first; s.lang = next; s.save("lang", next); Lang = next }
        }
        SettingsSection("Notifications") {
            SwitchRow(Icons.Default.WbSunny, "Morning briefing", s.briefing) { s.briefing = it; s.save("briefing", it); scheduleAll(ctx) }
            SettingRow(Icons.Default.Schedule, "Briefing time", java.time.LocalTime.ofSecondOfDay(s.briefingMin * 60L).format(DateTimeFormatter.ofPattern("h:mm a"))) { pickTime = true }
            SwitchRow(Icons.Default.Bedtime, "Wind-down reminder", s.winddown) { s.winddown = it; s.save("winddown", it); scheduleAll(ctx) }
            SettingRow(Icons.Default.Medication, "Medication reminders", last = true) { open("meds") }
        }
        SettingsSection("AI Coach") {
            SettingRow(Icons.Default.Key, "Anthropic API key", if (s.repo.apiKey.isBlank()) "Not set" else "••••" + s.repo.apiKey.takeLast(4), last = true) { open("apiKey") }
        }
        SettingsSection("Research") {
            SettingRow(Icons.Default.Science, "The Science", "${REFERENCES.size} sources", last = true) { open("science") }
        }
        SettingsSection("Health Connect") {
            SettingRow(Icons.Default.Favorite, "Connect with Health Connect", trailing = {
                if (healthOk) Icon(Icons.Default.Check, null, tint = Ink) else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink) }) { connect() }
            SettingRow(Icons.Default.Build, "Manage Permission") {
                runCatching { ctx.startActivity(Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, ctx.packageName)) }
                    .onFailure { runCatching { ctx.startActivity(Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")) } } }
            T("Fitbit writes your steps, sleep and heart data to Health Connect. If something is missing, open the Fitbit app → You → " +
              "Settings → Health Connect and turn on the data types you want to share.", 11.sp, color = Ink, lineHeight = 15.sp, modifier = Modifier.padding(16.dp))
        }
    }
}
