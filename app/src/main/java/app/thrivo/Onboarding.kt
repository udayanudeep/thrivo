package app.thrivo

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.LocalDate

@Composable
fun Onboarding(s: SettingsState, available: Boolean, connect: () -> Unit, done: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(s.name) }
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().navigationBarsPadding().imePadding().padding(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(if (it <= step) Color.White else Color.White.copy(.25f))) }
        }
        Spacer(Modifier.height(20.dp))
        when (step) {
            0 -> {
                HeroArt(Modifier.fillMaxWidth().height(220.dp))
                Spacer(Modifier.height(28.dp))
                T("every step", 56.sp, FontWeight.Bold, Accent, lineHeight = 58.sp); T("counts", 56.sp, FontWeight.Bold, lineHeight = 58.sp)
                T("Steps, water, sleep and recovery — powered by your Fitbit and real research.", 16.sp, FontWeight.Medium, lineHeight = 21.sp,
                    modifier = Modifier.padding(top = 12.dp))
            }
            1 -> {
                T("What should\nwe call you?", 40.sp, FontWeight.Bold, lineHeight = 42.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(name, { name = it.take(20) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(.5f), cursorColor = Color.White))
            }
            2 -> {
                T("About you", 40.sp, FontWeight.Bold)
                T("Used for heart-rate zones, sleep need and fitness norms. Stays on your phone.", 13.sp, color = Color.White.copy(.8f), lineHeight = 17.sp)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { NumUnit("${s.age}", " years", 44.sp) }
                    Stepper({ s.age = (s.age - 1).coerceAtLeast(13); s.repo.age = s.age }, { s.age = (s.age + 1).coerceAtMost(99); s.repo.age = s.age })
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false to "Male", true to "Female").forEach { (f, l) ->
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50)).background(if (s.female == f) Color.White else Glass)
                            .clickable { s.female = f; s.repo.female = f }, contentAlignment = Alignment.Center) {
                            T(l, 15.sp, FontWeight.Bold, if (s.female == f) Brand else Color.White) }
                    }
                }
            }
            else -> {
                val sg = Science.stepGoal(s.age); val wg = Science.waterGoalMl(s.female)
                T("Your goals", 40.sp, FontWeight.Bold)
                T("Suggested from research — change them any time.", 13.sp, color = Color.White.copy(.8f))
                Spacer(Modifier.height(20.dp))
                GoalTile(fmtInt(sg), "Daily steps · Paluch 2022", Modifier.fillMaxWidth()) {}
                Spacer(Modifier.height(10.dp))
                GoalTile(vol(wg), "Water from drinks · EFSA 2010", Modifier.fillMaxWidth()) {}
                Spacer(Modifier.height(10.dp))
                GoalTile("${Science.baseSleepMin(s.age) / 60}h${if (Science.baseSleepMin(s.age) % 60 > 0) " 30m" else ""}", "Sleep · National Sleep Foundation 2015", Modifier.fillMaxWidth()) {}
                LaunchedEffect(Unit) { s.stepGoal = sg; s.repo.stepGoal = sg; s.waterGoal = wg; s.repo.waterGoal = wg }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable {
            if (step == 1 && name.isNotBlank()) { s.name = name.trim(); s.repo.name = s.name }
            if (step < 3) step++ else { s.repo.onboarded = true; done(); connect() }
        }, contentAlignment = Alignment.Center) {
            T(if (step < 3) "Continue" else if (available) "Connect Fitbit" else "Get Health Connect", 16.sp, FontWeight.Bold, Brand)
        }
    }
}

// ---------------- Export & backup ----------------
fun exportCsv(d: Today, v: Vitals): String = buildString {
    appendLine("date,steps,distance_km,water_ml,sleep_min,deep_min,rem_min,hrv_ms,resting_hr")
    val nights = v.nights.associateBy { it.date }
    for (i in 0..364) {
        val date = LocalDate.now().minusDays(364L - i); val back30 = 29 - (364 - i)
        val n = nights[date]
        append("$date,${d.yearSteps[i]},${"%.2f".format(java.util.Locale.US, d.yearMeters[i] / 1000)},")
        append("${if (back30 >= 0) d.water30[back30] else ""},${n?.asleep?.toMinutes() ?: ""},${n?.deep?.toMinutes() ?: ""},${n?.rem?.toMinutes() ?: ""},")
        appendLine("${if (back30 >= 0) v.hrvDaily.getOrNull(back30)?.let { "%.1f".format(java.util.Locale.US, it) } ?: "" else ""},${if (back30 >= 0) v.rhrDaily.getOrNull(back30) ?: "" else ""}")
    }
}

/** All app settings, journal and shoes — never the API key. */
fun exportBackup(ctx: Context): String {
    val o = JSONObject()
    ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).all.forEach { (k, v) -> if (k != "apiKey" && !k.startsWith("w_")) o.put(k, JSONObject().put("t", v?.javaClass?.simpleName).put("v", v)) }
    return o.put("_app", "thrivo").put("_version", 1).toString(2)
}

fun importBackup(ctx: Context, json: String): Boolean = runCatching {
    val o = JSONObject(json); require(o.optString("_app") == "thrivo")
    val e = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).edit()
    o.keys().forEach { k -> if (!k.startsWith("_") && k != "apiKey") { val x = o.getJSONObject(k)
        when (x.getString("t")) { "Boolean" -> e.putBoolean(k, x.getBoolean("v")); "Integer" -> e.putInt(k, x.getInt("v")); "Long" -> e.putLong(k, x.getLong("v"))
            "Float" -> e.putFloat(k, x.getDouble("v").toFloat()); else -> e.putString(k, x.getString("v")) } } }
    e.apply(); true
}.getOrDefault(false)
