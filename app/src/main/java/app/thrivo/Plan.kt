package app.thrivo

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Lab values that shape the plan. Any field can be blank. */
data class Labs(
    val heightCm: Double? = null, val weightKg: Double? = null, val waistCm: Double? = null,
    val ldl: Double? = null, val hdl: Double? = null, val tg: Double? = null, val totalChol: Double? = null,
    val alt: Double? = null, val alp: Double? = null, val hba1c: Double? = null, val fastingGlucose: Double? = null,
    val uricAcid: Double? = null, val mcv: Double? = null, val hb: Double? = null, val sbp: Int? = null, val dbp: Int? = null,
    val tsh: Double? = null, val urea: Double? = null, val ecgCleared: Boolean = false, val reportDate: String? = null,
) {
    val bmi get() = if (heightCm != null && weightKg != null) weightKg / ((heightCm / 100) * (heightCm / 100)) else null
    val nonHdl get() = if (totalChol != null && hdl != null) totalChol - hdl else null
}

object LabStore {
    private fun p(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)
    private val fields = listOf("heightCm", "weightKg", "waistCm", "ldl", "hdl", "tg", "totalChol", "alt", "alp", "hba1c", "fastingGlucose", "uricAcid", "mcv", "hb", "tsh", "urea")
    fun get(ctx: Context): Labs {
        val s = p(ctx).getString("labs", null) ?: return ProfileSeed.labs?.also { save(ctx, it) } ?: Labs()
        val o = JSONObject(s); fun d(k: String) = if (o.has(k) && !o.isNull(k)) o.getDouble(k) else null
        return Labs(d("heightCm"), d("weightKg"), d("waistCm"), d("ldl"), d("hdl"), d("tg"), d("totalChol"), d("alt"), d("alp"), d("hba1c"),
            d("fastingGlucose"), d("uricAcid"), d("mcv"), d("hb"), d("sbp")?.toInt(), d("dbp")?.toInt(), d("tsh"), d("urea"),
            o.optBoolean("ecgCleared"), o.optString("reportDate").ifEmpty { null })
    }
    fun save(ctx: Context, l: Labs) {
        val o = JSONObject()
        listOf("heightCm" to l.heightCm, "weightKg" to l.weightKg, "waistCm" to l.waistCm, "ldl" to l.ldl, "hdl" to l.hdl, "tg" to l.tg, "totalChol" to l.totalChol,
            "alt" to l.alt, "alp" to l.alp, "hba1c" to l.hba1c, "fastingGlucose" to l.fastingGlucose, "uricAcid" to l.uricAcid, "mcv" to l.mcv, "hb" to l.hb,
            "sbp" to l.sbp?.toDouble(), "dbp" to l.dbp?.toDouble(), "tsh" to l.tsh, "urea" to l.urea).forEach { (k, v) -> if (v != null) o.put(k, v) }
        o.put("ecgCleared", l.ecgCleared).put("reportDate", l.reportDate ?: "")
        p(ctx).edit().putString("labs", o.toString()).apply()
    }
    fun planStart(ctx: Context): LocalDate = p(ctx).getString("planStart", null)?.let(LocalDate::parse)
        ?: LocalDate.now().with(DayOfWeek.MONDAY).also { p(ctx).edit().putString("planStart", it.toString()).apply() }
    fun restartPlan(ctx: Context) = p(ctx).edit().putString("planStart", LocalDate.now().with(DayOfWeek.MONDAY).toString()).apply()
}

/** A finding from the profile: label, value, status, and how the plan responds. */
data class Finding(val label: String, val value: String, val flag: Boolean, val response: String)

data class Priorities(val visceral: Boolean, val lipids: Boolean, val liver: Boolean, val hydrate: Boolean, val ironCaution: Boolean,
                      val hiitOk: Boolean, val weeklyCardioMin: Int, val proteinG: IntRange, val refKg: Double, val findings: List<Finding>)

