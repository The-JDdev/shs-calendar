package com.shs.calendar.location

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.shs.calendar.ui.SHSBaseActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.shs.calendar.R
import com.shs.calendar.data.CalendarDatabase
import com.shs.calendar.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual location picker (M1): searchable offline city catalog plus free-text
 * lat/lon entry. Optional "use current location" GPS path with a rationale
 * dialog — denying it never blocks the manual flow.
 */
class LocationPickerActivity : SHSBaseActivity() {

    private val repo: SettingsRepository by lazy {
        SettingsRepository(CalendarDatabase.get(this))
    }
    private val locationRepo: LocationRepository by lazy {
        LocationRepository(this)
    }

    private lateinit var list: ListView
    private lateinit var empty: TextView
    private lateinit var search: EditText
    private var results: List<City> = CityCatalog.all

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) fetchGps() else {
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_picker)

        findViewById<MaterialToolbar>(R.id.picker_toolbar).apply {
            setNavigationOnClickListener { finish() }
        }
        list = findViewById(R.id.picker_city_list)
        empty = findViewById(R.id.picker_empty)
        search = findViewById(R.id.picker_search)

        render(CityCatalog.all)
        list.setOnItemClickListener { _, _, position, _ ->
            val picked = results[position]
            val near = CityCatalog.nearest(picked.latitude, picked.longitude)
            val resolved = near?.let {
                ResolvedLocation(it.latitude, it.longitude, "${it.name}, ${it.country}", it.timeZone)
            } ?: ResolvedLocation(picked.latitude, picked.longitude, "${picked.name}, ${picked.country}", picked.timeZone)
            saveResolved(resolved)
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                render(CityCatalog.search(s?.toString() ?: ""))
            }
        })

        findViewById<MaterialButton>(R.id.picker_use_gps).setOnClickListener {
            when {
                locationRepo.hasPermission() -> fetchGps()
                else -> showRationale()
            }
        }
        findViewById<MaterialButton>(R.id.picker_save).setOnClickListener { saveManual() }
    }

    private fun render(cities: List<City>) {
        results = cities
        list.visibility = if (cities.isEmpty()) View.GONE else View.VISIBLE
        empty.visibility = if (cities.isEmpty()) View.VISIBLE else View.GONE
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            cities.map { "${it.name} — ${it.country}" }
        )
    }

    private fun showRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.location_permission_rationale_title)
            .setMessage(R.string.location_permission_rationale)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ---- GPS path (optional; deny/off never blocks manual flow) -----------

    private fun fetchGps() {
        if (!locationRepo.providersEnabled()) {
            Toast.makeText(this, R.string.location_gps_disabled, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            val fix = withContext(Dispatchers.IO) { locationRepo.current() }
            if (fix != null) saveResolved(fix)
            else Toast.makeText(this@LocationPickerActivity, R.string.location_fix_failed, Toast.LENGTH_LONG).show()
        }
    }

    // ---- persistence ------------------------------------------------------

    private fun saveManual() {
        val latText = findViewById<EditText>(R.id.picker_latitude).text.toString().trim()
        val lonText = findViewById<EditText>(R.id.picker_longitude).text.toString().trim()
        val lat = latText.toDoubleOrNull()
        val lon = lonText.toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            Toast.makeText(this, R.string.location_invalid_coords, Toast.LENGTH_LONG).show()
            return
        }
        val resolved = locationRepo.resolve(lat, lon)
        saveResolved(resolved)
    }

    private fun saveResolved(resolved: ResolvedLocation) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repo.update { s ->
                    s.copy(
                        latitude = resolved.latitude,
                        longitude = resolved.longitude,
                        locationName = resolved.label,
                        timezone = resolved.timeZone
                    )
                }
            }
            Toast.makeText(this@LocationPickerActivity, R.string.location_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
