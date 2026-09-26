package com.shs.calendar.ui.tasbih

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.shs.calendar.R

/**
 * M3 Tasbih counter: preset dhikr list, custom targets, vibration tick,
 * count persisted in SharedPreferences so a restart never loses progress.
 */
class TasbihActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private lateinit var dhikrName: TextView
    private lateinit var countView: TextView
    private lateinit var progressView: TextView
    private lateinit var presetSpinner: Spinner
    private lateinit var targetSpinner: Spinner

    private var count = 0
    private var target = 33
    private var presetIndex = 0

    /** "Custom…" entry appended after the fixed target presets. */
    private val fixedTargets = intArrayOf(33, 99, 100, 500, 1000)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tasbih)

        findViewById<MaterialToolbar>(R.id.tasbih_toolbar)?.apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        dhikrName = findViewById(R.id.tasbih_dhikr_name)
        countView = findViewById(R.id.tasbih_count)
        progressView = findViewById(R.id.tasbih_progress)
        presetSpinner = findViewById(R.id.tasbih_preset_spinner)
        targetSpinner = findViewById(R.id.tasbih_target_spinner)

        count = prefs.getInt(KEY_COUNT, 0).coerceAtLeast(0)
        target = prefs.getInt(KEY_TARGET, 33).coerceIn(1, 100_000)
        presetIndex = prefs.getInt(KEY_PRESET, 0)

        setupPresets()
        setupTargets()

        findViewById<View>(R.id.tasbih_tap_area)?.setOnClickListener { increment() }
        findViewById<MaterialButton>(R.id.tasbih_reset)?.setOnClickListener {
            count = 0
            persist()
            render()
        }

        render()
    }

    // ---- setup -------------------------------------------------------------

    private fun setupPresets() {
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.tasbih_presets, android.R.layout.simple_spinner_dropdown_item
        )
        presetSpinner.adapter = adapter
        presetSpinner.setSelection(presetIndex.coerceIn(0, adapter.count - 1))
        presetSpinner.onItemSelectedListener = simpleSelect { pos ->
            presetIndex = pos
            persist()
            render()
        }
    }

    private fun setupTargets() {
        val labels = fixedTargets.map { it.toString() } +
            getString(R.string.tasbih_target_custom)
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, labels
        )
        targetSpinner.adapter = adapter
        val fixedPos = fixedTargets.indexOf(target)
        targetSpinner.setSelection(if (fixedPos >= 0) fixedPos else labels.size - 1)
        // Attach after first layout: Spinner fires an initial onItemSelected
        // and a legacy custom target must not pop the dialog on open.
        targetSpinner.post {
            targetSpinner.onItemSelectedListener = simpleSelect { pos ->
                if (pos < fixedTargets.size) {
                    target = fixedTargets[pos]
                    persist()
                    render()
                } else {
                    promptCustomTarget()
                }
            }
        }
    }

    private fun simpleSelect(onSelect: (Int) -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long
            ) = onSelect(position)

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

    private fun promptCustomTarget() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(target.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.tasbih_target_custom_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = input.text.toString().toIntOrNull()
                if (v != null && v in 1..100_000) {
                    target = v
                    persist()
                    render()
                } else {
                    targetSpinner.setSelection(fixedTargets.indexOf(33).coerceAtLeast(0))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- counting ----------------------------------------------------------

    private fun increment() {
        count++
        val cycleDone = count % target == 0
        persist()
        render()
        vibrate(if (cycleDone) LONG_TICK else SHORT_TICK, cycleDone)
    }

    private fun persist() {
        prefs.edit()
            .putInt(KEY_COUNT, count)
            .putInt(KEY_TARGET, target)
            .putInt(KEY_PRESET, presetIndex)
            .apply()
    }

    private fun render() {
        countView.text = count.toString()
        val cycle = if (count == 0) 0 else ((count - 1) % target) + 1
        progressView.text = getString(R.string.tasbih_progress_format, cycle, target)
        dhikrName.text = resources.getStringArray(R.array.tasbih_presets)
            .getOrElse(presetIndex) { "" }
    }

    // ---- haptics -----------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long, strong: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE)
                as android.os.VibratorManager
            manager.defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26) {
            if (strong) {
                val pattern = longArrayOf(0, 60, 50, 90)
                vibrator.vibrate(
                    VibrationEffect.createWaveform(pattern, -1)
                )
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, 90))
            }
        } else {
            if (strong) vibrator.vibrate(longArrayOf(0, 60, 50, 90), -1)
            else vibrator.vibrate(ms)
        }
    }

    companion object {
        private const val PREFS = "tasbih_prefs"
        private const val KEY_COUNT = "count"
        private const val KEY_TARGET = "target"
        private const val KEY_PRESET = "preset"
        private const val SHORT_TICK = 30L
        private const val LONG_TICK = 60L
    }
}