fun priorities(l: Labs, age: Int): Priorities {
    val f = mutableListOf<Finding>()
    val bmi: Double? = l.bmi
    // Asia-Pacific BMI cut-offs (WHO Expert Consultation 2004): ≥25 obese; IDF waist ≥90 cm (South Asian men)
    val visceral = (bmi != null && bmi >= 25) || (l.alt != null && l.alt > 40) || (l.waistCm != null && l.waistCm >= 90)
    bmi?.let { f += Finding("BMI", "%.1f".format(it), it >= 23, if (it >= 25) "Obese range (Asian cut-off ≥25): fat-loss first, low-impact cardio to protect joints." else if (it >= 23) "Overweight range: fat-loss focus." else "Healthy range.") }
    l.alt?.let { f += Finding("ALT (liver)", "%.0f U/L".format(it), it > 40, if (it > 40) "Mildly raised — often fatty liver with visceral fat. 150–240 min/week of cardio plus strength cuts liver fat even before much weight loss (Keating 2012; EASL 2024)." else "Normal.") }
    l.alp?.let { if (it > 116) f += Finding("ALP", "%.0f U/L".format(it), true, "Slightly above range — ask your doctor to re-check with your liver enzymes.") }
    val lipids = (l.ldl != null && l.ldl >= 130) || (l.nonHdl?.let { it >= 160 } == true)
    l.ldl?.let { f += Finding("LDL cholesterol", "%.0f mg/dL".format(it), it >= 130, if (it >= 130) "Borderline-high: regular aerobic work and losing 5–10% body weight are first-line (LAI 2024)." else "OK.") }
    l.hdl?.let { f += Finding("HDL cholesterol", "%.0f mg/dL".format(it), it < 40, if (it < 40) "Low — aerobic exercise raises HDL." else "OK; cardio pushes it higher.") }
    l.hba1c?.let { f += Finding("HbA1c", "%.1f%%".format(it), it >= 5.7, if (it >= 5.7) "Pre-diabetes range: strength + cardio on most days." else "Normal blood sugar control.") }
    if (l.sbp != null && l.dbp != null) f += Finding("Blood pressure", "${l.sbp}/${l.dbp}", l.sbp >= 130 || l.dbp >= 80, if (l.sbp >= 130 || l.dbp >= 80) "Raised: avoid breath-holding on heavy lifts." else "Normal — no training limits.")
    val hydrate = l.uricAcid != null && l.uricAcid >= 7.0
    l.uricAcid?.let { f += Finding("Uric acid", "%.1f mg/dL".format(it), it >= 7.0, if (it >= 7.0) "Upper end: drink 500 ml water before and after workouts; limit sugary drinks and alcohol." else "Normal.") }
    val iron = l.mcv != null && l.mcv < 80
    l.mcv?.let { f += Finding("Red cell size (MCV)", "%.0f fL".format(it), it < 80, if (it < 80) "Small red cells with normal haemoglobin — often thalassaemia trait. Doesn't limit exercise; don't take iron without a doctor confirming (Hb-HPLC/iron tests)." else "Normal.") }
    l.urea?.let { if (it < 19) f += Finding("Urea / BUN", "%.1f mg/dL".format(it), true, "Low — often low protein intake. Hit the daily protein target below.") }
    l.tsh?.let { f += Finding("Thyroid (TSH)", "%.2f".format(it), it > 4.94 || it < 0.35, if (it in 0.35..4.94) "Normal." else "Out of range — check with your doctor.") }
    f += Finding("ECG", if (l.ecgCleared) "Normal" else "Pending", !l.ecgCleared, if (l.ecgCleared) "Cleared for hard intervals." else "Hard intervals stay moderate until your ECG is back and normal.")
    val refKg = l.heightCm?.let { 24.0 * (it / 100) * (it / 100) } ?: (l.weightKg ?: 70.0)         // protein on goal-weight basis
    val protein = (1.2 * refKg).toInt()..(1.6 * refKg).toInt()                                   // Morton 2018; ISSN 2017
    return Priorities(visceral, lipids, l.alt != null && l.alt > 40, hydrate, iron, l.ecgCleared, if (visceral || lipids) 240 else 150, protein, refKg, f)
}

// ---------------- routine ----------------
/** One exercise prescription. [id] is an asset folder under exercises/. */
data class Rx(val id: String, val sets: Int, val reps: String, val restSec: Int, val cue: String, val home: String? = null)
data class Cardio(val label: String, val minutes: Int, val program: String?, val note: String)
data class Day(val dow: DayOfWeek, val title: String, val focus: String, val warmup: String, val main: List<Rx>, val core: List<Rx>, val cardio: List<Cardio>,
               val cooldown: List<Rx>, val minutes: Int, val rest: Boolean = false)

/** Phase 1 = weeks 1–4 (technique, 12–15 reps), 2 = weeks 5–8 (10–12), 3 = weeks 9–12 (8–12, heavier). */
fun phaseOf(start: LocalDate, today: LocalDate = LocalDate.now()) = ((ChronoUnit.WEEKS.between(start, today) / 4).toInt() % 3) + 1

