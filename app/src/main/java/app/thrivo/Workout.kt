package app.thrivo

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.health.connect.client.records.ExerciseSessionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/** How a workout is measured. */
enum class Mode { TREADMILL, GPS, BIKE, MET, STRENGTH }

/**
 * Activity types. [met] values are from the 2011 Compendium of Physical Activities (Ainsworth et al.),
 * used where no speed/power-based equation applies.
 */
enum class Act(val label: String, val icon: ImageVector, val mode: Mode, val hcType: Int, val met: Double) {
    TREADMILL("Treadmill", Icons.Default.DirectionsRun, Mode.TREADMILL, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL, 0.0),
    WALK("Outdoor walk", Icons.Default.DirectionsWalk, Mode.GPS, ExerciseSessionRecord.EXERCISE_TYPE_WALKING, 0.0),
    RUN("Outdoor run", Icons.Default.DirectionsRun, Mode.GPS, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, 0.0),
    RIDE("Outdoor ride", Icons.Default.DirectionsBike, Mode.GPS, ExerciseSessionRecord.EXERCISE_TYPE_BIKING, 0.0),
    BIKE("Stationary bike", Icons.Default.PedalBike, Mode.BIKE, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY, 0.0),
    ELLIPTICAL("Elliptical", Icons.Default.Sync, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL, 5.0),
    ROWER("Rowing machine", Icons.Default.Rowing, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE, 7.0),
    STAIRS("Stair climber", Icons.Default.Stairs, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE, 9.0),
    STRENGTH("Strength", Icons.Default.FitnessCenter, Mode.STRENGTH, ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, 5.0),
    HIIT("HIIT", Icons.Default.Bolt, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, 8.0),
    YOGA("Yoga", Icons.Default.SelfImprovement, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_YOGA, 2.5),
    SWIM("Swimming (pool)", Icons.Default.Pool, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, 6.0),
    OTHER("Other workout", Icons.Default.SportsGymnastics, Mode.MET, ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, 4.0),
}

/** A stretch of a workout at fixed settings (speed km/h, incline %, power W). Open segment has no end. */
data class Seg(val start: Instant, val end: Instant?, val kmh: Double = 0.0, val incline: Double = 0.0, val watts: Int = 0, val label: String = "")
data class SetLog(val exercise: String, val reps: Int, val kg: Double, val at: Instant)

/** Treadmill programs: list of (minutes, km/h, incline %, label). */
data class Program(val name: String, val about: String, val steps: List<Triple<Double, Double, Double>>, val labels: List<String>)
val PROGRAMS = listOf(
    Program("12-3-30", "30 min at 12% incline, 4.8 km/h (3 mph). A steady, joint-friendly hill walk.",
        listOf(Triple(30.0, 4.8, 12.0)), listOf("12-3-30 walk")),
    Program("Hill intervals", "5 rounds: 2 min climbing at 8%, 2 min easy at 1%. Warm-up and cool-down included.",
        listOf(Triple(3.0, 4.5, 1.0)) + (1..5).flatMap { listOf(Triple(2.0, 5.5, 8.0), Triple(2.0, 5.0, 1.0)) } + listOf(Triple(3.0, 4.0, 0.0)),
        listOf("Warm-up") + (1..5).flatMap { listOf("Climb $it", "Recover $it") } + listOf("Cool-down")),
    Program("Run intervals", "6 rounds: 1 min fast run, 2 min easy jog. Builds speed and VO₂max.",
        listOf(Triple(4.0, 6.0, 1.0)) + (1..6).flatMap { listOf(Triple(1.0, 10.0, 1.0), Triple(2.0, 6.5, 1.0)) } + listOf(Triple(3.0, 5.0, 0.0)),
        listOf("Warm-up") + (1..6).flatMap { listOf("Fast $it", "Easy $it") } + listOf("Cool-down")),
    Program("Steady walk", "30 min brisk walk at 5.5 km/h, 2% incline.", listOf(Triple(30.0, 5.5, 2.0)), listOf("Brisk walk")),
)

data class Live(
    val act: Act, val start: Instant, val segs: List<Seg>, val pausedAt: Instant? = null, val pausedMs: Long = 0,
    val laps: List<Instant> = emptyList(), val sets: List<SetLog> = emptyList(), val program: String? = null,
)

/** A finished workout kept locally for the detail page (Health Connect has no incline field). */
data class Done(
    val id: Long, val act: Act, val start: Instant, val end: Instant, val activeSec: Long, val meters: Double, val kcal: Double,
    val elevM: Double, val segs: List<Seg>, val sets: List<SetLog>, val program: String?, val savedToHc: Boolean,
)

