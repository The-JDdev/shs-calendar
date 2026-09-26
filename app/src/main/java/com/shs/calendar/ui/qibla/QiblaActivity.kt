package com.shs.calendar.ui.qibla

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.shs.calendar.ui.SHSBaseActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.entity.SettingsEntity
import com.shs.calendar.data.repository.SettingsRepository
import com.shs.calendar.qibla.QiblaEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * M3 Qibla screen. Bearing is always computed from the persisted location
 * via [QiblaEngine] (manual fallback = big bearing number). With a
 * magnetometer the dial rotates live from the rotation vector sensor;
 * without one the dial stays north-up. Calibration hint shown while the
 * sensor reports low accuracy.
 */
class QiblaActivity : SHSBaseActivity(), SensorEventListener {

    private val repo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }

    private lateinit var locationLabel: TextView
    private lateinit var bearingNumber: TextView
    private lateinit var distanceView: TextView
    private lateinit var compass: QiblaCompassView
    private lateinit var calibrationHint: View
    private lateinit var noSensorHint: View
    private lateinit var emptyHint: View

    private var settings = SettingsEntity()
    private var bearing = 0.0
    private var hasBearing = false
    private var hasSensor = false
    private var sensor: Sensor? = null
    private lateinit var sensorManager: SensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qibla)

        findViewById<MaterialToolbar>(R.id.qibla_toolbar)?.apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        locationLabel = findViewById(R.id.qibla_location_label)
        bearingNumber = findViewById(R.id.qibla_bearing_number)
        distanceView = findViewById(R.id.qibla_distance)
        compass = findViewById(R.id.qibla_compass)
        calibrationHint = findViewById(R.id.qibla_calibration_hint)
        noSensorHint = findViewById(R.id.qibla_no_sensor_hint)
        emptyHint = findViewById(R.id.qibla_empty_hint)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // Rotation vector fuses gyro+accel+magnetometer; game variant is
        // tilt-compensated but not north-referenced — only accept true
        // rotation vector so headings stay absolute.
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        hasSensor = sensor != null
        if (hasSensor) {
            calibrationHint.visibility = View.VISIBLE
            noSensorHint.visibility = View.GONE
        } else {
            calibrationHint.visibility = View.GONE
            noSensorHint.visibility = View.VISIBLE
        }

        lifecycleScope.launch {
            repo.observe().collectLatest { s ->
                settings = s
                render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // ---- data -------------------------------------------------------------

    private fun render() {
        val lat = settings.latitude
        val lon = settings.longitude
        if (lat == null || lon == null) {
            hasBearing = false
            locationLabel.setText(R.string.prayer_no_location)
            bearingNumber.text = "—"
            distanceView.text = ""
            emptyHint.visibility = View.VISIBLE
            return
        }
        emptyHint.visibility = View.GONE
        hasBearing = true
        bearing = QiblaEngine.bearingToKaaba(lat, lon)
        locationLabel.text = settings.locationName.ifBlank {
            String.format(Locale.US, "%.2f\u00B0, %.2f\u00B0", lat, lon)
        }
        bearingNumber.text = String.format(Locale.US, "%.1f\u00B0", bearing)
        distanceView.text = getString(
            R.string.qibla_distance_format, QiblaEngine.distanceToKaabaKm(lat, lon)
        )
        compass.visibility = View.VISIBLE
        compass.setBearing(bearing, hasSensor)
    }

    // ---- sensors ----------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val heading = Math.toDegrees(orientation[0].toDouble())
        compass.setHeading(heading)
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
        if (s?.type != Sensor.TYPE_ROTATION_VECTOR) return
        val low = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
        calibrationHint.visibility = if (low || hasSensor) View.VISIBLE else View.GONE
    }
}
