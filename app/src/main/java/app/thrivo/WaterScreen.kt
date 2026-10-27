package app.thrivo

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

val BOTTLES = listOf("Flat Bottle", "Classic", "Sport", "Tumbler", "Flip Top", "Steel")
private val AMOUNTS = (1..20).map { it * 50 }
private val LABELED = setOf(50, 150, 250, 350, 450, 600, 700, 800, 900, 1000)

@Composable
fun WaterScreen(d: Today, goal: Int, glass: Int, remaining: Boolean, bottle: Int,
                onGlass: (Int) -> Unit, onAdd: () -> Unit, onGoal: () -> Unit, onBottles: () -> Unit) {
    val pct = (d.waterMl * 100 / goal.coerceAtLeast(1)).toInt()
    val fill = if (remaining) 1f - pct / 100f else pct / 100f
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) { Spacer(Modifier.weight(1f)); CircleBtn(Icons.Default.Waves, label = "Water goal and reminders", onClick = onGoal) }
        Box(Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = "Water $pct percent of goal" }) {
            Bottle(bottle, fill.coerceIn(0f, 1f), Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp).width(118.dp).fillMaxHeight(.78f))
            Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
                Chip(BOTTLES[bottle], onBottles)
                Spacer(Modifier.height(20.dp))
                T("$pct%", 104.sp, FontWeight.Bold, if (pct == 0) Faint else Coral, lineHeight = 104.sp, maxLines = 1)
                T("Completed", 15.sp, FontWeight.Medium)
            }
        }
        Motto(if (pct >= 100) "Goal reached!" else "Stay Hydrated!", if (pct >= 100) "Nicely done today." else "Keep the water flowing.")
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().padding(bottom = 96.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFFF2F3F7)).padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { NumUnit(volNum(glass), volUnit, 44.sp, Ink) }
                Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onAdd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Ink, modifier = Modifier.size(18.dp)); T("Add", 15.sp, FontWeight.Bold, Ink)
                }
            }
            Spacer(Modifier.height(10.dp))
            AmountPicker(glass, onGlass)
        }
    }
}

/** Circular gauge with an animated wave at the water level. */
@Composable
fun WaveGauge(level: Float, modifier: Modifier) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase")
    val shown by animateFloatAsState(level, tween(900), label = "level")
    Canvas(modifier) {
        val r = size.minDimension / 2; val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.White.copy(.08f), r, c)
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r - 10f)) }
        clipPath(circle) {
            val y0 = c.y + r - 2 * r * shown
            listOf(0f to Cyan.copy(.45f), 1.6f to Cyan.copy(.85f)).forEach { (shift, col) ->
                val wave = Path().apply {
                    moveTo(0f, size.height); lineTo(0f, y0)
                    var x = 0f
                    while (x <= size.width) { lineTo(x, y0 + kotlin.math.sin(x / size.width * 4 * Math.PI.toFloat() + phase + shift) * r * .05f); x += 6f }
                    lineTo(size.width, size.height); close()
                }
                drawPath(wave, col)
            }
        }
        drawCircle(Color.White.copy(.35f), r - 4f, c, style = Stroke(6f))
    }
}