object Science2 {
    /** ACSM metabolic equations (ACSM's Guidelines, 11th ed.): gross VO₂ in ml/kg/min for treadmill walking/running. */
    fun treadmillVo2(kmh: Double, inclinePct: Double): Double {
        val s = kmh * 1000 / 60; val g = inclinePct / 100            // m/min, fractional grade
        return if (kmh < 7.0) 0.1 * s + 1.8 * s * g + 3.5 else 0.2 * s + 0.9 * s * g + 3.5
    }
    /** ACSM leg-cycle ergometry: VO₂ = 10.8 × W / kg + 7. */
    fun bikeVo2(watts: Int, kg: Double) = 10.8 * watts / kg + 7
    /** Outdoor cycling METs by speed (Compendium 2011, codes 01010–01050). */
    fun rideMet(kmh: Double) = when { kmh < 16 -> 4.0; kmh < 19 -> 6.8; kmh < 22 -> 8.0; kmh < 25.5 -> 10.0; else -> 12.0 }
    /** Net (active) kcal from VO₂: (VO₂ − 3.5) × kg × min / 1000 × 5 kcal per litre O₂. */
    fun netKcal(vo2: Double, kg: Double, min: Double) = ((vo2 - 3.5).coerceAtLeast(0.0)) * kg * min / 1000 * 5
    fun metKcal(met: Double, kg: Double, min: Double) = ((met - 1).coerceAtLeast(0.0)) * 3.5 * kg * min / 1000 * 5
}

object WorkoutTracker {
    val live = MutableStateFlow<Live?>(null)
    private fun p(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)

    // ---------- live session ----------
    fun begin(ctx: Context, act: Act, kmh: Double = 0.0, incline: Double = 0.0, watts: Int = 0, program: Program? = null) {
        val now = Instant.now()
        val segs = if (program != null) {
            var t = now; program.steps.mapIndexed { i, (min, s, inc) ->
                val e = t.plusSeconds((min * 60).toLong()); Seg(t, e, s, inc, 0, program.labels[i]).also { t = e } }
        } else listOf(Seg(now, null, kmh, incline, watts))
        live.value = Live(act, now, segs, program = program?.name); persist(ctx)
    }
    /** Change speed/incline/power: closes the current segment and opens a new one (the rest of a program is dropped). */
    fun set(ctx: Context, kmh: Double? = null, incline: Double? = null, watts: Int? = null) {
        val l = live.value ?: return; val now = Instant.now()
        val cur = current(l) ?: return
        val done = l.segs.filter { !it.start.isAfter(now) }.map { if (it === cur) it.copy(end = now) else it }
        val next = Seg(now, null, kmh ?: cur.kmh, incline ?: cur.incline, watts ?: cur.watts, if (l.program != null) "Custom" else "")
        live.value = l.copy(segs = done + next, program = l.program); persist(ctx)
    }
    fun pause(ctx: Context) { val l = live.value ?: return; if (l.pausedAt == null) { live.value = l.copy(pausedAt = Instant.now()); persist(ctx) } }
    fun resume(ctx: Context) {
        val l = live.value ?: return; val p = l.pausedAt ?: return; val gap = Duration.between(p, Instant.now())
        // shift every not-yet-finished segment by the pause so programs continue where they left off
        val segs = l.segs.map { s -> when {
            s.end != null && !s.end.isAfter(p) -> s
            s.start.isAfter(p) -> s.copy(start = s.start.plus(gap), end = s.end?.plus(gap))
            else -> s.copy(end = s.end?.plus(gap)) } }
        live.value = l.copy(segs = segs, pausedAt = null, pausedMs = l.pausedMs + gap.toMillis()); persist(ctx)
    }
    fun lap(ctx: Context) { val l = live.value ?: return; live.value = l.copy(laps = l.laps + Instant.now()); persist(ctx) }
    fun addSet(ctx: Context, s: SetLog) { val l = live.value ?: return; live.value = l.copy(sets = l.sets + s); persist(ctx) }
    fun discard(ctx: Context) { live.value = null; p(ctx).edit().remove("liveWorkout").apply() }

    fun current(l: Live, at: Instant = Instant.now()): Seg? =
        l.segs.lastOrNull { !it.start.isAfter(at) && (it.end == null || it.end.isAfter(at)) } ?: l.segs.lastOrNull { !it.start.isAfter(at) }
    fun nextSeg(l: Live, at: Instant = Instant.now()): Seg? = l.segs.firstOrNull { it.start.isAfter(at) }
    fun activeSec(l: Live, at: Instant = Instant.now()) = (Duration.between(l.start, l.pausedAt ?: at).toMillis() - l.pausedMs) / 1000

    /** Distance (m), elevation (m), active kcal so far — from segments for treadmill/bike, GPS for outdoor, METs otherwise. */
    fun totals(l: Live, kg: Double, gpsMeters: Double, gpsKmh: Double, at: Instant = Instant.now()): Triple<Double, Double, Double> {
        val until = l.pausedAt ?: at
        var m = 0.0; var elev = 0.0; var kcal = 0.0
        when (l.act.mode) {
            Mode.TREADMILL, Mode.BIKE -> l.segs.forEach { s ->
                val e = minOf(s.end ?: until, until); if (!e.isAfter(s.start)) return@forEach
                val min = Duration.between(s.start, e).toMillis() / 60000.0
                if (l.act.mode == Mode.TREADMILL) {
                    val d = s.kmh * 1000 * min / 60; m += d; elev += d * s.incline / 100
                    kcal += Science2.netKcal(Science2.treadmillVo2(s.kmh, s.incline), kg, min)
                } else kcal += Science2.netKcal(Science2.bikeVo2(s.watts, kg), kg, min)
            }
            Mode.GPS -> {
                m = gpsMeters; val min = activeSec(l, at) / 60.0; val avg = if (min > 0) gpsMeters / 1000 / (min / 60) else 0.0
                kcal = if (l.act == Act.RIDE) Science2.metKcal(Science2.rideMet(avg), kg, min) else Science2.netKcal(Science2.treadmillVo2(avg, 0.0), kg, min)
            }
            else -> kcal = Science2.metKcal(l.act.met, kg, activeSec(l, at) / 60.0)
        }
        return Triple(m, elev, kcal)
    }

