package app.thrivo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(s)
        val startTab = intent.getIntExtra("tab", 0)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, background = Brand, surface = Color.White)) { App(Repo(this), startTab) } }
    }
}

private val TABS = listOf("Steps" to Icons.Default.DirectionsWalk, "Water" to Icons.Default.WaterDrop, "Plan" to Icons.Default.Route,
    "Health" to Icons.Default.Favorite, "Settings" to Icons.Default.Settings)

@Composable
fun App(repo: Repo, startTab: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { SettingsState(repo) }
    val journal = remember { Journal(repo) }
    val shoes = remember { ShoeStore(repo) }
    var data by remember { mutableStateOf(Today()) }
    var vitals by remember { mutableStateOf(Vitals()) }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    var onboarded by remember { mutableStateOf(repo.onboarded) }
    var tab by remember { mutableIntStateOf(startTab) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var walk by remember { mutableStateOf<WalkSlot?>(null) }
    var apiKey by remember { mutableStateOf(repo.apiKey) }
    val live by WorkoutTracker.live.collectAsState()
    var livePlanDay by remember { mutableStateOf<Day?>(null) }
    var openDone by remember { mutableStateOf<Done?>(null) }
    val bodyKg = vitals.weightKg ?: LabStore.get(ctx).weightKg ?: repo.bodyKg
    val todayWorkout = remember(s.age) { week(priorities(LabStore.get(ctx), s.age), phaseOf(LabStore.planStart(ctx))).first { it.dow == java.time.LocalDate.now().dayOfWeek }.takeIf { !it.rest } }

    var lastFull by remember { mutableLongStateOf(0L) }
    /** Full load (a year of history, heart samples, sleep) — on open, resume, after changes, and every 10 min at most. */
    fun refresh() = scope.launch {
        ok = runCatching { repo.granted() }.getOrDefault(false)
        if (ok == true) {
            runCatching { repo.load() }.onSuccess { data = it; saveSnapshot(ctx, Snapshot(it.steps, s.stepGoal, it.waterMl, s.waterGoal, it.meters / 1000)) }
            runCatching { repo.loadVitals() }.onSuccess { vitals = it }
            lastFull = System.currentTimeMillis()
        }
    }
    /** Cheap live update: only today's steps, distance and water. */
    fun refreshQuick() = scope.launch {
        runCatching { repo.loadQuick() }.onSuccess { q ->
            data = data.copy(steps = q.steps, meters = q.meters, waterMl = q.waterMl)
            saveSnapshot(ctx, Snapshot(q.steps, s.stepGoal, q.waterMl, s.waterGoal, q.meters / 1000))
        }
    }
    var askActivity by remember { mutableStateOf(false) }
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        refresh()
        // then, once, the Physical activity permission used for ride detection
        if (repo.excludeRides && !Rides.granted(ctx) && !repo.raw.getBoolean("askedActivity", false)) { repo.raw.edit().putBoolean("askedActivity", true).apply(); askActivity = true }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun findWalk() = scope.launch { lastLocation(ctx)?.let { (la, lo) -> walk = bestWalk(la, lo) } }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) findWalk() }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val okRestore = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.getOrNull()?.let { importBackup(ctx, it) } == true
        android.widget.Toast.makeText(ctx, if (okRestore) "Settings restored — reopen the app" else "That file isn't a ${APP_NAME} backup", android.widget.Toast.LENGTH_LONG).show()
    }
    fun connect() {
        if (repo.available) hcLauncher.launch(PERMISSIONS)
        else ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
    }
    fun locate() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) findWalk()
        else locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    fun setReminders(on: Boolean) {
        s.reminders = on; repo.reminders = on; scheduleReminders(ctx, on)
        if (on && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun open(k: String) = when (k) {
        "plan" -> tab = 2; "health" -> tab = 3
        "exportCsv" -> shareFile(ctx, "thrivo-data.csv", "text/csv", exportCsv(data, vitals))
        "backup" -> shareFile(ctx, "thrivo-backup.json", "application/json", exportBackup(ctx))
        "restore" -> restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        else -> overlay = k
    }
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) { Rides.register(ctx); refresh() } }
    LaunchedEffect(askActivity) { if (askActivity) { askActivity = false; activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) } }
    // speed rides: foreground location first, then Android asks for "Allow all the time" separately
    val bgLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { SpeedRides.register(ctx, repo.speedRides) }
    val fgLocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    var pendingGps by remember { mutableStateOf<Act?>(null) }
    fun beginWorkout(act: Act, program: Program?, day: Day? = null) {
        livePlanDay = day
        when (act.mode) {
            Mode.GPS -> runCatching { ctx.startForegroundService(Intent(ctx, WalkService::class.java).putExtra("saveSelf", false)) }
            else -> {}
        }
        WorkoutTracker.begin(ctx, act, kmh = if (act == Act.TREADMILL) 5.0 else 0.0, incline = if (act == Act.TREADMILL) 1.0 else 0.0,
            watts = if (act == Act.BIKE) 100 else 0, program = program)
        overlay = "live"
    }
    val gpsWorkoutPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val a = pendingGps; pendingGps = null; if (a != null && r[Manifest.permission.ACCESS_FINE_LOCATION] == true) beginWorkout(a, null) }
    fun startWorkout(act: Act, program: Program?, day: Day? = null) {
        if (act.mode == Mode.GPS && androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingGps = act; gpsWorkoutPerms.launch(buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray())
        } else beginWorkout(act, program, day)
    }
    fun endWorkout(save: Boolean) {
        val l = WorkoutTracker.live.value ?: return
        val done = finishWorkout(l, bodyKg)
        val route = WalkTracker.state.value.points
        if (l.act.mode == Mode.GPS) runCatching { ctx.startService(Intent(ctx, WalkService::class.java).setAction("stop")) }
        WorkoutTracker.discard(ctx)
        if (!save) { overlay = null; return }
        WorkoutTracker.saveDone(ctx, done); openDone = done; overlay = "workoutDetail"
        scope.launch { val ok2 = repo.saveWorkout(done, route); val d2 = done.copy(savedToHc = ok2); WorkoutTracker.saveDone(ctx, d2); openDone = d2; refresh() }
    }
    val walkPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true) startWalk(ctx) }
    LaunchedEffect(Unit) {
        Lang = s.lang; Metric = s.metric
        WorkoutTracker.restore(ctx); if (WorkoutTracker.live.value != null) overlay = "live"
        scheduleWidgetRefresh(ctx); scheduleAll(ctx); Rides.register(ctx); SpeedRides.register(ctx, repo.speedRides)
        refresh().join()
        if (BuildConfig.DEBUG) scope.launch { repo.diagnoseCalories() }
        // ask once for permissions added in newer versions; never nag on every launch
        val asked = repo.raw.getStringSet("askedPerms", emptySet())!!
        val missing = if (repo.onboarded && ok == true) runCatching { repo.missing() }.getOrDefault(emptySet()) else emptySet()
        if ((missing - asked).isNotEmpty()) { repo.raw.edit().putStringSet("askedPerms", asked + missing).apply(); hcLauncher.launch(PERMISSIONS) }
        // ride detection needs Physical activity permission — ask once
        else if (repo.onboarded && repo.excludeRides && !Rides.granted(ctx) && !repo.raw.getBoolean("askedActivity", false)) {
            repo.raw.edit().putBoolean("askedActivity", true).apply(); activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) findWalk()
    }
    LaunchedEffect(ok) {
        while (ok == true) { delay(30_000); if (System.currentTimeMillis() - lastFull > 10 * 60_000) refresh() else refreshQuick() }
    }
    // reload when the app comes back to the foreground
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && lastFull > 0) refresh() }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }
    // dark theme: 0 = off, 1 = on, 2 = follow the system
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    LaunchedEffect(s.themeMode, systemDark, s.bar) { applyTheme(s.themeMode == 1 || (s.themeMode == 2 && systemDark), s.bar) }
    BackHandler(overlay != null || tab != 0) { if (overlay != null) overlay = null else tab = 0 }

    Box(Modifier.fillMaxSize().background(Brand)) {
        when {
            !onboarded -> Onboarding(s, repo.available, ::connect) { onboarded = true }
            ok == null -> LoadingScreen()
            ok == false -> ConnectScreen(repo.available, ::connect)
            else -> {
                Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    when (tab) {
                        0 -> StepsScreen(data, s.stepGoal, s.metric, s.weekMon, onStats = { overlay = "reports" }) { n -> scope.launch { runCatching { repo.addSteps(n) }; refresh() } }
                        1 -> WaterScreen(data, s.waterGoal, s.glass, s.remaining, s.bottle, { s.glass = it; repo.glassMl = it },
                            { scope.launch { runCatching { repo.addWater(s.glass) }; refresh() } }, { overlay = "waterGoal" }, { overlay = "bottles" })
                        2 -> PlanTab(data, vitals, bodyKg, s.age, s.female, s.stepGoal, s.waterGoal, s.name, walk, todayWorkout, ::locate, { overlay = "stepGoal" },
                            { overlay = "streaks" }, { overlay = "plan5" }, { overlay = "workout" }, { overlay = "workoutHistory" }) { k -> if (k == "waterTab") tab = 1 else overlay = k }
                        3 -> HealthScreen(vitals, s.age, s.female, { overlay = "science" }, ::open, { k -> overlay = "metric:$k" }) { connect(); refresh() }
                        else -> SettingsScreen(s, data, true, ::open, ::connect)
                    }
                }
                TabBar(tab, Modifier.align(Alignment.BottomCenter)) { tab = it; overlay = null }
                // workout running in the background → floating bar to jump back
                live?.let { l -> if (overlay != "live") {
                    var t by remember { mutableStateOf(java.time.Instant.now()) }
                    LaunchedEffect(l.start) { while (true) { t = java.time.Instant.now(); kotlinx.coroutines.delay(1000) } }
                    Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 74.dp).clip(RoundedCornerShape(50)).background(Coral)
                        .clickable { overlay = "live" }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(l.act.icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        T("  ${l.program ?: l.act.label} · ${hms(WorkoutTracker.activeSec(l, t))} · Return", 13.sp, FontWeight.Bold)
                    }
                } }
            }
        }
        when (overlay) {
            "waterGoal" -> WaterGoalSheet(s.waterGoal, s.female, s.reminders, { s.waterGoal = it; repo.waterGoal = it }, ::setReminders) { overlay = null }
            "stepGoal" -> StepGoalSheet(s.stepGoal, s.age, { s.stepGoal = it; repo.stepGoal = it }) { overlay = null }
            "bottles" -> BottlesScreen(s.bottle, { s.bottle = it; repo.bottle = it }) { overlay = null }
            "more" -> MoreSettingsScreen(s, ok == true, ::connect, { overlay = it }) { overlay = null }
            "streaks" -> StreaksScreen(data, s.stepGoal) { overlay = null }
            "science" -> ScienceScreen { overlay = null }
            "fitness" -> FitnessScreen(vitals, s.age, s.female) { overlay = null }
            "stress" -> StressScreen(vitals, data, s.age, { overlay = "breathe" }) { overlay = null }
            "breathe" -> BreathingScreen { overlay = null }
            "workout" -> WorkoutPickerScreen(bodyKg, { repo.bodyKg = it }, { a, p -> startWorkout(a, p) }, { overlay = "workoutHistory" }, { overlay = "plan5" }) { overlay = null }
            "live" -> if (live != null) LiveWorkoutScreen(bodyKg, livePlanDay, { endWorkout(true) }, { endWorkout(false) }) { overlay = null } else overlay = null
            "workoutHistory" -> WorkoutHistoryScreen({ openDone = it; overlay = "workoutDetail" }) { overlay = "workout" }
            "workoutDetail" -> openDone?.let { d -> WorkoutDetailScreen(d, repo, s.age, vitals.resting, { WorkoutTracker.delete(ctx, d.id); overlay = "workoutHistory" }) { overlay = null } }
            "plan5" -> FitnessPlanScreen(s.age, vitals.resting, { p -> startWorkout(Act.TREADMILL, p) }, { d -> startWorkout(Act.STRENGTH, null, d) }, { overlay = "labs" }) { overlay = null }
            "labs" -> LabsScreen { overlay = null }
            "stepSource" -> StepSourceScreen(repo, data, { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                { fgLocLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, { refresh() }) { overlay = null }
            "caffeine" -> CaffeineScreen(coach(vitals, s.age, s.female).bedtime.plusMinutes(15)) { overlay = null }
            "body" -> BodyScreen(repo) { overlay = null }
            "meds" -> MedsScreen { overlay = null }
            "challenges" -> ChallengesScreen(s.name, data) { overlay = null }
            "walk" -> WalkScreen({ walkPerms.launch(buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray()) }) { overlay = null }
            "sleepProfile" -> SleepProfileScreen(vitals) { overlay = null }
            "cycle" -> CycleScreen(vitals, { d, n -> scope.launch { runCatching { repo.logPeriod(d, n) }; refresh() } }) { overlay = null }
            "journal" -> JournalScreen(journal, vitals) { overlay = null }
            "reports" -> ReportsScreen(data, vitals, s.stepGoal) { overlay = null }
            "badges" -> BadgesScreen(data, s.stepGoal) { overlay = null }
            "shoes" -> ShoesScreen(shoes, data) { overlay = null }
            "coach" -> CoachChatScreen(apiKey, { healthSummary(data, vitals, s.age, s.female, s.stepGoal) }, { overlay = "apiKey" }) { overlay = null }
            "apiKey" -> ApiKeyDialog(apiKey, { apiKey = it; repo.apiKey = it; overlay = "coach" }) { overlay = null }
                    else -> if (overlay?.startsWith("metric:") == true) {
                val spec = metricFor(overlay!!.removePrefix("metric:"), data, vitals, coach(vitals, s.age, s.female), s.stepGoal to s.waterGoal) { if (Metric) it else it * 2.20462 }
                if (spec != null) MetricDetailScreen(spec) { overlay = null } else overlay = null
            }
        }
    }
}

