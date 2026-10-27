package app.thrivo

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Whoop / Fitbit style scores built on published methods (see Research.kt).
 * They are estimates from Health Connect data, not the proprietary Whoop or Fitbit algorithms.
 */
data class Coach(
    val recovery: Int?, val recoveryParts: List<Pair<String, String>>,
    val strain: Double, val strainTarget: ClosedFloatingPointRange<Double>, val trimp: Double,
    val sleepScore: Int?, val sleepParts: List<Pair<String, String>>,
    val sleepPerformance: Int?, val sleepNeed: Duration, val sleepDebt: Duration, val bedtime: LocalTime,
    val azm: Int, val weeklyAzm: Int, val zoneMinutes: List<Int>,
    val maxHr: Int, val hrvBaseline: Double?, val rhrBaseline: Double?,
)

private fun mean(x: List<Double>) = x.average()
private fun sd(x: List<Double>): Double { val m = mean(x); return sqrt(x.sumOf { (it - m) * (it - m) } / (x.size - 1).coerceAtLeast(1)) }
/** standard normal CDF (Abramowitz & Stegun 7.1.26) — turns a z-score into 0–100 */
private fun phi(z: Double): Double {
    val t = 1 / (1 + 0.3275911 * abs(z) / sqrt(2.0))
    val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-z * z / 2)
    return 0.5 * (1 + if (z >= 0) y else -y)
}

