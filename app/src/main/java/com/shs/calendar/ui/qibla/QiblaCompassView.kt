package com.shs.calendar.ui.qibla

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * M3 compass dial for the Qibla screen.
 *
 * Draws a degree dial (ticks every 15°, cardinal letters) plus a needle
 * pointing at the computed bearing. When the magnetometer feeds [setHeading],
 * the dial rotates so the top of the view is where the phone points
 * (heading-up); without a sensor the dial stays north-up (manual fallback).
 * All colors are theme palette values — no decorative hardcoded accents.
 */
class QiblaCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Device compass heading in degrees (0 = facing true north). */
    private var heading = 0.0

    /** Qibla bearing in degrees (from QiblaEngine). */
    private var bearing = 0.0

    private var sensorPresent = false

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#243059") // shs_stroke
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#64709B") // shs_text_muted
    }
    private val tickMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.parseColor("#9AA6C9") // shs_text_secondary
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#F5F8FF") // shs_text_primary
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#22D3EE") // shs_cyan
    }
    private val kaabaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBBF24") // shs_amber (Kaaba marker)
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F8FF")
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#121A36") // shs_surface
    }
    private val oval = RectF()

    /** Feed device heading (degrees) from the rotation sensor; null-safe redraw. */
    fun setHeading(degrees: Double) {
        heading = ((degrees % 360.0) + 360.0) % 360.0
        invalidate()
    }

    /** Set the computed Qibla bearing and whether a sensor is driving heading. */
    fun setBearing(degrees: Double, hasSensor: Boolean) {
        bearing = ((degrees % 360.0) + 360.0) % 360.0
        sensorPresent = hasSensor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = min(w, h)
        val cx = w / 2f
        val cy = h / 2f
        val radius = size / 2f - 10f
        if (radius <= 0f) return

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawOval(oval, bgPaint)
        canvas.drawOval(oval, ringPaint)

        labelPaint.textSize = radius * 0.14f

        // Dial rotation: heading-up with a sensor, north-up without.
        val dialRot = if (sensorPresent) -heading.toFloat() else 0f
        canvas.save()
        canvas.rotate(dialRot, cx, cy)

        // Ticks every 15°, majors every 45°.
        var deg = 0
        while (deg < 360) {
            val major = deg % 45 == 0
            val paint = if (major) tickMajorPaint else tickPaint
            val outer = radius
            val inner = radius * if (major) 0.84f else 0.90f
            val rad = Math.toRadians(deg.toDouble())
            val s = sin(rad).toFloat()
            val c = cos(rad).toFloat()
            canvas.drawLine(cx + s * inner, cy - c * inner, cx + s * outer, cy - c * outer, paint)
            if (major) {
                val lr = radius * 0.72f
                val label = when (deg) {
                    0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"
                    else -> deg.toString()
                }
                canvas.drawText(label, cx + s * lr, cy - c * lr + labelPaint.textSize / 3f, labelPaint)
            }
            deg += 15
        }
        canvas.restore()

        // Needle: points at the Qibla relative to current heading.
        canvas.save()
        canvas.rotate((bearing - heading).toFloat(), cx, cy)
        val nr = radius * 0.78f
        canvas.drawLine(cx, cy, cx, cy - nr, needlePaint)
        // Kaaba marker at the needle tip.
        val m = radius * 0.05f
        canvas.drawRect(cx - m, cy - nr - m, cx + m, cy - nr + m, kaabaPaint)
        canvas.restore()

        canvas.drawCircle(cx, cy, radius * 0.04f, centerPaint)
    }
}