@Composable
fun AmountPicker(value: Int, onChange: (Int) -> Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    fun pick(x: Float) = onChange(AMOUNTS[((x / width) * AMOUNTS.size).toInt().coerceIn(0, AMOUNTS.lastIndex)])
    Column {
        Row(Modifier.fillMaxWidth().height(42.dp)
            .pointerInput(Unit) { width = size.width.toFloat(); detectTapGestures { pick(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> pick(ch.position.x) } },
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            AMOUNTS.forEachIndexed { i, a ->
                Box(Modifier.weight(1f).height((12 + i * 1.6f).dp).clip(RoundedCornerShape(50)).background(if (a <= value) Ink else Color(0xFFE3E3E8)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AMOUNTS.forEach { a ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (a in LABELED) {
                        Box(Modifier.size(1.dp, 5.dp).background(Muted))
                        T(if (!Metric) "%.0f".format(a / 29.5735) else if (a == 1000) "1,000" else "$a", 7.sp, color = Muted, maxLines = 1, modifier = Modifier.wrapContentWidth(unbounded = true))
                    }
                }
            }
        }
    }
}

@Composable
fun Bottle(style: Int, fill: Float, modifier: Modifier) = Canvas(modifier) { drawBottle(style, fill) }

/** [dark] = drawn on a dark background (Focus Mode, My Bottles): caps get a lighter shade so they stay visible. */
fun DrawScope.drawBottle(style: Int, fill: Float, dark: Boolean = Brand.luminance() < .1f) {
    val w = size.width; val h = size.height
    val glass = Color(0xFF8C8CFF).copy(.55f); val water = Color(0xFF6F6BFF); val edge = Color.White.copy(.55f)
    val Ink = if (dark) Color(0xFF4B4B60) else app.thrivo.Ink   // cap colour
    // caps always get a thin light outline so their shape reads on any background
    fun capRect(c: Color, o: Offset, sz: Size, r: Float) {
        drawRoundRect(c, o, sz, CornerRadius(r)); drawRoundRect(Color.White.copy(if (dark) .55f else .3f), o, sz, CornerRadius(r), style = Stroke(2f))
    }
    val capH = h * .1f
    val body: Path; val bodyTop: Float
    when (style) {
        0 -> { // flat memo bottle: rectangle, small cap
            capRect(Ink, Offset(w * .14f, 0f), Size(w * .34f, capH), 4f)
            bodyTop = capH; body = Path().apply { addRoundRect(RoundRect(0f, bodyTop, w, h, CornerRadius(w * .06f))) } }
        1 -> { // classic round bottle with neck
            capRect(Ink, Offset(w * .3f, 0f), Size(w * .4f, capH), 6f)
            bodyTop = capH; body = Path().apply {
                moveTo(w * .32f, bodyTop); lineTo(w * .68f, bodyTop); cubicTo(w * .7f, h * .2f, w, h * .2f, w, h * .3f)
                lineTo(w, h - 20f); quadraticTo(w, h, w - 20f, h); lineTo(20f, h); quadraticTo(0f, h, 0f, h - 20f)
                lineTo(0f, h * .3f); cubicTo(0f, h * .2f, w * .3f, h * .2f, w * .32f, bodyTop); close() } }
        3 -> { // tumbler with handle, tapering
            capRect(Ink, Offset(0f, 0f), Size(w * .82f, capH), 8f)
            drawRoundRect(edge, Offset(w * .74f, h * .2f), Size(w * .26f, h * .3f), CornerRadius(20f), style = Stroke(w * .06f))
            bodyTop = capH; body = Path().apply { moveTo(0f, bodyTop); lineTo(w * .82f, bodyTop); lineTo(w * .7f, h); lineTo(w * .12f, h); close() } }
        else -> { // sport / flip / steel: tall rounded cylinder
            val cap = if (style == 5) Color(0xFF9AA0B5) else Ink
            capRect(cap, Offset(w * .08f, 0f), Size(w * .84f, capH * 1.2f), 14f)
            if (style == 4) drawRoundRect(Color(0xFFFFC21A), Offset(w * .8f, capH * .3f), Size(w * .14f, h * .35f), CornerRadius(12f))
            if (style == 2) capRect(Ink, Offset(w * .35f, 0f), Size(w * .3f, capH * .5f), 6f)
            bodyTop = capH * 1.2f; body = Path().apply { addRoundRect(RoundRect(w * .06f, bodyTop, w * .94f, h, CornerRadius(w * .18f))) } }
    }
    drawPath(body, glass.copy(.25f))
    clipPath(body) {
        val top = h - (h - bodyTop) * fill
        drawRect(Brush.verticalGradient(listOf(water, water.copy(.8f)), top, h), Offset(0f, top), Size(w, h - top))
        drawRect(Color.White.copy(.18f), Offset(w * .08f, bodyTop), Size(w * .08f, h - bodyTop))
    }
    drawPath(body, edge, style = Stroke(2.5f))
}

/** Water Intake goal sheet (dark → electric glow). */
@Composable
fun WaterGoalSheet(goal: Int, female: Boolean, reminders: Boolean, onGoal: (Int) -> Unit, onReminders: (Boolean) -> Unit, close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black, Color.Black, BrandDeep, Brand)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            Row { Spacer(Modifier.weight(1f)); CircleBtn(Icons.Default.Close, 36.dp, label = "Close", onClick = close) }
            Spacer(Modifier.height(10.dp))
            Icon(Icons.Default.LocalDrink, null, tint = Color(0xFF3D6BFF), modifier = Modifier.align(Alignment.CenterHorizontally).size(90.dp))
            Spacer(Modifier.height(30.dp))
            T("Water Intake", 32.sp, FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { NumUnit(volNum(goal), volUnit, 40.sp) }
                Stepper({ onGoal((goal - 250).coerceAtLeast(500)) }, { onGoal((goal + 250).coerceAtMost(6000)) })
            }
            Spacer(Modifier.height(10.dp))
            T("EFSA (2010) adequate intake is ${if (female) "2.0" else "2.5"} L of total water a day, about 80% of it from drinks — " +
              "so ~${fmtInt(Science.waterGoalMl(female))} ml from drinks, more on hot or active days.", 12.sp, color = Color.White.copy(.75f), lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.15f)).clickable { onGoal(Science.waterGoalMl(female)) }
                .padding(horizontal = 16.dp, vertical = 8.dp)) { T("Use ${vol(Science.waterGoalMl(female))}", 13.sp, FontWeight.Bold) }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f))
                .border(1.dp, Color.White.copy(.3f), RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    T("Reminders", 14.sp, FontWeight.Medium); T("Stay on track with regular sips.", 11.sp, color = Color.White.copy(.75f))
                }
                GoSwitch(reminders, onReminders)
            }
            Spacer(Modifier.weight(1f))
            DoneButton(close, Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun Stepper(minus: () -> Unit, plus: () -> Unit) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.15f)).border(1.dp, Color.White.copy(.3f), RoundedCornerShape(50))) {
        Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.clickable(onClick = minus).padding(horizontal = 12.dp, vertical = 6.dp).size(16.dp))
        Box(Modifier.size(1.dp, 28.dp).background(Color.White.copy(.3f)))
        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.clickable(onClick = plus).padding(horizontal = 12.dp, vertical = 6.dp).size(16.dp))
    }