fun week(p: Priorities, phase: Int): List<Day> {
    val sets = when (phase) { 1 -> 2; 2 -> 3; else -> 4 }
    val reps = when (phase) { 1 -> "12–15"; 2 -> "10–12"; else -> "8–12" }
    val rest = when (phase) { 1 -> 60; 2 -> 75; else -> 90 }
    val core = listOf(Rx("Dead_Bug", 2, "10/side", 30, "Low back pressed into the floor; move slowly."),
        Rx("Plank", 3, if (phase == 1) "20–30 s" else "40–60 s", 30, "Straight line head to heels; squeeze glutes."),
        Rx("Side_Bridge", 2, "20–30 s/side", 30, "Hips up, don't sag."))
    val stretch = listOf(Rx("Cat_Stretch", 1, "8 slow reps", 0, "Move with your breath."), Rx("Childs_Pose", 1, "45 s", 0, "Sink hips back, relax shoulders."))
    val intervals = if (p.hiitOk && phase >= 2) "Run intervals" else "Hill intervals"
    return listOf(
        Day(DayOfWeek.MONDAY, "Lower body + incline walk", "Legs & glutes — your biggest muscles burn the most energy",
            "5 min easy walk + 10 bodyweight squats",
            listOf(Rx("Goblet_Squat", sets + 1, reps, rest, "Chest up, knees track over toes, sit between your heels.", "Bodyweight_Squat"),
                Rx("Romanian_Deadlift", sets, reps, rest, "Push hips back, soft knees, flat back; feel the hamstrings.", "Single_Leg_Glute_Bridge"),
                Rx("Dumbbell_Lunges", sets, "10/leg", rest, "Long step, back knee towards the floor, front heel down.", "Dumbbell_Lunges"),
                Rx("Leg_Press", sets, reps, rest, "Don't lock knees at the top; full controlled range.", "Dumbbell_Step_Ups"),
                Rx("Lying_Leg_Curls", 2, reps, 60, "Squeeze at the top, lower slowly.", "Single_Leg_Glute_Bridge"),
                Rx("Standing_Calf_Raises", 2, "15–20", 45, "Full stretch at the bottom, pause at the top.")),
            core.take(2), listOf(Cardio("Incline walk", 20, "Incline walk 20", "5.0 km/h at 6–10% incline — conversational pace.")), stretch, 60),
        Day(DayOfWeek.TUESDAY, "Fat-burn cardio + core", "Visceral & liver fat — steady Zone 2 is the most proven tool",
            "3 min easy walk",
            emptyList(), core + listOf(Rx("Reverse_Crunch", 2, "12–15", 30, "Curl hips off the floor, no swinging."), Rx("Superman", 2, "10", 30, "Lift chest and legs, hold 2 s.")),
            listOf(Cardio(if (phase == 1) "Steady incline walk" else "12-3-30 walk", if (phase == 1) 35 else 30, if (phase == 1) "Zone 2 walk 35" else "12-3-30",
                "Zone 2: you can talk in sentences. Keep heart rate near the target below.")), stretch, 55),
        Day(DayOfWeek.WEDNESDAY, "Upper body + short intervals", "Chest, back, shoulders, arms — posture and strength",
            "5 min easy row or bike + arm circles",
            listOf(Rx("Dumbbell_Bench_Press", sets, reps, rest, "Shoulder blades pinned back, lower to chest level.", "Pushups"),
                Rx("Seated_Cable_Rows", sets, reps, rest, "Pull elbows back, squeeze shoulder blades, don't lean back.", "Bent_Over_Two-Dumbbell_Row"),
                Rx("Dumbbell_Shoulder_Press", sets, reps, rest, "Ribs down, press straight up, no arching.", "Dumbbell_Shoulder_Press"),
                Rx("Wide-Grip_Lat_Pulldown", sets, reps, rest, "Pull bar to upper chest, elbows down and back.", "Bent_Over_Two-Dumbbell_Row"),
                Rx("Face_Pull", 2, "12–15", 45, "Pull to eyes, elbows high, rotate hands back.", "Superman"),
                Rx("Dumbbell_Bicep_Curl", 2, reps, 45, "No swinging; lower slowly."),
                Rx("Triceps_Pushdown", 2, reps, 45, "Elbows pinned to your sides.", "Pushups")),
            emptyList(), listOf(Cardio("Hill intervals", 15, "Hill intervals (short)", "Alternate 2 min climbing at 8% with 2 min easy.")), stretch, 60),
        Day(DayOfWeek.THURSDAY, "Intervals + core", "Intervals cut visceral fat in less time (Maillard 2018)",
            "5 min easy walk building to brisk",
            emptyList(), listOf(Rx("Mountain_Climbers", 3, "30 s", 30, "Hips level, drive knees to chest.", "Step-up_with_Knee_Raise"),
                Rx("Russian_Twist", 3, "16 total", 30, "Rotate from the ribs, feet down if needed."),
                Rx("Air_Bike", 3, "20 total", 30, "Elbow to opposite knee, slow and controlled."), core[1]),
            listOf(Cardio(intervals, 30, intervals, if (p.hiitOk) "Fast efforts should feel 8/10 — breathing hard." else "Climbs should feel 6–7/10 until your ECG is cleared.")), stretch, 50),
        Day(DayOfWeek.FRIDAY, "Full-body circuit + Zone 2", "Every major muscle in one circuit — high calorie burn",
            "5 min easy walk + 10 hip hinges",
            listOf(Rx("Goblet_Squat", 3, "12", 0, "Circuit: move straight to the next exercise.", "Bodyweight_Squat"),
                Rx("Pushups", 3, "8–12", 0, "Knees down is fine; full range.", "Pushups"),
                Rx("Bent_Over_Two-Dumbbell_Row", 3, "12", 0, "Flat back, pull to hips.", "Bent_Over_Two-Dumbbell_Row"),
                Rx("Dumbbell_Step_Ups", 3, "10/leg", 0, "Drive through the heel of the top foot.", "Step-up_with_Knee_Raise"),
                Rx("One-Arm_Kettlebell_Swings", 3, "15", 0, "Snap hips forward; arms just guide the bell.", "Single_Leg_Glute_Bridge"),
                Rx("Farmers_Walk", 3, "30 m", 90, "Tall posture, tight core. Rest 90 s after each round.", "Farmers_Walk")),
            emptyList(), listOf(Cardio("Zone 2 walk", 25, "Zone 2 walk 25", "Easy-moderate: brisk walk or light incline.")), stretch, 60),
        Day(DayOfWeek.SATURDAY, "Active recovery", "Long easy movement — 8–10k steps", "",
            emptyList(), emptyList(), listOf(Cardio("Long walk, hike or easy ride", 45, null, "Outdoors if you can. Easy pace.")), stretch, 50, rest = true),
        Day(DayOfWeek.SUNDAY, "Rest", "Recovery is when muscle is built", "", emptyList(), emptyList(), emptyList(), stretch, 10, rest = true),
    )
}