    private fun segJ(s: Seg) = JSONObject().put("s", s.start.toEpochMilli()).put("e", s.end?.toEpochMilli() ?: 0).put("k", s.kmh).put("i", s.incline).put("w", s.watts).put("l", s.label)
    private fun segP(o: JSONObject) = Seg(Instant.ofEpochMilli(o.getLong("s")), o.optLong("e").takeIf { it > 0 }?.let(Instant::ofEpochMilli), o.optDouble("k"), o.optDouble("i"), o.optInt("w"), o.optString("l"))
    private fun setJ(s: SetLog) = JSONObject().put("x", s.exercise).put("r", s.reps).put("kg", s.kg).put("t", s.at.toEpochMilli())
    private fun setP(o: JSONObject) = SetLog(o.getString("x"), o.getInt("r"), o.getDouble("kg"), Instant.ofEpochMilli(o.getLong("t")))
    private fun arr(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it) }

    fun persist(ctx: Context) {
        val l = live.value ?: return
        p(ctx).edit().putString("liveWorkout", JSONObject().put("a", l.act.name).put("st", l.start.toEpochMilli()).put("pa", l.pausedAt?.toEpochMilli() ?: 0)
            .put("pm", l.pausedMs).put("pr", l.program ?: "").put("segs", JSONArray(l.segs.map(::segJ))).put("sets", JSONArray(l.sets.map(::setJ)))
            .put("laps", JSONArray(l.laps.map { it.toEpochMilli() })).toString()).apply()
    }
    /** Restore an in-progress workout after the app was closed. */
    fun restore(ctx: Context) {
        if (live.value != null) return
        val o = runCatching { JSONObject(p(ctx).getString("liveWorkout", null) ?: return) }.getOrNull() ?: return
        live.value = Live(Act.valueOf(o.getString("a")), Instant.ofEpochMilli(o.getLong("st")), arr(o.optJSONArray("segs")).map(::segP),
            o.optLong("pa").takeIf { it > 0 }?.let(Instant::ofEpochMilli), o.optLong("pm"),
            o.optJSONArray("laps")?.let { a -> (0 until a.length()).map { Instant.ofEpochMilli(a.getLong(it)) } } ?: emptyList(),
            arr(o.optJSONArray("sets")).map(::setP), o.optString("pr").ifEmpty { null })
    }

    // ---------- history ----------
    fun history(ctx: Context): List<Done> = runCatching {
        arr(JSONArray(p(ctx).getString("workouts", "[]"))).map { o -> Done(o.getLong("id"), Act.valueOf(o.getString("a")), Instant.ofEpochMilli(o.getLong("st")),
            Instant.ofEpochMilli(o.getLong("en")), o.getLong("as"), o.getDouble("m"), o.getDouble("kc"), o.getDouble("el"),
            arr(o.optJSONArray("segs")).map(::segP), arr(o.optJSONArray("sets")).map(::setP), o.optString("pr").ifEmpty { null }, o.optBoolean("hc")) }
    }.getOrDefault(emptyList())
    fun saveDone(ctx: Context, d: Done) {
        val all = history(ctx).filter { it.id != d.id } + d
        p(ctx).edit().putString("workouts", JSONArray(all.takeLast(500).map { w -> JSONObject().put("id", w.id).put("a", w.act.name).put("st", w.start.toEpochMilli())
            .put("en", w.end.toEpochMilli()).put("as", w.activeSec).put("m", w.meters).put("kc", w.kcal).put("el", w.elevM).put("pr", w.program ?: "").put("hc", w.savedToHc)
            .put("segs", JSONArray(w.segs.map(::segJ))).put("sets", JSONArray(w.sets.map(::setJ))) }).toString()).apply()
    }
    fun delete(ctx: Context, id: Long) { saveAll(ctx, history(ctx).filter { it.id != id }) }
    private fun saveAll(ctx: Context, l: List<Done>) { p(ctx).edit().putString("workouts", "[]").apply(); l.forEach { saveDone(ctx, it) } }
}

val STRENGTH_EXERCISES = listOf("Squat", "Bench press", "Deadlift", "Overhead press", "Barbell row", "Pull-up", "Lat pulldown", "Leg press",
    "Lunge", "Dumbbell curl", "Tricep pushdown", "Shoulder raise", "Push-up", "Plank (sec)", "Romanian deadlift", "Hip thrust")
