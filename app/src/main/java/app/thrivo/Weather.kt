package app.thrivo

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

data class WalkSlot(val time: LocalTime, val tempC: Double, val rainPct: Int, val uv: Double, val golden: Boolean,
                    val sunrise: LocalTime, val sunset: LocalTime)

@SuppressLint("MissingPermission")
fun lastLocation(ctx: Context): Pair<Double, Double>? {
    val lm = ctx.getSystemService(LocationManager::class.java)
    return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
}

/** Picks today's best remaining hour to walk: comfortable temperature, low rain chance, UV < 6 (WHO), bonus for golden hour. */
suspend fun bestWalk(lat: Double, lon: Double): WalkSlot? = withContext(Dispatchers.IO) {
    runCatching {
        // location rounded to ~1 km before it leaves the phone
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(java.util.Locale.US, lat, lon) +
            "&hourly=temperature_2m,precipitation_probability,uv_index&daily=sunrise,sunset&timezone=auto&forecast_days=1"
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 8000; con.readTimeout = 8000
        val j = JSONObject(con.inputStream.bufferedReader().readText())
        val h = j.getJSONObject("hourly"); val d = j.getJSONObject("daily")
        val sunrise = LocalDateTime.parse(d.getJSONArray("sunrise").getString(0)).toLocalTime()
        val sunset = LocalDateTime.parse(d.getJSONArray("sunset").getString(0)).toLocalTime()
        val now = LocalTime.now()
        (0 until h.getJSONArray("time").length()).mapNotNull { i ->
            val t = LocalDateTime.parse(h.getJSONArray("time").getString(i)).toLocalTime()
            if (t.isBefore(now.withMinute(0)) || t.hour < 6 || t.hour > 21) return@mapNotNull null
            val temp = h.getJSONArray("temperature_2m").optDouble(i, 20.0)
            val rain = h.getJSONArray("precipitation_probability").optInt(i, 0)
            val uv = h.getJSONArray("uv_index").optDouble(i, 0.0)
            val golden = (t >= sunrise && t <= sunrise.plusMinutes(90)) || (t >= sunset.minusMinutes(90) && t <= sunset)
            val score = -abs(temp.coerceIn(-30.0, 50.0) - 18) * 2 - rain * .6 - (if (uv >= 6) (uv - 5) * 8 else 0.0) + (if (golden) 15 else 0)
            score to WalkSlot(t, temp, rain, uv, golden, sunrise, sunset)
        }.maxByOrNull { it.first }?.second
    }.getOrNull()
}
