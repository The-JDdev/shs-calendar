package com.shs.calendar.ui.tools

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.shs.calendar.R
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * Age Calculator: exact age from a birth date, broken down as years / months /
 * days, with the date of the next birthday. All arithmetic runs through
 * [java.time.Period] / [ChronoUnit] — no calendar tables of our own.
 */
class AgeCalculatorActivity : AppCompatActivity() {

    private var birthDate: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_age_calculator)

        findViewById<MaterialToolbar>(R.id.age_toolbar).apply {
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener { finish() }
        }

        val birthValue = findViewById<TextView>(R.id.age_birthdate_value)
        val error = findViewById<TextView>(R.id.age_error)
        val result = findViewById<TextView>(R.id.age_result)
        val nextBirthday = findViewById<TextView>(R.id.age_next_birthday)

        fun recompute() {
            val birth = birthDate ?: return
            val today = LocalDate.now()
            if (birth.isAfter(today)) {
                error.setText(R.string.age_error_future)
                error.visibility = View.VISIBLE
                result.visibility = View.GONE
                nextBirthday.visibility = View.GONE
                return
            }
            error.visibility = View.GONE

            val period = Period.between(birth, today)
            result.text = getString(
                R.string.age_years_months_days,
                period.years,
                period.months,
                period.days
            )
            result.visibility = View.VISIBLE

            val next = nextBirthdayDate(birth, today)
            nextBirthday.text = getString(
                R.string.age_next_birthday,
                next,
                ChronoUnit.DAYS.between(today, next)
            )
            nextBirthday.visibility = View.VISIBLE
        }

        birthValue.setText(R.string.age_result_placeholder)
        findViewById<MaterialButton>(R.id.age_pick).setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.age_birthdate)
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                birthDate = java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                birthValue.text = birthDate.toString()
                recompute()
            }
            picker.show(supportFragmentManager, "age_pick")
        }
    }

    /** Next occurrence of the birth month/day, or today if it already passed. */
    private fun nextBirthdayDate(birth: LocalDate, today: LocalDate): LocalDate {
        val candidate = birth.withYear(today.year)
        return if (candidate.isBefore(today)) candidate.plusYears(1) else candidate
    }
}