@Composable
fun GoSwitch(on: Boolean, change: (Boolean) -> Unit) = Switch(on, change, colors = SwitchDefaults.colors(
    checkedTrackColor = Color(0xFF2F6BFF), checkedThumbColor = Color.White, uncheckedTrackColor = Color.White.copy(.3f),
    uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent))

@Composable
fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Done") =
    Box(modifier.width(150.dp).height(54.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCFCFD6)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { T(label, 16.sp, FontWeight.Bold, Brand) }

/** My Bottles picker. */
@Composable
fun BottlesScreen(selected: Int, pick: (Int) -> Unit, back: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black, Color.Black, BrandDeep, Brand)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Box(Modifier.fillMaxWidth()) {
                CircleBtn(Icons.AutoMirrored.Filled.ArrowBack, 36.dp, label = "Back", onClick = back)
                T("My Bottles", 14.sp, FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.height(30.dp))
            Canvas(Modifier.align(Alignment.CenterHorizontally).size(76.dp, 170.dp)) { drawBottle(selected, 0f, dark = true) }
            Spacer(Modifier.height(20.dp))
            T(BOTTLES[selected], 34.sp, FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(16.dp)); DottedLine(); Spacer(Modifier.height(16.dp))
            BOTTLES.indices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { i ->
                        Box(Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.25f))
                            .clickable { pick(i) }, contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(26.dp, 64.dp)) { drawBottle(i, .5f) }
                            if (i == selected) Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(.9f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, null, tint = Brand, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        }
    }
}