@Composable
fun ApiKeyDialog(current: String, save: (String) -> Unit, dismiss: () -> Unit) {
    var k by remember { mutableStateOf(current) }
    AlertDialog(dismiss, containerColor = Color.White,
        title = { T("Anthropic API key", 20.sp, FontWeight.Bold, Ink) },
        text = { Column {
            T("Get a key at console.anthropic.com. It's stored only on this phone and excluded from backups. Questions you ask, plus a summary " +
              "of your health numbers, are sent to Anthropic to answer them.", 12.sp, color = Muted, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(k, { k = it.trim() }, singleLine = true, placeholder = { Text("sk-ant-…") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        } },
        confirmButton = { TextButton({ save(k) }) { T("Save", 16.sp, FontWeight.Bold, Brand) } },
        dismissButton = { TextButton({ save(""); dismiss() }) { T("Remove", 16.sp, color = Ink) } })
}

@Composable
fun TabBar(selected: Int, modifier: Modifier, pick: (Int) -> Unit) {
    Row(modifier.navigationBarsPadding().padding(bottom = 14.dp).clip(RoundedCornerShape(50)).background(Color(0xFFB9B4FF).copy(.55f))
        .border(1.dp, Color.White.copy(.35f), RoundedCornerShape(50)).padding(4.dp)) {
        TABS.forEachIndexed { i, (name, icon) ->
            val on = i == selected
            Row(Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(.45f) else Color.Transparent).clickable { pick(i) }
                .padding(horizontal = 7.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, name, tint = if (on) Color.White else Ink, modifier = Modifier.size(15.dp))
                T(" $name", 10.sp, FontWeight.Medium, if (on) Color.White else Ink)
            }
        }
    }
}

@Composable
fun LoadingScreen() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(56.dp, Accent)
        Spacer(Modifier.height(16.dp))
        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        T("Loading your health data…", 13.sp, color = Color.White.copy(.75f), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ConnectScreen(available: Boolean, connect: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        HeroArt(Modifier.fillMaxWidth().height(240.dp))
        Spacer(Modifier.height(24.dp)); DottedLine(); Spacer(Modifier.height(28.dp))
        T("every step", 52.sp, FontWeight.Bold, Accent, lineHeight = 54.sp)
        T("counts", 52.sp, FontWeight.Bold, lineHeight = 54.sp)
        Spacer(Modifier.height(12.dp))
        T(if (available) "Connect your Fitbit through Health Connect to sync steps, water, sleep and heart data."
          else "${APP_NAME} needs Health Connect to read your Fitbit data. Install it from Google Play.", 15.sp, FontWeight.Medium, lineHeight = 20.sp)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = connect), contentAlignment = Alignment.Center) {
            T(if (available) "Connect Fitbit" else "Get Health Connect", 16.sp, FontWeight.Bold, Brand)
        }
    }
}
