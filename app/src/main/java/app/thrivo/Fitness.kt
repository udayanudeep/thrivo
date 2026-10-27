package app.thrivo

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- Cardio Fitness (VO₂max) ----------
/** FRIEND registry treadmill VO₂max medians (Kaminsky 2015), by decade 20s…70s. */
private val VO2_MEDIAN_M = listOf(48.0, 42.4, 37.8, 32.6, 28.2, 24.4)
private val VO2_MEDIAN_F = listOf(37.6, 30.9, 27.7, 24.2, 20.9, 18.3)

fun vo2Median(age: Int, female: Boolean): Double {
    val t = if (female) VO2_MEDIAN_F else VO2_MEDIAN_M
    val x = ((age - 25) / 10.0).coerceIn(0.0, 5.0)          // midpoints 25, 35 … 75
    val i = x.toInt().coerceAtMost(4); val f = x - i
    return t[i] + (t[i + 1] - t[i]) * f
}

data class Fitness(val vo2: Double?, val fromDevice: Boolean, val rating: String, val fitnessAge: Int?, val median: Double)

fun fitness(v: Vitals, age: Int, female: Boolean): Fitness {
    val rhr = v.rhrDaily.filterNotNull().takeLast(14).takeIf { it.isNotEmpty() }?.average() ?: v.resting?.toDouble()
    val vo2 = v.vo2Device ?: rhr?.let { 15.3 * Science.maxHr(age) / it }           // Uth 2004
    val med = vo2Median(age, female)
    val rating = vo2?.let { val r = it / med; when { r < .8 -> "Poor"; r < .95 -> "Fair"; r < 1.05 -> "Average"; r < 1.2 -> "Good"; else -> "Excellent" } } ?: "–"
    // fitness age (Nes 2014): age where the median equals your VO₂max
    val fAge = vo2?.let { x -> (20..90).minByOrNull { abs(vo2Median(it, female) - x) } }
    return Fitness(vo2, v.vo2Device != null, rating, fAge, med)
}

// ---------- Cardio Load (Banister TRIMP per day, Gabbett ACWR) ----------
data class Load(val daily: List<Double>, val acute: Double, val chronic: Double, val ratio: Double?, val status: String)

fun cardioLoad(v: Vitals, age: Int, female: Boolean): Load {
    val rest = (v.resting ?: 65L).toDouble(); val reserve = (Science.maxHr(age) - rest).coerceAtLeast(30.0)
    val daily = (0 until 28).map { d ->
        (0 until 48).sumOf { s ->
            val hr = v.hr30m.getOrNull(d * 48 + s) ?: return@sumOf 0.0
            val hrr = ((hr - rest) / reserve).coerceIn(0.0, 1.0)
            if (hrr < Science.MODERATE) 0.0 else 30 * hrr * Science.trimpWeight(hrr, female)   // only count active slots
        }
    }
    val acute = daily.takeLast(7).average(); val chronic = daily.average()
    val ratio = if (chronic > 1) acute / chronic else null
    val status = when { ratio == null -> "Building baseline"; ratio < .8 -> "Detraining"; ratio <= 1.3 -> "Optimal"; ratio <= 1.5 -> "High"; else -> "Overreaching" }
    return Load(daily, acute, chronic, ratio, status)
}

// ---------- Stress (0–3), Whoop-style ----------
/** Per 30-min slot today: HR elevation above resting while not moving (< 300 steps), as a share of HR reserve. */
fun stressToday(v: Vitals, t: Today, age: Int): List<Double?> {
    val rest = (v.resting ?: 65L).toDouble(); val reserve = (Science.maxHr(age) - rest).coerceAtLeast(30.0)
    val today = v.hr30m.takeLast(48)
    val nowSlot = LocalTime.now().let { it.hour * 2 + it.minute / 30 }
    return List(48) { i ->
        val hr = today.getOrNull(i) ?: return@List null
        if (i > nowSlot || t.steps30m[i] >= 300) return@List null            // moving = exercise, not stress
        (((hr - rest) / (reserve * .25)) * 3).coerceIn(0.0, 3.0)
    }
}

// ---------- Sleep regularity & chronotype ----------
data class SleepProfile(val sri: Int?, val avgBed: LocalTime?, val avgWake: LocalTime?, val bedSdMin: Int?,
                        val midSleep: LocalTime?, val chronotype: String, val avgSleep: Duration?, val nights: Int)

