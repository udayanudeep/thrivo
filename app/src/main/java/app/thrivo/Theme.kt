package app.thrivo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The app's display name — change it here and in res/values/strings.xml (app_name). */
const val APP_NAME = "Thrivo"

val AppFont = FontFamily(
    Font(R.font.space_grotesk_400, FontWeight.Normal),
    Font(R.font.space_grotesk_500, FontWeight.Medium),
    Font(R.font.space_grotesk_700, FontWeight.Bold),
)

// theme-able colours (see Look.kt applyTheme)
var Brand by mutableStateOf(Color(0xFF2B1EF5))      // main background: electric blue
var BrandDeep by mutableStateOf(Color(0xFF1E14C8))
var Section by mutableStateOf(Color(0xFF3A5BFF))     // settings section container
val Glass = Color(0x33FFFFFF)
var Faint by mutableStateOf(Color(0xFF4A5CFF))       // big faint numerals / chart track
val Cyan = Color(0xFF3FE6FF)
val Accent = Color(0xFFFFF200)   // yellow
val Orange = Color(0xFFFF6A1A)
val Coral = Color(0xFFFF5B3A)
val Ink = Color(0xFF0D0D12)
val Paper = Color(0xFFF4F4F7)
val Muted = Color(0xFF8A8A95)

@Composable
fun T(text: String, size: TextUnit = 14.sp, weight: FontWeight = FontWeight.Normal, color: Color = Color.White,
      modifier: Modifier = Modifier, lineHeight: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE) =
    Text(tr(text), modifier, color, style = TextStyle(fontFamily = AppFont, fontSize = size, fontWeight = weight, lineHeight = lineHeight,
        letterSpacing = if (size.value > 30) (-1).sp else 0.sp), maxLines = maxLines)

/** Number with a smaller unit suffix, e.g. 2,500ml */
@Composable
fun NumUnit(num: String, unit: String, size: TextUnit, color: Color = Color.White, weight: FontWeight = FontWeight.Bold) {
    Row(verticalAlignment = Alignment.Bottom) {
        T(num, size, weight, color)
        T(unit, size * .45f, FontWeight.Medium, color, Modifier.padding(bottom = (size.value * .12f).dp))
    }
}

@Composable
fun CircleBtn(icon: ImageVector, size: Dp = 40.dp, bg: Color = Glass, tint: Color = Color.White, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit) =
    Box(modifier.size(size).clip(CircleShape).background(bg).border(1.dp, Color.White.copy(.25f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, tr(label ?: icon.name.substringAfterLast(".").replace("Filled.", "")), tint = tint, modifier = Modifier.size(size * .5f)) }

/** Thrivo mark: an open progress ring with a leading dot. */
@Composable
fun BrandMark(height: Dp = 24.dp, color: Color = Color.White) {
    Canvas(Modifier.size(height, height)) {
        val w = size.height * .16f; val r = (size.height - w) / 2
        drawArc(color, 120f, 300f, false, Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
        val a = Math.toRadians(60.0)
        drawCircle(color, w * .9f, Offset(size.width / 2 + r * kotlin.math.cos(a).toFloat(), size.height / 2 + r * kotlin.math.sin(a).toFloat()))
    }
}

@Composable
fun BrandPill() = Box(Modifier.size(64.dp, 46.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(.28f)), contentAlignment = Alignment.Center) { BrandMark() }

/** Row under the hero: brand pill | ⚡ title / subtitle */
@Composable
fun Motto(title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandPill()
        Box(Modifier.padding(horizontal = 14.dp).size(1.dp, 26.dp).background(Color.White.copy(.35f)))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = Cyan, modifier = Modifier.size(16.dp))
                T(title, 16.sp, FontWeight.Bold, Cyan)
            }
            T(sub, 11.sp, FontWeight.Medium)
        }
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit) =
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(.22f)).clickable(onClick = onClick).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        T(text, 13.sp, FontWeight.Medium, Color.White.copy(.8f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(.8f), modifier = Modifier.size(18.dp))
    }

@Composable
fun DottedLine(color: Color = Color.White.copy(.3f), modifier: Modifier = Modifier) = Canvas(modifier.fillMaxWidth().height(1.dp)) {
    drawLine(color, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
}

/** Settings section: lighter blue container, small label, white list. */
@Composable
fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(22.dp)).background(Section).padding(6.dp)) {
        T(label, 12.sp, FontWeight.Medium, modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White), content = content)
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, value: String? = null, go: Boolean = false, picker: Boolean = false,
               last: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(18.dp))
        T(title, 14.sp, FontWeight.Medium, Ink, Modifier.padding(start = 10.dp).weight(1f))
        if (value != null) T(value, 13.sp, FontWeight.Medium, if (go) Brand else Ink.copy(.75f))
        when {
            trailing != null -> trailing()
            picker -> Icon(Icons.Default.UnfoldMore, null, tint = Ink.copy(.6f), modifier = Modifier.padding(start = 4.dp).size(16.dp))
            else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink, modifier = Modifier.padding(start = 2.dp).size(20.dp))
        }
    }
    if (!last) DottedLine(Ink.copy(.15f), Modifier.padding(horizontal = 16.dp))
}

/** A settings row whose whole width toggles the switch; the switch is styled for the white list background. */
@Composable
fun SwitchRow(icon: ImageVector, title: String, on: Boolean, last: Boolean = false, change: (Boolean) -> Unit) =
    SettingRow(icon, title, last = last, trailing = {
        androidx.compose.material3.Switch(on, change, colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = Color(0xFF2F6BFF), checkedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFFE3E4EC), uncheckedThumbColor = Color(0xFF8A8A95), uncheckedBorderColor = Color(0xFFC9CAD6)))
    }) { change(!on) }

/** Concentric stadium rings — running-track art. */
@Composable
fun HeroArt(modifier: Modifier, color: Color = Color(0xFF3D6BFF)) = Canvas(modifier) {
    val rings = 6; val gap = size.height / (rings * 2 + 6)
    for (i in 0 until rings) {
        val inset = i * gap * 1.05f
        drawRoundRect(color, Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            androidx.compose.ui.geometry.CornerRadius((size.height - inset * 2) / 2), style = Stroke(gap * .5f))
    }
    // a runner's dot on the inner lane
    val inner = (rings - 1) * gap * 1.05f
    drawCircle(color, gap * .55f, Offset(size.width * .72f, inner + gap * .1f))
}

fun fmtInt(n: Number) = "%,d".format(n.toLong())
