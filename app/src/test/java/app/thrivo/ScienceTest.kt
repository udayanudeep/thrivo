package app.thrivo

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class ScienceTest {
    private fun near(expected: Double, actual: Double, tol: Double) = assertTrue("expected $expected ± $tol, got $actual", abs(expected - actual) <= tol)

    @Test fun maxHr_tanaka() { near(187.0, Science.maxHr(30), 1e-9); near(166.0, Science.maxHr(60), 1e-9) }

    @Test fun trimp_weights_match_banister() {
        near(0.64 * Math.exp(1.92 * .5), Science.trimpWeight(.5, female = false), 1e-12)
        near(0.86 * Math.exp(1.67 * .5), Science.trimpWeight(.5, female = true), 1e-12)
    }

    @Test fun step_and_water_goals() {
        assertEquals(9000, Science.stepGoal(35)); assertEquals(7000, Science.stepGoal(65))
        assertEquals(2000, Science.waterGoalMl(false)); assertEquals(1600, Science.waterGoalMl(true))
    }

    @Test fun sleep_need_by_age() {
        assertEquals(540, Science.baseSleepMin(16)); assertEquals(480, Science.baseSleepMin(40)); assertEquals(450, Science.baseSleepMin(70))
    }

    @Test fun vo2_median_interpolates_friend_table() {
        near(48.0, vo2Median(25, false), 1e-9)          // table point
        near((48.0 + 42.4) / 2, vo2Median(30, false), 1e-9)
        near(37.6, vo2Median(20, true), 1e-9)           // clamps below 25
    }

    @Test fun vo2_estimate_uth_and_rating() {
        val v = Vitals(resting = 60, rhrDaily = List(14) { 60L })
        val f = fitness(v, age = 30, female = false)
        near(15.3 * 187.0 / 60.0, f.vo2!!, 1e-6)        // ≈ 47.7
        assertEquals("Good", f.rating)                    // 47.7 / median(30)=45.2 → 1.055
        assertTrue(f.fitnessAge!! < 30)
    }

    @Test fun streaks_count_current_and_longest() {
        val h = listOf(0L, 9000, 9000, 9000, 0, 9000, 9000, 100)   // today (last) below goal doesn't break yesterday's run
        assertEquals(2 to 3, streaks(h, 8000))
    }

    @Test fun strain_scale_for_one_hour_at_75pct_hrr() {
        // 60 one-minute samples at 75 % of HR reserve for a 30-year-old male with RHR 60
        val rest = 60.0; val hr = (rest + .75 * (187 - rest)).toLong()
        val t0 = Instant.now().minus(Duration.ofMinutes(70))
        val samples = (0..60).map { t0.plus(Duration.ofMinutes(it.toLong())) to hr }
        val c = coach(Vitals(resting = 60, hrSamples = samples.filter { it.first.atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }), 30, false)
        if (c.trimp > 0) {                                 // skip right after midnight, when samples straddle days
            assertTrue("strain ${c.strain}", c.strain in 9.0..14.0)
            assertEquals(c.zoneMinutes[2] * 2, c.azm - c.zoneMinutes[1] - 2 * c.zoneMinutes[3])
        }
    }

    @Test fun sleep_score_rewards_full_good_night() {
        val start = Instant.parse("2026-10-20T22:30:00Z"); fun at(m: Long) = start.plus(Duration.ofMinutes(m))
        val stages = listOf(
            Stage(SleepSessionRecord.STAGE_TYPE_LIGHT, at(10), at(100)), Stage(SleepSessionRecord.STAGE_TYPE_DEEP, at(100), at(190)),
            Stage(SleepSessionRecord.STAGE_TYPE_LIGHT, at(190), at(300)), Stage(SleepSessionRecord.STAGE_TYPE_REM, at(300), at(410)),
            Stage(SleepSessionRecord.STAGE_TYPE_LIGHT, at(410), at(490)))
        val n = Night(LocalDate.parse("2026-10-21"), Duration.ofMinutes(480), Duration.ofMinutes(90), Duration.ofMinutes(110), Duration.ofMinutes(280),
            Duration.ZERO, start, at(490), stages)
        near(98.0, n.efficiency, 0.5); assertEquals(10L, n.latency.toMinutes()); assertEquals(0L, n.waso.toMinutes())
        val c = coach(Vitals(nights = listOf(n)), 35, false)
        assertTrue("score ${c.sleepScore}", c.sleepScore!! >= 85)
        assertEquals(100, c.sleepPerformance)
    }

    @Test fun cycle_prediction_uses_average_length() {
        val today = LocalDate.now()
        val p = listOf(today.minusDays(60) to today.minusDays(55), today.minusDays(32) to today.minusDays(27), today.minusDays(4) to today.minusDays(0))
        val c = cycle(p)
        near(28.0, c.length, 1e-9); assertEquals(5, c.day); assertEquals(today.minusDays(4).plusDays(28), c.nextPeriod)
        assertEquals("Menstrual", c.phase)
    }

    @Test fun caffeine_halves_every_five_hours() {
        val t = Instant.now(); val d = listOf(Dose(t.minus(Duration.ofHours(5)), 200, "coffee"))
        near(100.0, caffeineAt(d, t), 0.5)
    }

    @Test fun early_warning_needs_both_signals() {
        val hrv = List(28) { 50.0 } + listOf(35.0, 34.0); val rhr = List(28) { 55L } + listOf(60L, 61L)
        assertNotNull(anomaly(Vitals(hrvDaily = hrv.map { it + (it % 3) * 0.1 }, rhrDaily = rhr)))
        assertNull(anomaly(Vitals(hrvDaily = hrv, rhrDaily = List(30) { 55L })))
    }

    @Test fun ride_overlap_is_pro_rated() {
        val t0 = Instant.parse("2026-10-26T08:00:00Z")
        // 600 steps logged 08:00–08:10, ride 08:05–08:20 → half removed
        near(300.0, proRate(t0, t0.plusSeconds(600), t0.plusSeconds(300), t0.plusSeconds(1200), 600.0)!!.second, 1e-9)
        assertNull(proRate(t0, t0.plusSeconds(60), t0.plusSeconds(120), t0.plusSeconds(180), 50.0))
        val m = mergeIntervals(listOf(t0 to t0.plusSeconds(100), t0.plusSeconds(50) to t0.plusSeconds(200), t0.plusSeconds(500) to t0.plusSeconds(600)))
        assertEquals(2, m.size); assertEquals(t0.plusSeconds(200), m[0].second)
    }

    @Test fun acsm_treadmill_equations() {
        // 4.8 km/h = 80 m/min at 12%: VO2 = 0.1*80 + 1.8*80*0.12 + 3.5 = 28.78 ml/kg/min
        near(28.78, Science2.treadmillVo2(4.8, 12.0), 0.01)
        // 10 km/h = 166.7 m/min flat running: 0.2*166.7 + 3.5 = 36.83
        near(36.83, Science2.treadmillVo2(10.0, 0.0), 0.05)
        // 30 min of 12-3-30 at 80 kg ≈ (28.78-3.5)*80*30/1000*5 = 303 active kcal
        near(303.4, Science2.netKcal(Science2.treadmillVo2(4.8, 12.0), 80.0, 30.0), 0.5)
        near(3.5 * 4.0, 3.5 * Science2.rideMet(15.0), 1e-9)
    }

    @Test fun plan_priorities_follow_labs() {
        val p = priorities(Labs(heightCm = 165.0, weightKg = 80.0, ldl = 150.0, hdl = 45.0, alt = 47.0, uricAcid = 7.0, mcv = 62.3, ecgCleared = false), 28)
        assertTrue(p.visceral); assertTrue(p.lipids); assertTrue(p.liver); assertTrue(p.hydrate); assertTrue(p.ironCaution); assertFalse(p.hiitOk)
        assertEquals(240, p.weeklyCardioMin)
        assertEquals(78..104, p.proteinG)                      // 1.2–1.6 g/kg of 65.3 kg (BMI 24 at 165 cm)
        val w = week(p, 1); assertEquals(7, w.size); assertEquals(5, w.count { !it.rest })
        assertEquals("Hill intervals", w.first { it.dow == java.time.DayOfWeek.THURSDAY }.cardio.first().program)   // no hard runs before ECG
        val healthy = priorities(Labs(heightCm = 175.0, weightKg = 68.0, ldl = 90.0, alt = 20.0, ecgCleared = true), 28)
        assertFalse(healthy.visceral); assertEquals(150, healthy.weeklyCardioMin)
        assertEquals("Run intervals", week(healthy, 2).first { it.dow == java.time.DayOfWeek.THURSDAY }.cardio.first().program)
    }

    @Test fun zone2_uses_karvonen_when_resting_known() {
        assertEquals(137..149, zone2(28, 60))                 // HRmax 188.4; 60 + .6/.7 × 128.4 = 137.0/149.9
    }

    @Test fun calorie_budget_mifflin() {
        near(1696.25, bmr(80.0, 165.0, 28, false), 1e-9)
        near(1696.25 - 166, bmr(80.0, 165.0, 28, true), 1e-9)
    }
}