fun sleepProfile(nights: List<Night>): SleepProfile {
    val zone = ZoneId.systemDefault()
    if (nights.isEmpty()) return SleepProfile(null, null, null, null, null, "Not enough data", null, 0)
    // minute-level sleep map for the last 8 days → Sleep Regularity Index (Phillips 2017)
    val end = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant(); val start = end.minus(Duration.ofDays(8))
    val asleep = BooleanArray(8 * 1440)
    nights.forEach { n ->
        val spans = if (n.stages.isNotEmpty()) n.stages.filter { it.type !in AWAKE_TYPES }.map { it.start to it.end } else listOf(n.start to n.end)
        spans.forEach { (a, b) ->
            var m = Duration.between(start, a).toMinutes().toInt(); val e = Duration.between(start, b).toMinutes().toInt()
            while (m < e) { if (m in asleep.indices) asleep[m] = true; m++ }
        }
    }
    val nowIdx = Duration.between(start, java.time.Instant.now()).toMinutes().toInt().coerceIn(0, asleep.size)
    val pairs = (0 until (nowIdx - 1440).coerceAtLeast(0))
    val sri = if (nights.size >= 3 && pairs.count() > 1440 * 2) (pairs.count { asleep[it] == asleep[it + 1440] } * 100.0 / pairs.count()).roundToInt() else null
    // timing in minutes relative to noon, so 23:00 and 01:00 average correctly
    fun rel(t: LocalTime) = ((t.toSecondOfDay() / 60 - 720 + 1440) % 1440)
    fun back(m: Double) = LocalTime.ofSecondOfDay((((m + 720) % 1440) * 60).toLong())
    val recent = nights.takeLast(30)
    val beds = recent.map { rel(it.start.atZone(zone).toLocalTime()).toDouble() }
    val wakes = recent.map { rel(it.end.atZone(zone).toLocalTime()).toDouble() }
    val mids = recent.zip(beds).map { (n, b) -> b + n.inBed.toMinutes() / 2.0 }
    val bedMean = beds.average(); val sdBed = kotlin.math.sqrt(beds.sumOf { (it - bedMean) * (it - bedMean) } / beds.size)
    val mid = back(mids.average())
    val midH = mid.hour + mid.minute / 60.0
    val chrono = when { midH in 12.0..24.0 || midH < 3 -> "Early bird (lark)"; midH <= 5 -> "Intermediate"; else -> "Night owl" }  // Roenneberg 2003
    return SleepProfile(sri, back(bedMean), back(wakes.average()), sdBed.roundToInt(), mid, chrono,
        Duration.ofMinutes(recent.map { it.asleep.toMinutes() }.average().toLong()), recent.size)
}

private val AWAKE_TYPES = setOf(
    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_AWAKE,
    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    androidx.health.connect.client.records.SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)

// ---------- Cycle ----------
data class Cycle(val day: Int?, val length: Double, val phase: String, val nextPeriod: LocalDate?, val fertile: ClosedRange<LocalDate>?)

fun cycle(periods: List<Pair<LocalDate, LocalDate>>): Cycle {
    if (periods.isEmpty()) return Cycle(null, 29.3, "Log a period to start", null, null)
    val starts = periods.map { it.first }.distinct()
    val gaps = starts.zipWithNext { a, b -> Duration.between(a.atStartOfDay(), b.atStartOfDay()).toDays().toDouble() }.filter { it in 18.0..45.0 }
    val len = gaps.takeLast(6).takeIf { it.isNotEmpty() }?.average() ?: 29.3                     // Bull 2019 mean
    val last = periods.last(); val today = LocalDate.now()
    val day = Duration.between(last.first.atStartOfDay(), today.atStartOfDay()).toDays().toInt() + 1
    val next = last.first.plusDays(len.roundToInt().toLong())
    val ovulation = next.minusDays(14)
    val phase = when {
        !today.isAfter(last.second) -> "Menstrual"
        today.isBefore(ovulation.minusDays(1)) -> "Follicular"
        !today.isAfter(ovulation.plusDays(1)) -> "Ovulation (estimated)"
        else -> "Luteal"
    }
    return Cycle(day, len, phase, next, ovulation.minusDays(5)..ovulation.plusDays(1))
}
