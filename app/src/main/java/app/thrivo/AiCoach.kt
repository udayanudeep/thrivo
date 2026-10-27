package app.thrivo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMsg(val user: Boolean, val text: String)

/** Compact, de-identified summary of the user's numbers that goes into the system prompt. */
fun healthSummary(d: Today, v: Vitals, age: Int, female: Boolean, stepGoal: Int): String {
    val c = coach(v, age, female); val f = fitness(v, age, female); val l = cardioLoad(v, age, female); val p = sleepProfile(v.nights)
    val n = v.nights.lastOrNull()
    return buildString {
        appendLine("Age $age, ${if (female) "female" else "male"}. Step goal $stepGoal.")
        appendLine("Today: ${d.steps} steps, ${"%.1f".format(d.meters / 1000)} km, ${d.kcal.toInt()} active kcal, ${d.waterMl} ml water.")
        appendLine("Last 7 days steps: ${d.history.takeLast(7).joinToString()}.")
        n?.let { appendLine("Last night: asleep ${fmt(it.asleep)}, deep ${fmt(it.deep)}, REM ${fmt(it.rem)}, awake ${fmt(it.awake)}, efficiency ${"%.0f".format(it.efficiency)}%.") }
        appendLine("Recovery ${c.recovery ?: "n/a"}%, strain ${"%.1f".format(c.strain)}/21, sleep score ${c.sleepScore ?: "n/a"}, sleep need tonight ${fmt(c.sleepNeed)}.")
        appendLine("HRV last 7 days (ms): ${v.hrvDaily.takeLast(7).joinToString { it?.let { x -> "%.0f".format(x) } ?: "-" }}; baseline ${c.hrvBaseline?.let { "%.0f".format(it) } ?: "n/a"}.")
        appendLine("Resting HR last 7 days: ${v.rhrDaily.takeLast(7).joinToString { it?.toString() ?: "-" }}.")
        appendLine("VO2max ${f.vo2?.let { "%.1f".format(it) } ?: "n/a"} (${f.rating}), fitness age ${f.fitnessAge ?: "n/a"}. Cardio load ratio ${l.ratio?.let { "%.2f".format(it) } ?: "n/a"} (${l.status}).")
        appendLine("Sleep regularity index ${p.sri ?: "n/a"}, chronotype ${p.chronotype}. SpO2 ${v.spo2 ?: "n/a"}, breathing ${v.resp ?: "n/a"}.")
    }
}

private val SYSTEM = """You are Thrivo Coach, a friendly, concise health and fitness coach inside the $APP_NAME app.
You get the user's own wearable data below. Ground every answer in those numbers and the research the app uses
(NSF sleep guidelines, Plews/Buchheit HRV monitoring, WHO activity guidelines, Gabbett training load).
Keep answers short and practical: 2–5 sentences or a few bullets. You are not a doctor: for symptoms, chest pain,
very abnormal readings or medical questions, recommend seeing a healthcare professional."""

suspend fun askCoach(apiKey: String, summary: String, history: List<ChatMsg>): String = withContext(Dispatchers.IO) {
    val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    val b = MessageCreateParams.builder()
        .model("claude-opus-5-5")
        .maxTokens(4000L)
        .system("$SYSTEM\n\nUSER DATA:\n$summary")
        .addBeta("server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
    history.forEach { if (it.user) b.addUserMessage(it.text) else b.addAssistantMessage(it.text) }
    try {
        val res = client.beta().messages().create(b.build())
        if (res.stopReason().orElse(null) == BetaStopReason.REFUSAL) return@withContext "I can't help with that one. Try asking about your sleep, recovery or training."
        res.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").ifBlank { "…" }
    } catch (e: UnauthorizedException) { "That API key wasn't accepted. Check it in More Settings → AI Coach."
    } catch (e: RateLimitException) { "Too many requests right now — try again in a moment."
    } catch (e: AnthropicServiceException) { "The coach is unavailable (${e.statusCode()}). Try again later."
    } catch (e: Exception) { "Couldn't reach the coach. Check your internet connection." }
    finally { client.close() }
}

@Composable
fun CoachChatScreen(apiKey: String, summary: () -> String, onSetKey: () -> Unit, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val msgs = remember { mutableStateListOf<ChatMsg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    fun send(q: String) {
        if (q.isBlank() || busy) return
        msgs += ChatMsg(true, q.trim()); input = ""; busy = true
        scope.launch { msgs += ChatMsg(false, askCoach(apiKey, summary(), msgs.toList())); busy = false; list.animateScrollToItem(msgs.size) }
    }
    Column(Modifier.fillMaxSize().background(Brand).statusBarsPadding().navigationBarsPadding().imePadding().padding(14.dp)) {
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            CircleBtn(Icons.AutoMirrored.Filled.ArrowBack, 36.dp, label = "Back", onClick = back)
            T("Thrivo Coach", 14.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        }
        if (apiKey.isBlank()) {
            T("Ask about your data", 32.sp, FontWeight.Bold)
            T("Thrivo Coach uses Claude by Anthropic. Add your own Anthropic API key to turn it on. When you ask a question, a summary of your " +
              "steps, sleep, heart and recovery numbers is sent to Anthropic's API to answer it.", 13.sp, color = Color.White.copy(.85f), lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 12.dp))
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onSetKey), contentAlignment = Alignment.Center) {
                T("Add API key", 15.sp, FontWeight.Bold, Brand) }
            return@Column
        }
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (msgs.isEmpty()) item {
                T("Ask me anything about your data", 24.sp, FontWeight.Bold, lineHeight = 28.sp)
                Spacer(Modifier.height(10.dp))
                listOf("Why is my recovery low today?", "How can I sleep better this week?", "Should I train hard today?", "How do I raise my VO₂max?").forEach { q ->
                    T(q, 13.sp, FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp).clip(RoundedCornerShape(50)).background(Glass).clickable { send(q) }
                        .padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.user) Arrangement.End else Arrangement.Start) {
                    T(m.text, 14.sp, color = if (m.user) Color.White else Ink, lineHeight = 19.sp, modifier = Modifier.widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(18.dp)).background(if (m.user) Color.White.copy(.22f) else Color.White).padding(12.dp))
                }
            }
            if (busy) item { T("Thrivo Coach is thinking…", 12.sp, color = Color.White.copy(.7f)) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ask Thrivo Coach…") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Ink, unfocusedTextColor = Ink))
            IconButton({ send(input) }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Brand) }
        }
        T("AI answers can be wrong and aren't medical advice.", 9.sp, color = Color.White.copy(.6f), modifier = Modifier.padding(top = 4.dp))
    }
}
