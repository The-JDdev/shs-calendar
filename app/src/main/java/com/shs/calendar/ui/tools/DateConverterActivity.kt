package com.shs.calendar.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.shs.calendar.ui.SHSBaseActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.shs.calendar.R
import com.shs.calendar.calendar.ConversionEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Date Converter: shows the picked date in Gregorian, Bengali and Hijri
 * side by side, with a copy-to-clipboard action. All conversions go through
 * [ConversionEngine] so the Hijri adjustment setting applies automatically.
 */
class DateConverterActivity : SHSBaseActivity() {

    private var picked: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_date_converter)

        findViewById<MaterialToolbar>(R.id.converter_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        val gregorian = findViewById<TextView>(R.id.conv_gregorian)
        val bengali = findViewById<TextView>(R.id.conv_bengali)
        val hijri = findViewById<TextView>(R.id.conv_hijri)
        val invalid = findViewById<TextView>(R.id.conv_invalid)
        val pickedDate = findViewById<TextView>(R.id.conv_picked_date)

        fun render(date: LocalDate?) {
            if (date == null) {
                invalid.visibility = View.VISIBLE
                gregorian.visibility = View.GONE
                bengali.visibility = View.GONE
                hijri.visibility = View.GONE
                return
            }
            invalid.visibility = View.GONE
            picked = date
            val result = ConversionEngine.fromGregorian(date)
            pickedDate.text = date.toString()
            gregorian.text = date.toString()
            bengali.text = ConversionEngine.gregorianToBengali(date).toString()
            hijri.text = result.hijri.toString()
            gregorian.visibility = View.VISIBLE
            bengali.visibility = View.VISIBLE
            hijri.visibility = View.VISIBLE
        }

        render(picked)

        findViewById<MaterialButton>(R.id.conv_copy).setOnClickListener {
            val text = "${picked} | ${ConversionEngine.gregorianToBengali(picked)} | " +
                ConversionEngine.fromGregorian(picked).hijri
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("converted_date", text))
            Toast.makeText(this, R.string.converter_copied, Toast.LENGTH_SHORT).show()
        }

        // The M10 layout renders conv_picked_date as a styled TextView, not a
        // MaterialButton — the old cast threw ClassCastException when this
        // screen opened. pickedDate above is already the same view.
        pickedDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.converter_title)
                .setSelection(picked.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                render(
                    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                )
            }
            picker.show(supportFragmentManager, "conv_pick")
        }
    }
}