fun coach(v: Vitals, age: Int, female: Boolean): Coach {
    val zone = ZoneId.systemDefault()
    val rest = (v.resting ?: v.rhrDaily.lastOrNull { it != null } ?: 65L).toDouble()
    val max = Science.maxHr(age)                                    // Tanaka 2001
    val reserve = (max - rest).coerceAtLeast(30.0)                  // Karvonen 1957

    // ---------- Zones, AZM (WHO 2020 / ACSM 2011) and TRIMP (Banister) ----------
    val today = LocalDate.now()
    val zoneSec = DoubleArray(4); var trimp = 0.0; var weekAzm = 0.0
    v.hrSamples.zipWithNext().forEach { (a, b) ->
        val mins = Duration.between(a.first, b.first).seconds / 60.0
        if (mins <= 0 || mins > 10) return@forEach                  // gap = watch off
        val hrr = ((a.second - rest) / reserve).coerceIn(0.0, 1.0)
        val z = when { hrr >= Science.NEAR_MAX -> 3; hrr >= Science.VIGOROUS -> 2; hrr >= Science.MODERATE -> 1; else -> 0 }
        weekAzm += mins * when (z) { 1 -> 1; 2, 3 -> 2; else -> 0 }
        if (a.first.atZone(zone).toLocalDate() == today) {
            zoneSec[z] += mins * 60
            trimp += mins * hrr * Science.trimpWeight(hrr, female)
        }
    }
    val zoneMinutes = zoneSec.map { (it / 60).roundToInt() }
    val azm = zoneMinutes[1] + 2 * (zoneMinutes[2] + zoneMinutes[3])
    // Strain: Whoop's logarithmic 0–21 scale; ~100 TRIMP (≈1 h hard run) maps to ~12, 300+ to 20+.
    val strain = (21 * (1 - exp(-trimp / 140.0))).coerceIn(0.0, 21.0)

    // ---------- Sleep need: NSF 2015 by age + 7-night debt (Van Dongen 2003) ----------
    val base = Science.baseSleepMin(age)
    val recent = v.nights.takeLast(7)
    val debtMin = recent.sumOf { (base - it.asleep.toMinutes()).coerceAtLeast(0) }
    val need = Duration.ofMinutes((base + debtMin / 4 + if (strain > 14) 20 else 0).coerceAtMost(base + 90L))
    val night = v.nights.lastOrNull()
    val sleepPerf = night?.let { ((it.asleep.toMinutes() * 100.0) / base).roundToInt().coerceIn(0, 100) }

    // ---------- Sleep score: NSF quality indicators (Ohayon 2017) + stage norms (Ohayon 2004) ----------
    var sleepScore: Int? = null; val sleepParts = mutableListOf<Pair<String, String>>()
    if (night != null) {
        val durPts = (night.asleep.toMinutes().toDouble() / base).coerceAtMost(1.0) * 40
        val effPts = ((night.efficiency - 70) / (Science.GOOD_EFFICIENCY - 70)).coerceIn(0.0, 1.0) * 20
        val wasoPts = (1 - (night.waso.toMinutes() - Science.goodWasoMin(age)).coerceAtLeast(0) / 40.0).coerceIn(0.0, 1.0) * 10
        val total = night.asleep.toMinutes().coerceAtLeast(1).toDouble()
        val deepPct = night.deep.toMinutes() * 100 / total; val remPct = night.rem.toMinutes() * 100 / total
        fun band(p: Double, r: ClosedFloatingPointRange<Double>) = if (p in r) 1.0 else (1 - minOf(abs(p - r.start), abs(p - r.endInclusive)) / r.start).coerceIn(0.0, 1.0)
        val stagePts = if (night.stagesKnown) band(deepPct, Science.deepTarget(age)) * 15 + band(remPct, Science.REM_TARGET) * 15 else 22.0
        sleepScore = (durPts + effPts + wasoPts + stagePts).roundToInt()
        sleepParts += "Duration" to "${fmt(night.asleep)} of ${base / 60}h${if (base % 60 > 0) " ${base % 60}m" else ""} (NSF)"
        sleepParts += "Efficiency" to "%.0f%% · good ≥ 85%%".format(night.efficiency)
        sleepParts += "Awake after onset" to "${night.waso.toMinutes()} min · good ≤ ${Science.goodWasoMin(age)}"
        sleepParts += "Fell asleep in" to "${night.latency.toMinutes()} min · good ≤ 30"
        if (night.stagesKnown) {
            val dt = Science.deepTarget(age)
            sleepParts += "Deep" to "%.0f%% · typical %.0f–%.0f%%".format(deepPct, dt.start, dt.endInclusive)
            sleepParts += "REM" to "%.0f%% · typical 20–25%%".format(remPct)
        }
    }

    // ---------- Recovery: ln rMSSD 7-day rolling vs baseline (Plews 2013, Buchheit 2014) ----------
    val lnHrv = v.hrvDaily.map { it?.takeIf { x -> x > 0 }?.let(::ln) }
    val known = lnHrv.filterNotNull()
    val rhrs = v.rhrDaily.filterNotNull().map { it.toDouble() }
    val recoveryParts = mutableListOf<Pair<String, String>>()
    var recovery: Int? = null
    val hrvBase = known.takeIf { it.size >= 5 }?.let { exp(mean(it)) }
    val rhrBase = rhrs.takeIf { it.size >= 5 }?.let(::mean)
    // last night's HRV, or the night before if the tracker hasn't synced yet
    val todayLn = lnHrv.takeLast(2).lastOrNull { it != null }
    if (known.size >= 5 && todayLn != null) {
        val roll7 = lnHrv.takeLast(7).filterNotNull()
        val baseSd = sd(known).coerceAtLeast(0.03)
        // today's reading weighted with the 7-day trend, expressed in baseline SDs
        val zHrv = ((todayLn * .5 + mean(roll7) * .5) - mean(known)) / baseSd
        val zRhr = if (rhrs.size >= 5) -(rest - mean(rhrs)) / sd(rhrs).coerceAtLeast(1.0) else 0.0
        val zSleep = night?.let { (it.asleep.toMinutes() - base) / 60.0 } ?: 0.0   // 1 h short ≈ −1 SD
        val z = zHrv * .5 + zRhr * .25 + zSleep.coerceIn(-2.5, 1.0) * .25
        recovery = (phi(z) * 100).roundToInt().coerceIn(1, 99)
        val swc = Science.SWC * baseSd
        recoveryParts += "HRV" to "%.0f ms vs %.0f baseline · %s".format(exp(todayLn), hrvBase,
            when { todayLn - mean(known) > swc -> "above normal"; mean(known) - todayLn > swc -> "below normal"; else -> "within normal" })
        if (rhrBase != null) recoveryParts += "Resting HR" to "%.0f bpm vs %.0f baseline".format(rest, rhrBase)
        night?.let { recoveryParts += "Sleep" to "${fmt(it.asleep)} vs ${base / 60}h need" }
    }

    // Whoop-style daily strain target from recovery colour
    val target = when { recovery == null -> 8.0..12.0; recovery >= 67 -> 14.0..18.0; recovery >= 34 -> 10.0..14.0; else -> 4.0..9.0 }
    val bedtime = LocalTime.of(7, 0).minusMinutes(need.toMinutes() + Science.GOOD_LATENCY_MIN / 2)
    return Coach(recovery, recoveryParts, strain, target, trimp, sleepScore, sleepParts, sleepPerf, need,
        Duration.ofMinutes(debtMin.toLong()), bedtime, azm, weekAzm.roundToInt(), zoneMinutes, max.roundToInt(), hrvBase, rhrBase)
}

fun recoveryColor(r: Int?) = when { r == null -> Muted; r >= 67 -> androidx.compose.ui.graphics.Color(0xFF16EC06); r >= 34 -> Accent; else -> Coral }
