package app.thrivo

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import java.time.*

val PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getWritePermission(StepsRecord::class),
    HealthPermission.getReadPermission(DistanceRecord::class),
    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(FloorsClimbedRecord::class),
    HealthPermission.getReadPermission(HydrationRecord::class),
    HealthPermission.getWritePermission(HydrationRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    HealthPermission.getReadPermission(RespiratoryRateRecord::class),
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    HealthPermission.getReadPermission(Vo2MaxRecord::class),
    HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
    HealthPermission.getWritePermission(MenstruationPeriodRecord::class),
    HealthPermission.getReadPermission(MenstruationFlowRecord::class),
    HealthPermission.getWritePermission(WeightRecord::class),
    HealthPermission.getReadPermission(HeightRecord::class),
    HealthPermission.getReadPermission(BloodPressureRecord::class),
    HealthPermission.getReadPermission(BloodGlucoseRecord::class),
    HealthPermission.getReadPermission(BodyTemperatureRecord::class),
    HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    HealthPermission.getWritePermission(DistanceRecord::class),
    HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
    HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
    HealthPermission.getWritePermission(ElevationGainedRecord::class),
    HealthPermission.getWritePermission(SpeedRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(BasalMetabolicRateRecord::class),
    "android.permission.health.READ_HEALTH_DATA_HISTORY",
    "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
)

/** Activity for today plus 60 days of daily step history (oldest first). */
data class Today(
    val steps: Long = 0, val meters: Double = 0.0, val kcal: Double = 0.0, val floors: Double = 0.0, val waterMl: Long = 0,
    val history: List<Long> = List(60) { 0 }, val hourly: List<Long> = List(24) { 0 },
    /** 365 days of daily steps / metres, oldest first (badges, shoes, reports) */
    val yearSteps: List<Long> = List(365) { 0 }, val yearMeters: List<Double> = List(365) { 0.0 },
    /** 30 days of water (ml), oldest first */
    val water30: List<Long> = List(30) { 0 },
    /** today's steps per 30-min slot (48) — used to tell rest from movement for stress */
    val steps30m: List<Long> = List(48) { 0 },
    /** steps removed today because they were logged during a car/bus/bike ride, and the ride minutes */
    val rideSteps: Long = 0, val rideMinutes: Long = 0,
    /** total calories burned today (resting + active) as reported by the tracker, and the last 7 days, oldest first */
    val totalKcal: Double? = null, val burned7: List<Double?> = List(7) { null },
    /** resting calories so far, BMR (kcal/day) measured from overnight blocks, calories burned walking (steps only) */
    val restingKcal: Double? = null, val bmrDay: Double? = null, val stepKcal: Double = 0.0,
)

data class Stage(val type: Int, val start: Instant, val end: Instant)
data class Night(val date: LocalDate, val asleep: Duration, val deep: Duration, val rem: Duration, val light: Duration,
                 val awake: Duration, val start: Instant, val end: Instant, val stages: List<Stage>) {
    val inBed: Duration get() = Duration.between(start, end)
    val efficiency: Double get() = asleep.seconds * 100.0 / inBed.seconds.coerceAtLeast(1)
    private fun isAwake(t: Int) = t == SleepSessionRecord.STAGE_TYPE_AWAKE || t == SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED || t == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
    /** time from going to bed until the first sleep stage */
    val latency: Duration get() = stages.firstOrNull { !isAwake(it.type) }?.let { Duration.between(start, it.start).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    /** wake after sleep onset: awake time between first and last sleep stage */
    val waso: Duration get() {
        val first = stages.indexOfFirst { !isAwake(it.type) }; val last = stages.indexOfLast { !isAwake(it.type) }
        if (first < 0) return Duration.ZERO
        return Duration.ofSeconds(stages.subList(first, last + 1).filter { isAwake(it.type) }.sumOf { Duration.between(it.start, it.end).seconds })
    }
    val stagesKnown: Boolean get() = deep + rem > Duration.ZERO
}
data class Workout(val name: String, val start: Instant, val minutes: Long)
data class Vitals(
    val nights: List<Night> = emptyList(), val hrHourly: List<Long?> = List(24) { null },
    val bpmAvg: Long? = null, val bpmMin: Long? = null, val bpmMax: Long? = null,
    val resting: Long? = null, val hrv: Double? = null, val spo2: Double? = null, val resp: Double? = null,
    val weightKg: Double? = null, val workouts: List<Workout> = emptyList(),
    /** raw heart-rate samples (time, bpm) for the last 7 days */
    val hrSamples: List<Pair<Instant, Long>> = emptyList(),
    /** daily HRV (ms) and resting HR for the last 30 days, oldest first, null = no data */
    val hrvDaily: List<Double?> = emptyList(), val rhrDaily: List<Long?> = emptyList(),
    /** average HR per 30-min slot for the last 28 days (1344 slots, oldest first) */
    val hr30m: List<Long?> = emptyList(),
    val vo2Device: Double? = null,
    /** period start/end dates, oldest first */
    val periods: List<Pair<LocalDate, LocalDate>> = emptyList(),
    /** 30 days, oldest first */
    val spo2Daily: List<Double?> = List(30) { null }, val respDaily: List<Double?> = List(30) { null }, val weightDaily: List<Double?> = List(30) { null },
)

/** The part of value [v] (recorded evenly over [s, e)) that falls inside window [a, b), with the overlap's midpoint. */
fun proRate(s: Instant, e: Instant, a: Instant, b: Instant, v: Double): Pair<Instant, Double>? {
    val os = maxOf(s, a); val oe = minOf(e, b); if (!oe.isAfter(os)) return null
    val frac = Duration.between(os, oe).toMillis().toDouble() / Duration.between(s, e).toMillis().coerceAtLeast(1)
    return os.plusMillis(Duration.between(os, oe).toMillis() / 2) to v * frac
}

data class Body(val weights: List<Pair<LocalDate, Double>> = emptyList(), val heightM: Double? = null,
                val bp: List<Triple<LocalDate, Double, Double>> = emptyList(), val glucose: Double? = null, val tempC: Double? = null)

private suspend fun <T> safe(block: suspend () -> T): T? = runCatching { block() }.getOrNull()

const val FITBIT = "com.fitbit.FitbitMobile"

class Repo(private val ctx: Context) {
    val available get() = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
    private val hc by lazy { HealthConnectClient.getOrCreate(ctx) }
    private val prefs = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)
    val raw get() = prefs

    var stepGoal: Int get() = prefs.getInt("stepGoal", 10000); set(v) = prefs.edit().putInt("stepGoal", v).apply()
    var waterGoal: Int get() = prefs.getInt("waterGoal", 2500); set(v) = prefs.edit().putInt("waterGoal", v).apply()
    var glassMl: Int get() = prefs.getInt("glassMl", 100); set(v) = prefs.edit().putInt("glassMl", v).apply()
    var name: String get() = prefs.getString("name", "Anudeep")!!; set(v) = prefs.edit().putString("name", v).apply()
    var waterRemaining: Boolean get() = prefs.getBoolean("waterRemaining", false); set(v) = prefs.edit().putBoolean("waterRemaining", v).apply()
    var reminders: Boolean get() = prefs.getBoolean("reminders", false); set(v) = prefs.edit().putBoolean("reminders", v).apply()
    var metric: Boolean get() = prefs.getBoolean("metric", true); set(v) = prefs.edit().putBoolean("metric", v).apply()
    var weekStartsMonday: Boolean get() = prefs.getBoolean("weekMon", false); set(v) = prefs.edit().putBoolean("weekMon", v).apply()
    var female: Boolean get() = prefs.getBoolean("female", false); set(v) = prefs.edit().putBoolean("female", v).apply()
    var age: Int get() = prefs.getInt("age", 30); set(v) = prefs.edit().putInt("age", v).apply()
    var bottle: Int get() = prefs.getInt("bottle", 0); set(v) = prefs.edit().putInt("bottle", v).apply()
    val since: LocalDate get() = Instant.ofEpochMilli(ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime)
        .atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Which app's data to count — for every data type (steps, sleep, heart, body…). Several apps write the same things
     * (e.g. Samsung Health and Fitbit), which would otherwise double-count, so by default we use only Fitbit plus our own entries.
     * Empty = every source, using Health Connect's own priority order.
     */
    var stepSource: String
        // first run: lock in Fitbit when it's installed (needs the <queries> entry to be visible on Android 11+)
        get() = prefs.getString("stepSource", null) ?: (if (installed(FITBIT)) FITBIT else "").also { if (it.isNotEmpty()) prefs.edit().putString("stepSource", it).apply() }
        set(v) = prefs.edit().putString("stepSource", v).apply()
    private fun installed(pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    fun activityOrigins(): Set<DataOrigin> = stepSource.takeIf { it.isNotEmpty() }?.let { setOf(DataOrigin(it), DataOrigin(ctx.packageName)) } ?: emptySet()

    /** Today's raw step totals per writing app (sources can overlap, so these don't add up to the real total). */
    suspend fun stepsBySource(): List<Pair<String, Long>> {
        val range = TimeRangeFilter.between(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now())
        val out = mutableMapOf<String, Long>(); var token: String? = null
        do {
            val r = hc.readRecords(ReadRecordsRequest(StepsRecord::class, range, pageToken = token))
            r.records.forEach { out.merge(it.metadata.dataOrigin.packageName, it.count, Long::plus) }
            token = r.pageToken
        } while (token != null)
        return out.toList().sortedByDescending { it.second }
    }

    /** Don't count steps logged while riding (car, bus, bike) — see Rides.kt. */
    /** Also detect rides by speed (≥ 13 km/h) using low-power background location. */
    var speedRides: Boolean get() = prefs.getBoolean("speedRides", false); set(v) = prefs.edit().putBoolean("speedRides", v).apply()
    var excludeRides: Boolean get() = prefs.getBoolean("excludeRides", true); set(v) = prefs.edit().putBoolean("excludeRides", v).apply()

    /** Ride windows: phone-detected vehicle/bike rides plus bike workouts recorded by the tracker. */
    suspend fun rideIntervals(from: Instant, to: Instant): List<Pair<Instant, Instant>> {
        val phone = Rides.list(ctx).map { it.start to (it.end ?: Instant.now()) }
        val bikes = safe { all(ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))
            .filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BIKING || it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY }
            .map { it.startTime to it.endTime } } ?: emptyList()
        return mergeIntervals((phone + bikes).filter { it.second.isAfter(from) && it.first.isBefore(to) }
            .map { maxOf(it.first, from) to minOf(it.second, to) })
    }

    /** Steps and metres recorded inside ride windows, as (time, steps, metres) pieces, pro-rated by overlap. */
    private suspend fun ridePieces(windows: List<Pair<Instant, Instant>>): List<Triple<Instant, Double, Double>> = windows.flatMap { (a, b) ->
        fun <T> share(s: Instant, e: Instant, v: Double) = proRate(s, e, a, b, v)
        val st = safe { all(StepsRecord::class, TimeRangeFilter.between(a, b))
            .filter { it.metadata.dataOrigin.packageName != ctx.packageName }        // steps you added by hand always count
            .mapNotNull { share<Unit>(it.startTime, it.endTime, it.count.toDouble())?.let { (t, v) -> Triple(t, v, 0.0) } } } ?: emptyList()
        val di = safe { all(DistanceRecord::class, TimeRangeFilter.between(a, b))
            .filter { it.metadata.dataOrigin.packageName != ctx.packageName }
            .mapNotNull { share<Unit>(it.startTime, it.endTime, it.distance.inMeters)?.let { (t, v) -> Triple(t, 0.0, v) } } } ?: emptyList()
        st + di
    }

    /** Subtract ride steps/distance from today's totals, the hourly and 30-min charts, and the daily history. */
    private suspend fun withoutRides(t: Today, from: Instant, to: Instant): Today {
        val windows = rideIntervals(from, to); if (windows.isEmpty()) return t
        val pieces = ridePieces(windows); val zone = ZoneId.systemDefault(); val today = LocalDate.now()
        val byDay = pieces.groupBy { it.first.atZone(zone).toLocalDate() }
        fun stepsOn(d: LocalDate) = byDay[d]?.sumOf { it.second }?.toLong() ?: 0L
        fun metersOn(d: LocalDate) = byDay[d]?.sumOf { it.third } ?: 0.0
        val todayPieces = byDay[today].orEmpty()
        val hourly = t.hourly.mapIndexed { h, v -> (v - todayPieces.filter { it.first.atZone(zone).hour == h }.sumOf { it.second }.toLong()).coerceAtLeast(0) }
        val s30 = t.steps30m.mapIndexed { i, v -> (v - todayPieces.filter { val z = it.first.atZone(zone); z.hour * 2 + z.minute / 30 == i }.sumOf { it.second }.toLong()).coerceAtLeast(0) }
        val hist = t.history.mapIndexed { i, v -> (v - stepsOn(today.minusDays(59L - i))).coerceAtLeast(0) }
        val ySteps = t.yearSteps.mapIndexed { i, v -> (v - stepsOn(today.minusDays(364L - i))).coerceAtLeast(0) }
        val yMeters = t.yearMeters.mapIndexed { i, v -> (v - metersOn(today.minusDays(364L - i))).coerceAtLeast(0.0) }
        val removed = stepsOn(today)
        val rideMin = windows.filter { it.second.atZone(zone).toLocalDate() == today }.sumOf { Duration.between(maxOf(it.first, today.atStartOfDay(zone).toInstant()), it.second).toMinutes() }
        return t.copy(steps = (t.steps - removed).coerceAtLeast(0), meters = (t.meters - metersOn(today)).coerceAtLeast(0.0), hourly = hourly, steps30m = s30,
            history = hist, yearSteps = ySteps, yearMeters = yMeters, rideSteps = removed, rideMinutes = rideMin)
    }

    /** Debug: which apps write calorie, BMR and HRV records (logged to logcat under "Thrivo"). */
    suspend fun diagnoseCalories() {
        val zone = ZoneId.systemDefault(); val start = LocalDate.now().atStartOfDay(zone).toInstant(); val now = Instant.now()
        val r = TimeRangeFilter.between(start, now)
        safe { hc.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, r)).records.groupBy { it.metadata.dataOrigin.packageName }
            .forEach { (k, v) -> android.util.Log.i("Thrivo", "TOTAL $k n=${v.size} sum=${v.sumOf { it.energy.inKilocalories }} first=${v.first().startTime}-${v.first().endTime}")
                android.util.Log.i("Thrivo", "TOTALS $k " + v.sortedBy { it.startTime }.joinToString(",") { "%.1f".format(it.energy.inKilocalories) }) } }
        safe { hc.readRecords(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, r)).records.groupBy { it.metadata.dataOrigin.packageName }
            .forEach { (k, v) -> android.util.Log.i("Thrivo", "ACTIVE $k n=${v.size} sum=${v.sumOf { it.energy.inKilocalories }}") } }
        safe { hc.readRecords(ReadRecordsRequest(BasalMetabolicRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(60)), now))).records.groupBy { it.metadata.dataOrigin.packageName }
            .forEach { (k, v) -> android.util.Log.i("Thrivo", "BMR $k n=${v.size} last=${v.last().basalMetabolicRate.inKilocaloriesPerDay} at ${v.last().time}") } }
            ?: android.util.Log.i("Thrivo", "BMR read failed (permission?)")
        safe { hc.readRecords(ReadRecordsRequest(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(4)), now))).records
            .groupBy { it.time.atZone(zone).toLocalDate() }.forEach { (d, l) -> android.util.Log.i("Thrivo", "HRVDAY $d n=${l.size} avg=${l.map { it.heartRateVariabilityMillis }.average()}") } }
        safe { all(StepsRecord::class, r).sortedBy { it.startTime }
            .let { l -> android.util.Log.i("Thrivo", "STEPS15 " + (0 until 96).joinToString(",") { i -> val a = start.plusSeconds(i * 900L); val b = a.plusSeconds(900)
                l.filter { it.startTime >= a && it.startTime < b }.sumOf { it.count }.toString() }) } }
    }

    /**
     * Active vs resting like Fitbit: BMR rate = the steady overnight 15-min blocks (lowest quartile); active = calories above BMR
     * in 15-min blocks at ≥ 4 × BMR (checked against Fitbit's own split: 517 vs 516 kcal); resting = the rest of the total.
     * Returns (active, resting, BMR kcal/day) or null when the tracker writes no total calories.
     */
    fun calorieSplit(recs: List<TotalCaloriesBurnedRecord>): Triple<Double, Double, Double>? {
        if (recs.isEmpty()) return null
        val rates = recs.mapNotNull { r -> val sec = Duration.between(r.startTime, r.endTime).seconds; if (sec < 300) null else r.energy.inKilocalories * 900.0 / sec }
        val bmr15 = rates.sorted().let { l -> if (l.size >= 8) l.take(maxOf(4, l.size / 4)).average() else null }
            ?: bmr(bodyKg, LabStore.get(ctx).heightCm ?: 170.0, age, female) / 96
        var active = 0.0
        recs.forEach { r -> val sec = Duration.between(r.startTime, r.endTime).seconds.coerceAtLeast(1); val kc = r.energy.inKilocalories
            val base = bmr15 * sec / 900.0; if (kc >= 4 * base) active += kc - base }
        val total = recs.sumOf { it.energy.inKilocalories }
        return Triple(active, total - active, bmr15 * 96)
    }

    /** Calories burned walking, from steps only: ACSM walking equation over the walking time (≈100 steps/min), gross. */
    fun walkingKcal(steps: Long, meters: Double): Double {
        if (steps <= 0) return 0.0
        val m = if (meters > 0) meters else steps * 0.415 * (LabStore.get(ctx).heightCm ?: 170.0) / 100
        val min = steps / 100.0; val speed = m / min                                   // m/min
        return (0.1 * speed + 3.5) * bodyKg * min / 1000 * 5
    }

    /** Read every page of a record type (Health Connect returns at most 1,000 records per request). */
    suspend fun <T : androidx.health.connect.client.records.Record> all(type: kotlin.reflect.KClass<T>, range: TimeRangeFilter, origins: Set<DataOrigin> = activityOrigins()): List<T> {
        val out = mutableListOf<T>(); var token: String? = null
        do {
            val r = hc.readRecords(ReadRecordsRequest(type, range, dataOriginFilter = origins, pageToken = token))
            out += r.records; token = r.pageToken
        } while (token != null && out.size < 100_000)
        return out
    }

    suspend fun missing() = PERMISSIONS - hc.permissionController.getGrantedPermissions()

    suspend fun granted() = available &&
        hc.permissionController.getGrantedPermissions().contains(HealthPermission.getReadPermission(StepsRecord::class))

    suspend fun load(): Today {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val range = TimeRangeFilter.between(start, now)
        // each metric separately so one missing permission doesn't blank the rest
        val steps = safe { hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range, dataOriginFilter = activityOrigins()))[StepsRecord.COUNT_TOTAL] } ?: 0L
        val meters = safe { hc.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range, dataOriginFilter = activityOrigins()))[DistanceRecord.DISTANCE_TOTAL]?.inMeters } ?: 0.0
        val kcal = safe { hc.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), range, dataOriginFilter = activityOrigins()))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories } ?: 0.0
        val floors = safe { hc.aggregate(AggregateRequest(setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL), range, dataOriginFilter = activityOrigins()))[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] } ?: 0.0
        val water = safe { hc.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), range, dataOriginFilter = activityOrigins()))[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.toLong() } ?: 0L
        val days = hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL),
            TimeRangeFilter.between(LocalDate.now().minusDays(59).atStartOfDay(), LocalDateTime.now()), Period.ofDays(1), dataOriginFilter = activityOrigins()))
        val map = days.associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }
        val history = (0..59).map { map[LocalDate.now().minusDays(59L - it)] ?: 0L }
        val hrs = hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(
            setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, now), Duration.ofHours(1), dataOriginFilter = activityOrigins()))
        val hourly = List(24) { h -> hrs.firstOrNull { it.startTime.atZone(zone).hour == h }?.result?.get(StepsRecord.COUNT_TOTAL) ?: 0L }
        val yStart = LocalDate.now().minusDays(364).atStartOfDay()
        val year = safe { hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL, DistanceRecord.DISTANCE_TOTAL),
            TimeRangeFilter.between(yStart, LocalDateTime.now()), Period.ofDays(1), dataOriginFilter = activityOrigins())) } ?: emptyList()
        val yMap = year.associate { it.startTime.toLocalDate() to it.result }
        val yearSteps = (0..364).map { yMap[LocalDate.now().minusDays(364L - it)]?.get(StepsRecord.COUNT_TOTAL) ?: 0L }
        val yearMeters = (0..364).map { yMap[LocalDate.now().minusDays(364L - it)]?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters ?: 0.0 }
        val water30 = safe {
            val w = hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(HydrationRecord.VOLUME_TOTAL),
                TimeRangeFilter.between(LocalDate.now().minusDays(29).atStartOfDay(), LocalDateTime.now()), Period.ofDays(1), dataOriginFilter = activityOrigins()))
                .associate { it.startTime.toLocalDate() to (it.result[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.toLong() ?: 0L) }
            (0..29).map { w[LocalDate.now().minusDays(29L - it)] ?: 0L }
        } ?: List(30) { 0L }
        val s30 = safe { hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(StepsRecord.COUNT_TOTAL), range, Duration.ofMinutes(30), dataOriginFilter = activityOrigins())) } ?: emptyList()
        val steps30m = List(48) { i -> s30.firstOrNull { val t = it.startTime.atZone(zone); t.hour * 2 + t.minute / 30 == i }?.result?.get(StepsRecord.COUNT_TOTAL) ?: 0L }
        val total = safe { hc.aggregate(AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), range, dataOriginFilter = activityOrigins()))[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories }
        val burned7 = safe {
            val g = hc.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                TimeRangeFilter.between(LocalDate.now().minusDays(6).atStartOfDay(), LocalDateTime.now()), Period.ofDays(1), dataOriginFilter = activityOrigins()))
                .associate { it.startTime.toLocalDate() to it.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories }
            (0..6).map { g[LocalDate.now().minusDays(6L - it)] }
        } ?: List(7) { null }
        val split = safe { calorieSplit(all(TotalCaloriesBurnedRecord::class, range)) }
        val active = if (kcal > 0 || split == null) kcal else split.first
        val raw = Today(steps, meters, active, floors, water, history, hourly, yearSteps, yearMeters, water30, steps30m, totalKcal = total, burned7 = burned7,
            restingKcal = split?.second, bmrDay = split?.third, stepKcal = walkingKcal(steps, meters))
        return if (excludeRides) withoutRides(raw, LocalDate.now().minusDays(364).atStartOfDay(zone).toInstant(), now) else raw
    }

    /** Just steps / distance / water for today — used by the widget refresh. */
    suspend fun loadQuick(): Today {
        val range = TimeRangeFilter.between(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now())
        val steps = safe { hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range, dataOriginFilter = activityOrigins()))[StepsRecord.COUNT_TOTAL] } ?: 0L
        val meters = safe { hc.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), range, dataOriginFilter = activityOrigins()))[DistanceRecord.DISTANCE_TOTAL]?.inMeters } ?: 0.0
        val water = safe { hc.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), range, dataOriginFilter = activityOrigins()))[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.toLong() } ?: 0L
        val raw = Today(steps = steps, meters = meters, waterMl = water)
        return if (excludeRides) withoutRides(raw, LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now()) else raw
    }

    suspend fun loadVitals(): Vitals {
        val zone = ZoneId.systemDefault(); val now = Instant.now()
        val dayStart = LocalDate.now().atStartOfDay(zone).toInstant()
        val week = now.minus(Duration.ofDays(8)); val recent = now.minus(Duration.ofDays(2))
        val nights = safe {
            all(SleepSessionRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(31)), now))
                .groupBy { it.endTime.atZone(zone).toLocalDate() }.map { (d, list) ->
                    val stages = list.flatMap { s -> s.stages.map { Stage(it.stage, it.startTime, it.endTime) } }.sortedBy { it.start }
                    fun sum(vararg t: Int) = Duration.ofSeconds(stages.filter { it.type in t }.sumOf { Duration.between(it.start, it.end).seconds })
                    val total = Duration.ofSeconds(list.sumOf { Duration.between(it.startTime, it.endTime).seconds })
                    val awake = sum(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
                    Night(d, total - awake, sum(SleepSessionRecord.STAGE_TYPE_DEEP), sum(SleepSessionRecord.STAGE_TYPE_REM),
                        sum(SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING), awake,
                        list.minOf { it.startTime }, list.maxOf { it.endTime }, stages)
                }.sortedBy { it.date }
        } ?: emptyList()
        val hr = safe { hc.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
            TimeRangeFilter.between(dayStart, now), dataOriginFilter = activityOrigins())) }
        val hrHourly = safe {
            val g = hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(HeartRateRecord.BPM_AVG),
                TimeRangeFilter.between(dayStart, now), Duration.ofHours(1), dataOriginFilter = activityOrigins()))
            List(24) { h -> g.firstOrNull { it.startTime.atZone(zone).hour == h }?.result?.get(HeartRateRecord.BPM_AVG) }
        } ?: List(24) { null }
        val resting = safe { hc.aggregate(AggregateRequest(setOf(RestingHeartRateRecord.BPM_AVG), TimeRangeFilter.between(recent, now), dataOriginFilter = activityOrigins()))[RestingHeartRateRecord.BPM_AVG] }
        val hrv = safe { all(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(recent, now)).lastOrNull()?.heartRateVariabilityMillis }
        val spo2 = safe { all(OxygenSaturationRecord::class, TimeRangeFilter.between(recent, now)).lastOrNull()?.percentage?.value }
        val resp = safe { all(RespiratoryRateRecord::class, TimeRangeFilter.between(recent, now)).lastOrNull()?.rate }
        val weight = safe { all(WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now)).lastOrNull()?.weight?.inKilograms }
        val workouts = safe {
            all(ExerciseSessionRecord::class, TimeRangeFilter.between(week, now))
                .sortedByDescending { it.startTime }.take(8)
                .map { Workout(it.title ?: exerciseName(it.exerciseType), it.startTime, Duration.between(it.startTime, it.endTime).toMinutes()) }
        } ?: emptyList()
        val hrSamples = safe {
            val out = mutableListOf<Pair<Instant, Long>>(); var token: String? = null
            do {
                val res = hc.readRecords(ReadRecordsRequest(HeartRateRecord::class,
                    TimeRangeFilter.between(LocalDate.now().minusDays(6).atStartOfDay(zone).toInstant(), now), pageToken = token, dataOriginFilter = activityOrigins()))
                res.records.forEach { r -> r.samples.forEach { out += it.time to it.beatsPerMinute } }
                token = res.pageToken
            } while (token != null && out.size < 200_000)
            out.sortedBy { it.first }
        } ?: emptyList()
        val month = LocalDate.now().minusDays(29)
        val hrvDaily = safe {
            val by = all(HeartRateVariabilityRmssdRecord::class, TimeRangeFilter.between(month.atStartOfDay(zone).toInstant(), now))
                .groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { e -> e.value.map { it.heartRateVariabilityMillis }.average() }
            (0..29).map { by[month.plusDays(it.toLong())] }
        } ?: List(30) { null }
        val rhrDaily = safe {
            val by = all(RestingHeartRateRecord::class, TimeRangeFilter.between(month.atStartOfDay(zone).toInstant(), now))
                .groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { e -> e.value.map { it.beatsPerMinute }.average().toLong() }
            (0..29).map { by[month.plusDays(it.toLong())] }
        } ?: List(30) { null }
        val m30 = TimeRangeFilter.between(month.atStartOfDay(zone).toInstant(), now)
        val spo2Daily = safe { val by = all(OxygenSaturationRecord::class, m30).groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { e -> e.value.map { it.percentage.value }.average() }
            (0..29).map { by[month.plusDays(it.toLong())] } } ?: List(30) { null }
        val respDaily = safe { val by = all(RespiratoryRateRecord::class, m30).groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { e -> e.value.map { it.rate }.average() }
            (0..29).map { by[month.plusDays(it.toLong())] } } ?: List(30) { null }
        val weightDaily = safe { val by = all(WeightRecord::class, m30).groupBy { it.time.atZone(zone).toLocalDate() }.mapValues { e -> e.value.last().weight.inKilograms }
            (0..29).map { by[month.plusDays(it.toLong())] } } ?: List(30) { null }
        val start28 = LocalDate.now().minusDays(27).atStartOfDay(zone)
        val hr30m = safe {
            val g = hc.aggregateGroupByDuration(AggregateGroupByDurationRequest(setOf(HeartRateRecord.BPM_AVG),
                TimeRangeFilter.between(start28.toInstant(), now), Duration.ofMinutes(30), dataOriginFilter = activityOrigins()))
                .associate { Duration.between(start28.toInstant(), it.startTime).toMinutes().toInt() / 30 to it.result[HeartRateRecord.BPM_AVG] }
            List(28 * 48) { g[it] }
        } ?: emptyList()
        val vo2 = safe { all(Vo2MaxRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(90)), now)).lastOrNull()?.vo2MillilitersPerMinuteKilogram }
        val periods = safe {
            all(MenstruationPeriodRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(400)), now))
                .map { it.startTime.atZone(zone).toLocalDate() to it.endTime.atZone(zone).toLocalDate() }.sortedBy { it.first }
        } ?: emptyList()
        return Vitals(nights, hrHourly, hr?.get(HeartRateRecord.BPM_AVG), hr?.get(HeartRateRecord.BPM_MIN), hr?.get(HeartRateRecord.BPM_MAX),
            resting, hrv, spo2, resp, weight, workouts, hrSamples, hrvDaily, rhrDaily, hr30m, vo2, periods, spo2Daily, respDaily, weightDaily)
    }

    suspend fun addWater(ml: Int) {
        val now = Instant.now(); val off = ZoneId.systemDefault().rules.getOffset(now)
        hc.insertRecords(listOf(HydrationRecord(now.minusSeconds(1), off, now, off, Volume.milliliters(ml.toDouble()))))
    }

    suspend fun loadBody(): Body {
        val now = Instant.now(); val zone = ZoneId.systemDefault()
        val weights = safe { all(WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(180)), now))
            .map { it.time.atZone(zone).toLocalDate() to it.weight.inKilograms } } ?: emptyList()
        val height = safe { all(HeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(3650)), now)).lastOrNull()?.height?.inMeters }
        val bp = safe { all(BloodPressureRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now))
            .map { Triple(it.time.atZone(zone).toLocalDate(), it.systolic.inMillimetersOfMercury, it.diastolic.inMillimetersOfMercury) } } ?: emptyList()
        val glucose = safe { all(BloodGlucoseRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now))
            .lastOrNull()?.level?.inMilligramsPerDeciliter }
        val temp = safe { all(BodyTemperatureRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now))
            .lastOrNull()?.temperature?.inCelsius }
        return Body(weights, height, bp, glucose, temp)
    }

    suspend fun logWeight(kg: Double) {
        val now = Instant.now()
        hc.insertRecords(listOf(WeightRecord(now, ZoneId.systemDefault().rules.getOffset(now), androidx.health.connect.client.units.Mass.kilograms(kg))))
    }

    /** Body weight for calorie maths: latest Health Connect weight, else the value set in the workout screen. */
    var bodyKg: Double get() = if (prefs.contains("bodyKg")) prefs.getFloat("bodyKg", 70f).toDouble() else (LabStore.get(ctx).weightKg ?: 70.0)
        set(v) = prefs.edit().putFloat("bodyKg", v.toFloat()).apply()

    /** Save a finished workout: session (+ GPS route), distance, speed samples, elevation and active calories. */
    suspend fun saveWorkout(d: Done, route: List<android.location.Location>): Boolean = runCatching {
        val z = ZoneId.systemDefault().rules; val so = z.getOffset(d.start); val eo = z.getOffset(d.end)
        val notes = buildString {
            if (d.act.mode == Mode.TREADMILL) {
                val avgInc = d.segs.sumOf { (it.incline * Duration.between(it.start, it.end ?: d.end).seconds) } / d.segs.sumOf { Duration.between(it.start, it.end ?: d.end).seconds }.coerceAtLeast(1)
                append("Avg incline %.1f%%, max %.1f%%. ".format(avgInc, d.segs.maxOf { it.incline }))
            }
            if (d.sets.isNotEmpty()) append(d.sets.groupBy { it.exercise }.entries.joinToString("; ") { (x, l) -> "$x ${l.size}×" + l.joinToString("/") { "${it.reps}@${"%.1f".format(it.kg)}kg" } })
            d.program?.let { append(" Program: $it.") }
        }.trim().ifEmpty { null }
        val pts = route.filter { !Instant.ofEpochMilli(it.time).isBefore(d.start) && !Instant.ofEpochMilli(it.time).isAfter(d.end) }
        val recs = mutableListOf<androidx.health.connect.client.records.Record>(
            ExerciseSessionRecord(startTime = d.start, startZoneOffset = so, endTime = d.end, endZoneOffset = eo, exerciseType = d.act.hcType,
                title = d.program ?: d.act.label, notes = notes,
                exerciseRoute = if (pts.size >= 2) ExerciseRoute(pts.map { ExerciseRoute.Location(time = Instant.ofEpochMilli(it.time), latitude = it.latitude, longitude = it.longitude) }) else null))
        if (d.meters > 1) recs += DistanceRecord(d.start, so, d.end, eo, androidx.health.connect.client.units.Length.meters(d.meters))
        if (d.kcal > 0.5) recs += ActiveCaloriesBurnedRecord(d.start, so, d.end, eo, androidx.health.connect.client.units.Energy.kilocalories(d.kcal))
        if (d.elevM > 0.5) recs += ElevationGainedRecord(d.start, so, d.end, eo, androidx.health.connect.client.units.Length.meters(d.elevM))
        val samples = when (d.act.mode) {
            Mode.TREADMILL -> d.segs.flatMap { sg -> val e = minOf(sg.end ?: d.end, d.end); generateSequence(sg.start) { it.plusSeconds(60) }.takeWhile { it.isBefore(e) }
                .map { SpeedRecord.Sample(it, androidx.health.connect.client.units.Velocity.kilometersPerHour(sg.kmh)) }.toList() }
            Mode.GPS -> pts.filter { it.hasSpeed() }.map { SpeedRecord.Sample(Instant.ofEpochMilli(it.time), androidx.health.connect.client.units.Velocity.metersPerSecond(it.speed.toDouble())) }
            else -> emptyList()
        }.filter { !it.time.isBefore(d.start) && it.time.isBefore(d.end) }
        if (samples.isNotEmpty()) recs += SpeedRecord(d.start, so, d.end, eo, samples)
        hc.insertRecords(recs); true
    }.onFailure { android.util.Log.w("Thrivo", "saveWorkout", it) }.getOrDefault(false)

    /** Heart-rate samples recorded by the tracker during a time window (arrive after it syncs). */
    suspend fun heartRateBetween(a: Instant, b: Instant): List<Pair<Instant, Long>> = safe {
        all(HeartRateRecord::class, TimeRangeFilter.between(a, b))
            .flatMap { r -> r.samples.map { it.time to it.beatsPerMinute } }.filter { !it.first.isBefore(a) && !it.first.isAfter(b) }.sortedBy { it.first }
    } ?: emptyList()

    suspend fun logPeriod(start: LocalDate, days: Int) {
        val z = ZoneId.systemDefault(); val s0 = start.atStartOfDay(z).toInstant(); val e0 = start.plusDays(days.toLong()).atStartOfDay(z).toInstant()
        hc.insertRecords(listOf(MenstruationPeriodRecord(s0, z.rules.getOffset(s0), e0, z.rules.getOffset(e0))))
    }

    // ---- local stores (journal, shoes, widget snapshot, coach key) ----
    fun getString(k: String, d: String = "") = prefs.getString(k, d)!!
    fun putString(k: String, v: String) = prefs.edit().putString(k, v).apply()
    var onboarded: Boolean get() = prefs.getBoolean("onboarded", false); set(v) = prefs.edit().putBoolean("onboarded", v).apply()
    private val secret = ctx.getSharedPreferences("thrivo_secret", Context.MODE_PRIVATE)
    /** Kept in thrivo_secret.xml, which is excluded from Google Drive backup. */
    var apiKey: String
        get() = secret.getString("apiKey", null) ?: prefs.getString("apiKey", "")!!.also { if (it.isNotEmpty()) { secret.edit().putString("apiKey", it).apply(); prefs.edit().remove("apiKey").apply() } }
        set(v) = secret.edit().putString("apiKey", v).apply()

    suspend fun addSteps(count: Long) {
        val now = Instant.now(); val off = ZoneId.systemDefault().rules.getOffset(now)
        hc.insertRecords(listOf(StepsRecord(now.minusSeconds(60), off, now, off, count)))
    }
}

fun exerciseName(t: Int) = when (t) {
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walk"; ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Run"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Bike"; ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swim"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"; ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hike"
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength"; else -> "Workout"
}

fun streaks(h: List<Long>, goal: Int): Pair<Int, Int> {
    var cur = 0; var i = h.lastIndex
    if (h[i] < goal) i--
    while (i >= 0 && h[i] >= goal) { cur++; i-- }
    var best = 0; var run = 0
    h.forEach { if (it >= goal) { run++; best = maxOf(best, run) } else run = 0 }
    return cur to best
}
