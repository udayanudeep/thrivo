package app.thrivo

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationResult
import java.time.Instant

/**
 * Speed-based ride detection. Walking is ~4–6 km/h and recreational running ~8–12 km/h (Bohannon 1997; Tudor-Locke 2018),
 * while riding a bike, scooter, car or bus is usually well above that. Two consecutive location samples at ≥ 13 km/h
 * mark a ride, which then ends when speed drops back. Uses low-power location (Wi-Fi/cell) every 1–2 minutes.
 */
object SpeedRides {
    const val RIDE_KMH = 13.0
    private fun p(ctx: Context) = ctx.getSharedPreferences("thrivo", Context.MODE_PRIVATE)

    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 8, Intent(ctx, SpeedReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    @SuppressLint("MissingPermission")
    fun register(ctx: Context, on: Boolean) {
        val lm = ctx.getSystemService(android.location.LocationManager::class.java)
        runCatching { lm.removeUpdates(pi(ctx)) }
        if (!on || !granted(ctx)) return
        // low-power fused fixes every ~1 min, plus any fixes other apps request (passive, free)
        val provider = if (android.os.Build.VERSION.SDK_INT >= 31 && lm.hasProvider(android.location.LocationManager.FUSED_PROVIDER))
            android.location.LocationManager.FUSED_PROVIDER else android.location.LocationManager.NETWORK_PROVIDER
        runCatching { lm.requestLocationUpdates(provider, 60_000L, 0f, pi(ctx)) }.onFailure { android.util.Log.w("Thrivo", "speed: $provider failed", it) }
        runCatching { lm.requestLocationUpdates(android.location.LocationManager.PASSIVE_PROVIDER, 30_000L, 0f, pi(ctx)) }
        android.util.Log.i("Thrivo", "speed rides: listening via $provider + passive")
    }

    /** Feed one fix: returns the speed in km/h used for the decision (null if the fix was too rough). */
    fun onFix(ctx: Context, t: Long, lat: Double, lon: Double, acc: Float, reportedMps: Float?): Double? {
        val prefs = p(ctx)
        val lastT = prefs.getLong("sp_t", 0); val lastLat = prefs.getFloat("sp_lat", 0f).toDouble(); val lastLon = prefs.getFloat("sp_lon", 0f).toDouble()
        val lastAcc = prefs.getFloat("sp_acc", 9999f)
        prefs.edit().putLong("sp_t", t).putFloat("sp_lat", lat.toFloat()).putFloat("sp_lon", lon.toFloat()).putFloat("sp_acc", acc).apply()
        val kmh = when {
            reportedMps != null && acc < 50 -> reportedMps * 3.6                                     // GPS-quality fix with Doppler speed
            lastT > 0 && t - lastT in 30_000..600_000 -> {
                val d = FloatArray(1); android.location.Location.distanceBetween(lastLat, lastLon, lat, lon, d)
                if (d[0] < 2 * (acc + lastAcc)) 0.0 else d[0] / ((t - lastT) / 1000.0) * 3.6      // ignore moves inside the error circles
            }
            else -> return null
        }
        val fastStreak = if (kmh >= RIDE_KMH) prefs.getInt("sp_fast", 0) + 1 else 0
        prefs.edit().putInt("sp_fast", fastStreak).apply()
        val at = Instant.ofEpochMilli(t)
        if (fastStreak == 2) Rides.begin(ctx, Instant.ofEpochMilli(if (lastT > 0) lastT else t), "speed")
        if (fastStreak == 0 && Rides.list(ctx).lastOrNull()?.let { it.end == null && it.type == "speed" } == true) Rides.finish(ctx, at)
        return kmh
    }
}

class SpeedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val fixes = LocationResult.extractResult(intent)?.locations
            ?: listOfNotNull(intent.getParcelableExtra<android.location.Location>(android.location.LocationManager.KEY_LOCATION_CHANGED))
        fixes.sortedBy { it.time }.forEach { l ->
            val kmh = SpeedRides.onFix(ctx, l.time, l.latitude, l.longitude, l.accuracy, if (l.hasSpeed()) l.speed else null)
            android.util.Log.d("Thrivo", "speed fix ${l.provider} acc=${l.accuracy} → ${kmh?.let { "%.1f km/h".format(it) } ?: "skipped"}")
        }
    }
}
