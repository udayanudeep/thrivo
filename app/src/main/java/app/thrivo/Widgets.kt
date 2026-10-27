package app.thrivo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Latest numbers the widgets show; written whenever the app or the background refresh loads data. */
data class Snapshot(val steps: Long, val goal: Int, val water: Long, val waterGoal: Int, val km: Double)

fun saveSnapshot(ctx: Context, s: Snapshot) {
    ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).edit().putLong("w_steps", s.steps).putInt("w_goal", s.goal)
        .putLong("w_water", s.water).putInt("w_wgoal", s.waterGoal).putFloat("w_km", s.km.toFloat()).apply()
    CoroutineScope(Dispatchers.Default).launch { listOf(StepsWidget(), WaterWidget(), ComboWidget()).forEach { runCatching { it.updateAll(ctx) } } }
}

fun loadSnapshot(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).let {
    Snapshot(it.getLong("w_steps", 0), it.getInt("w_goal", 10000), it.getLong("w_water", 0), it.getInt("w_wgoal", 2500), it.getFloat("w_km", 0f).toDouble())
}

private fun metric(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE).getBoolean("metric", true)

private val Blue = ColorProvider(Color(0xFF2B1EF5)); private val White = ColorProvider(Color.White)
private val Dim = ColorProvider(Color(0xCCFFFFFF)); private val Cy = ColorProvider(Color(0xFF3FE6FF))

@Composable
private fun Bar(frac: Float, color: Color) = Row(GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp).background(Color(0x40FFFFFF))) {
    if (frac > 0) Box(GlanceModifier.width((frac.coerceIn(0f, 1f) * 130).dp).height(8.dp).cornerRadius(4.dp).background(color)) {}
}

class StepsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadSnapshot(context)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Blue).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>())) {
                Text("STEPS", style = TextStyle(color = Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text("%,d".format(s.steps), style = TextStyle(color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text("${s.steps * 100 / s.goal.coerceAtLeast(1)}% of %,d".format(s.goal), style = TextStyle(color = Dim, fontSize = 11.sp))
                Spacer(GlanceModifier.height(8.dp)); Bar(s.steps.toFloat() / s.goal, Color(0xFFFFF200))
            }
        }
    }
}

class WaterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadSnapshot(context)
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(Blue).cornerRadius(24.dp).padding(14.dp)
                .clickable(androidx.glance.appwidget.action.actionStartActivity(Intent(context, MainActivity::class.java).putExtra("tab", 1)))) {
                Text("WATER", style = TextStyle(color = Cy, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Text("${s.water * 100 / s.waterGoal.coerceAtLeast(1)}%", style = TextStyle(color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text(if (metric(context)) "%,d / %,d ml".format(s.water, s.waterGoal) else "%.0f / %.0f fl oz".format(s.water / 29.5735, s.waterGoal / 29.5735), style = TextStyle(color = Dim, fontSize = 11.sp))
                Spacer(GlanceModifier.height(8.dp)); Bar(s.water.toFloat() / s.waterGoal, Color(0xFF3FE6FF))
            }
        }
    }
}

class ComboWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = loadSnapshot(context)
        provideContent {
            Row(GlanceModifier.fillMaxSize().background(Blue).cornerRadius(24.dp).padding(16.dp).clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text("%,d".format(s.steps), style = TextStyle(color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                    Text(if (metric(context)) "STEPS · %.1f km".format(s.km) else "STEPS · %.1f mi".format(s.km / 1.609344), style = TextStyle(color = Dim, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(6.dp)); Bar(s.steps.toFloat() / s.goal, Color(0xFFFFF200))
                }
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("%,d".format(s.water), style = TextStyle(color = White, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                    Text("ML · ${s.water * 100 / s.waterGoal.coerceAtLeast(1)}% of goal", style = TextStyle(color = Cy, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(6.dp)); Bar(s.water.toFloat() / s.waterGoal, Color(0xFF3FE6FF))
                }
            }
        }
    }
}

class StepsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = StepsWidget() }
class WaterWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = WaterWidget() }
class ComboWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = ComboWidget() }

/** Every 30 min, read today's steps and water in the background (needs READ_HEALTH_DATA_IN_BACKGROUND) and refresh widgets. */
fun scheduleWidgetRefresh(ctx: Context) {
    val pi = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, WidgetRefresh::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    ctx.getSystemService(AlarmManager::class.java).setInexactRepeating(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR,
        AlarmManager.INTERVAL_HALF_HOUR, pi)
}

class WidgetRefresh : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) scheduleWidgetRefresh(ctx)
                val repo = Repo(ctx)
                withTimeoutOrNull(8000) { runCatching { repo.loadQuick() }.getOrNull() }?.let {
                    saveSnapshot(ctx, Snapshot(it.steps, repo.stepGoal, it.waterMl, repo.waterGoal, it.meters / 1000))
                }
            } finally { pending.finish() }
        }
    }
}
