package com.mohnishraj.goldmineledger

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FlowRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f * density
        color = ContextCompat.getColor(context, R.color.white_18)
    }
    private val incomePaint = Paint(track).apply { color = ContextCompat.getColor(context, R.color.income) }
    private val expensePaint = Paint(track).apply { color = ContextCompat.getColor(context, R.color.expense) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.hero_muted)
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val valuePaint = Paint(labelPaint).apply {
        color = ContextCompat.getColor(context, R.color.hero_text)
        textSize = 17f * resources.displayMetrics.scaledDensity
    }
    private var income = 0L
    private var expense = 0L
    private var centreLabel = "CASH FLOW"
    private var centreValue = "Balanced"

    fun setValues(incomeMinor: Long, expenseMinor: Long, value: String = "") {
        income = incomeMinor.coerceAtLeast(0)
        expense = expenseMinor.coerceAtLeast(0)
        centreValue = value.ifBlank {
            when {
                income == 0L && expense == 0L -> "No data"
                income >= expense -> "Positive"
                else -> "Watch"
            }
        }
        contentDescription = "Cash-flow ring: $centreValue"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 18f * density
        val diameter = (min(width, height).toFloat() - (pad * 2f)).coerceAtLeast(0f)
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        val rect = RectF(left, top, left + diameter, top + diameter)
        canvas.drawArc(rect, -90f, 360f, false, track)
        val total = (income + expense).coerceAtLeast(1L).toFloat()
        val incomeSweep = 350f * income / total
        val expenseSweep = 350f * expense / total
        if (income > 0) canvas.drawArc(rect, -90f, incomeSweep, false, incomePaint)
        if (expense > 0) canvas.drawArc(rect, -90f + incomeSweep + 6f, max(0f, expenseSweep - 6f), false, expensePaint)
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText(centreValue, cx, cy - 1f * density, valuePaint)
        canvas.drawText(centreLabel, cx, cy + 17f * density, labelPaint)
    }
}

class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline_variant)
        strokeWidth = 1f * density
        alpha = 100
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.FILL
        alpha = 36
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(linePaint).apply { style = Paint.Style.FILL }
    private var points: List<Long> = emptyList()

    fun setValues(values: List<Long>) {
        points = values.takeLast(31)
        contentDescription = when {
            points.isEmpty() -> "Cash-flow trend with no recorded data"
            points.size == 1 -> "Cash-flow trend with one recorded point"
            points.last() > points.first() -> "Cash-flow trend ending above its starting point"
            points.last() < points.first() -> "Cash-flow trend ending below its starting point"
            else -> "Cash-flow trend ending level with its starting point"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 4f * density
        val right = width - 4f * density
        val top = 12f * density
        val bottom = height - 12f * density
        repeat(3) { index ->
            val y = top + (bottom - top) * index / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        if (points.isEmpty()) return
        val minValue = points.minOrNull() ?: 0L
        val maxValue = points.maxOrNull() ?: 1L
        val span = max(1L, maxValue - minValue).toFloat()
        val step = if (points.size == 1) 0f else (right - left) / (points.size - 1)
        val line = Path()
        val fill = Path()
        points.forEachIndexed { index, value ->
            val x = if (points.size == 1) (left + right) / 2f else left + index * step
            val normalised = (value - minValue) / span
            val y = bottom - normalised * (bottom - top)
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, bottom)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
            if (index == points.lastIndex) canvas.drawCircle(x, y, 4f * density, dotPaint)
        }
        fill.lineTo(if (points.size == 1) (left + right) / 2f else right, bottom)
        fill.close()
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(line, linePaint)
    }
}

class SpendingHeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline_variant)
        alpha = 90
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.expense)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private var values: Map<java.time.LocalDate, Long> = emptyMap()
    private var startDate: java.time.LocalDate = java.time.LocalDate.now().minusDays(83)
    private var endDate: java.time.LocalDate = java.time.LocalDate.now()

    fun setValues(rows: List<ReportRow>, from: String, to: String) {
        val parsedFrom = runCatching { java.time.LocalDate.parse(from) }.getOrNull()
        val parsedTo = runCatching { java.time.LocalDate.parse(to) }.getOrNull()
        endDate = parsedTo ?: java.time.LocalDate.now()
        val floor = endDate.minusDays(83)
        val candidate = parsedFrom ?: floor
        startDate = if (candidate.isBefore(floor)) floor else candidate
        values = rows.mapNotNull { row ->
            runCatching { java.time.LocalDate.parse(row.label) }.getOrNull()?.let { it to abs(row.amountMinor) }
        }.toMap()
        val activeDays = values.count { !it.key.isBefore(startDate) && !it.key.isAfter(endDate) && it.value > 0L }
        contentDescription = if (activeDays == 0) {
            "Spending heatmap with no recorded activity"
        } else {
            "Spending heatmap with activity on $activeDays days"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visibleFloor = endDate.minusDays(83)
        val effectiveStart = if (startDate.isBefore(visibleFloor)) visibleFloor else startDate
        val displayStart = effectiveStart.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)
        )
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(displayStart, endDate).toInt() + 1
        if (totalDays <= 0) return

        val labelHeight = 20f * density
        val gap = 4f * density
        val columns = 7
        val rows = ((totalDays + columns - 1) / columns).coerceAtLeast(1)
        val cell = min(
            (width - gap * (columns - 1)) / columns,
            (height - labelHeight - gap * (rows - 1)) / rows
        ).coerceAtLeast(4f * density)
        val gridWidth = cell * columns + gap * (columns - 1)
        val left = (width - gridWidth) / 2f
        val maxAmount = values.filterKeys { !it.isBefore(effectiveStart) && !it.isAfter(endDate) }
            .values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val radius = 4f * density

        repeat(totalDays) { index ->
            val date = displayStart.plusDays(index.toLong())
            val row = index / columns
            val column = date.dayOfWeek.value - 1
            val x = left + column * (cell + gap)
            val y = row * (cell + gap)
            val inSelectedWindow = !date.isBefore(effectiveStart) && !date.isAfter(endDate)
            val amount = if (inSelectedWindow) values[date] ?: 0L else 0L
            val paint = if (!inSelectedWindow || amount <= 0L) emptyPaint else activePaint.apply {
                alpha = (55 + 200 * (amount.toDouble() / maxAmount.toDouble())).roundToInt().coerceIn(55, 255)
            }
            if (!inSelectedWindow) paint.alpha = 35
            canvas.drawRoundRect(x, y, x + cell, y + cell, radius, radius, paint)
            if (paint === emptyPaint) emptyPaint.alpha = 90
        }

        val labels = listOf("M", "T", "W", "T", "F", "S", "S")
        labels.forEachIndexed { index, label ->
            val x = left + index * (cell + gap) + cell / 2f
            canvas.drawText(label, x, height - 3f * density, labelPaint)
        }
    }
}

class AllocationDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f * density
        color = ContextCompat.getColor(context, R.color.outline_variant)
    }
    private val segmentPaint = Paint(trackPaint)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = ContextCompat.getColor(context, R.color.on_surface_variant)
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val valuePaint = Paint(labelPaint).apply {
        color = ContextCompat.getColor(context, R.color.on_surface)
        textSize = 23f * resources.displayMetrics.scaledDensity
    }
    private val colours = intArrayOf(
        ContextCompat.getColor(context, R.color.primary),
        ContextCompat.getColor(context, R.color.income),
        ContextCompat.getColor(context, R.color.transfer),
        ContextCompat.getColor(context, R.color.tertiary),
        ContextCompat.getColor(context, R.color.expense)
    )
    private var segments: List<Pair<String, Long>> = emptyList()
    private var centreValue = "0"

    fun setValues(rows: List<Pair<String, Long>>, centre: String) {
        segments = rows.filter { it.second > 0L }.sortedByDescending { it.second }.take(7)
        centreValue = centre
        val total = segments.sumOf { it.second }
        contentDescription = if (total <= 0L) {
            "Asset allocation chart with no recorded positive value"
        } else {
            "Asset allocation chart. " + segments.joinToString { (label, value) ->
                "$label ${((value.toDouble() / total.toDouble()) * 100).roundToInt()} percent"
            }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 24f * density
        val size = (min(width, height).toFloat() - pad * 2f).coerceAtLeast(0f)
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val rect = RectF(left, top, left + size, top + size)
        canvas.drawArc(rect, -90f, 360f, false, trackPaint)
        val total = segments.sumOf { it.second }.coerceAtLeast(1L).toFloat()
        var start = -90f
        segments.forEachIndexed { index, (_, amount) ->
            val sweep = amount / total * 360f
            segmentPaint.color = colours[index % colours.size]
            canvas.drawArc(rect, start + 2f, (sweep - 4f).coerceAtLeast(1f), false, segmentPaint)
            start += sweep
        }
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawText(centreValue, cx, cy - 1f * density, valuePaint)
        canvas.drawText("NET WORTH", cx, cy + 19f * density, labelPaint)
    }
}