/** Extra treadmill programs used by the plan. */
val PLAN_PROGRAMS = listOf(
    Program("Incline walk 20", "20 min at 5.0 km/h, 8% incline.", listOf(Triple(2.0, 4.5, 2.0), Triple(16.0, 5.0, 8.0), Triple(2.0, 4.0, 0.0)), listOf("Warm-up", "Incline walk", "Cool-down")),
    Program("Zone 2 walk 35", "35 min brisk walk with gentle incline.", listOf(Triple(3.0, 4.5, 1.0), Triple(29.0, 5.5, 5.0), Triple(3.0, 4.0, 0.0)), listOf("Warm-up", "Zone 2", "Cool-down")),
    Program("Zone 2 walk 25", "25 min brisk walk with gentle incline.", listOf(Triple(2.0, 4.5, 1.0), Triple(21.0, 5.5, 4.0), Triple(2.0, 4.0, 0.0)), listOf("Warm-up", "Zone 2", "Cool-down")),
    Program("Hill intervals (short)", "3 rounds of 2 min at 8% / 2 min easy.", listOf(Triple(2.0, 4.5, 1.0)) + (1..3).flatMap { listOf(Triple(2.0, 5.5, 8.0), Triple(2.0, 5.0, 1.0)) } + listOf(Triple(1.0, 4.0, 0.0)),
        listOf("Warm-up") + (1..3).flatMap { listOf("Climb $it", "Recover $it") } + listOf("Cool-down")),
)
fun programByName(n: String) = (PROGRAMS + PLAN_PROGRAMS).firstOrNull { it.name == n }

/** Zone 2 heart-rate target: 60–70% of heart-rate reserve (Karvonen) when resting HR is known, else 60–70% of max. */
fun zone2(age: Int, restingHr: Long?): IntRange {
    val max = Science.maxHr(age)
    return if (restingHr != null) (restingHr + .6 * (max - restingHr)).toInt()..(restingHr + .7 * (max - restingHr)).toInt() else (.6 * max).toInt()..(.7 * max).toInt()
}
