package app.thrivo

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Serverless friend challenges: each person shares a short code with their week's daily steps;
 * friends paste it to update the leaderboard. (A live leaderboard would need a backend such as Firebase.)
 */
const val WEEK_CHALLENGE = 70_000
fun weekStart(): LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun myCode(name: String, d: Today): String {
    val days = (java.time.temporal.ChronoUnit.DAYS.between(weekStart(), LocalDate.now()) + 1).toInt()
    val o = JSONObject().put("n", name).put("w", weekStart().toString()).put("s", JSONArray(d.yearSteps.takeLast(days)))
    return "THRIVO:" + Base64.encodeToString(o.toString().toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
}

data class Friend(val name: String, val week: LocalDate, val steps: List<Long>) { val total get() = steps.sum() }

fun parseCode(code: String): Friend? = runCatching {
    val raw = code.trim().substringAfter("THRIVO:").trim()
    val o = JSONObject(String(Base64.decode(raw, Base64.NO_WRAP or Base64.URL_SAFE)))
    val a = o.getJSONArray("s"); Friend(o.getString("n").take(20), LocalDate.parse(o.getString("w")), (0 until a.length()).map { a.getLong(it) })
}.getOrNull()

fun friends(ctx: Context): List<Friend> = runCatching {
    val a = JSONArray(ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).getString("friends", "[]"))
    (0 until a.length()).mapNotNull { parseCode(a.getString(it)) }
}.getOrDefault(emptyList())

fun saveFriendCode(ctx: Context, code: String) {
    val f = parseCode(code) ?: return
    val p = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)
    val kept = runCatching { JSONArray(p.getString("friends", "[]")) }.getOrDefault(JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) } }
        .filter { parseCode(it)?.name != f.name }
    p.edit().putString("friends", JSONArray(kept + code.trim()).toString()).apply()
}

fun removeFriend(ctx: Context, name: String) {
    val p = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)
    val kept = runCatching { JSONArray(p.getString("friends", "[]")) }.getOrDefault(JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) } }
        .filter { parseCode(it)?.name != name }
    p.edit().putString("friends", JSONArray(kept).toString()).apply()
}

@Composable
fun ChallengesScreen(name: String, d: Today, back: () -> Unit) = SubScreen("Challenges", back) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(friends(ctx)) }
    var adding by remember { mutableStateOf(false) }
    val me = Friend("$name (you)", weekStart(), d.yearSteps.takeLast((java.time.temporal.ChronoUnit.DAYS.between(weekStart(), LocalDate.now()) + 1).toInt()))
    val board = (list.filter { it.week == weekStart() } + me).sortedByDescending { it.total }
    T("Weekly challenge", 32.sp, FontWeight.Bold)
    T("Walk ${fmtInt(WEEK_CHALLENGE)} steps Mon–Sun. Swap codes with friends to fill the leaderboard.", 12.sp, color = Color.White.copy(.8f), lineHeight = 16.sp)
    Spacer(Modifier.height(12.dp))
    WhiteCard {
        board.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("${i + 1}", 18.sp, FontWeight.Bold, if (i == 0) Orange else Muted, Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    T(f.name, 15.sp, FontWeight.Bold, Ink)
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFE6E8F0))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((f.total.toFloat() / WEEK_CHALLENGE).coerceIn(0f, 1f)).clip(RoundedCornerShape(50))
                            .background(if (f.total >= WEEK_CHALLENGE) Accent else Brand)) }
                }
                T("  ${fmtInt(f.total)}", 14.sp, FontWeight.Bold, Ink)
                if (!f.name.endsWith("(you)")) IconButton({ removeFriend(ctx, f.name); list = friends(ctx) }) { Icon(Icons.Default.Delete, null, tint = Muted) }
            }
        }
        val stale = list.count { it.week != weekStart() }
        if (stale > 0) T("$stale friend code(s) are from an earlier week — ask for a fresh one.", 11.sp, color = Muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable {
            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                "Join my ${APP_NAME} weekly step challenge! Paste this in ${APP_NAME} → Challenges:\n${myCode(name, d)}"), null))
        }, contentAlignment = Alignment.Center) { T("Share my code", 14.sp, FontWeight.Bold, Brand) }
        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(50)).background(Glass).clickable { adding = true }, contentAlignment = Alignment.Center) {
            T("Add friend", 14.sp, FontWeight.Bold) }
    }
    if (adding) {
        var code by remember { mutableStateOf(ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
            ?.let { Regex("THRIVO:[A-Za-z0-9_=-]+").find(it)?.value } ?: "") }
        AlertDialog({ adding = false }, containerColor = Color.White, title = { T("Add a friend's code", 20.sp, FontWeight.Bold, Ink) },
            text = { OutlinedTextField(code, { code = it }, label = { Text("THRIVO:…") }) },
            confirmButton = { TextButton({ if (parseCode(code) != null) { saveFriendCode(ctx, code); list = friends(ctx) }; adding = false }) {
                T("Add", 16.sp, FontWeight.Bold, Brand) } })
    }
}
