package com.shs.calendar.calendar

/**
 * Bengali numeral helpers (০-৯) for the "Bengali numerals" setting toggle
 * and for rendering calendar cells / hero dates in Bangla digits.
 */
object BengaliNumerals {

    private val DIGITS = charArrayOf(
        '০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯'
    )

    /** 0..9 -> ০..৯ */
    fun digit(d: Int): Char {
        require(d in 0..9) { "digit must be 0..9, was $d" }
        return DIGITS[d]
    }

    /** Convert any text containing Western digits to Bengali digits. */
    fun toBengali(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(if (ch in '0'..'9') DIGITS[ch - '0'] else ch)
        }
        return sb.toString()
    }

    /** Convert Bengali digits back to Western digits (for parsing user input). */
    fun toWestern(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = DIGITS.indexOf(ch)
            sb.append(if (idx >= 0) ('0'.code + idx).toChar() else ch)
        }
        return sb.toString()
    }

    fun number(value: Int): String = toBengali(value.toString())

    /** True when the text contains at least one Bengali digit. */
    fun containsBengaliDigit(text: String): Boolean = text.any { it in DIGITS }
}
