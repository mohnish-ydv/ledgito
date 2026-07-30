package com.mohnishraj.goldmineledger

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlin.math.min

object Utils {
    fun id(): String = UUID.randomUUID().toString()

    fun todayCompact(): String = LocalDate.now().toString().replace("-", "")

    fun defaultCurrency(): String = runCatching {
        Currency.getInstance(Locale.getDefault()).currencyCode
    }.getOrDefault("INR")

    val currencies: List<String>
        get() = (listOf(defaultCurrency()) + listOf("INR", "GBP", "USD", "EUR", "JPY", "AUD", "CAD", "AED", "SGD"))
            .distinct()

    fun currencyLabel(code: String): String = runCatching {
        "$code — ${Currency.getInstance(code).displayName}"
    }.getOrDefault(code)

    fun currencyCode(label: String): String = label.substringBefore(" —").trim().uppercase(Locale.ROOT)

    fun validCurrency(code: String): String? = runCatching {
        Currency.getInstance(code)
        null
    }.getOrElse { "Choose a valid currency" }

    fun parseMinor(text: String, code: String): Result<Long> = runCatching {
        require(text.trim().isNotEmpty()) { "Enter an amount" }
        val digits = Currency.getInstance(code).defaultFractionDigits.coerceAtLeast(0)
        BigDecimal(text.trim().replace(",", ""))
            .setScale(digits, RoundingMode.HALF_UP)
            .movePointRight(digits)
            .longValueExact()
    }

    fun plain(minor: Long, code: String): String {
        val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }
            .getOrDefault(2)
            .coerceAtLeast(0)
        return BigDecimal.valueOf(minor).movePointLeft(digits).stripTrailingZeros().toPlainString()
    }

    fun money(minor: Long, code: String): String {
        val currency = runCatching { Currency.getInstance(code) }.getOrElse { Currency.getInstance("INR") }
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val amount = BigDecimal.valueOf(minor).movePointLeft(digits)
        return NumberFormat.getCurrencyInstance().apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }.format(amount)
    }

    fun compactMoney(minor: Long, code: String): String {
        val currency = runCatching { Currency.getInstance(code) }.getOrElse { Currency.getInstance("INR") }
        val digits = currency.defaultFractionDigits.coerceAtLeast(0)
        val amount = BigDecimal.valueOf(minor).movePointLeft(digits).toDouble()
        val absolute = kotlin.math.abs(amount)
        val (scaled, suffix) = when {
            absolute >= 1_000_000_000 -> amount / 1_000_000_000.0 to "B"
            absolute >= 1_000_000 -> amount / 1_000_000.0 to "M"
            absolute >= 1_000 -> amount / 1_000.0 to "K"
            else -> amount to ""
        }
        val symbol = currency.symbol
        return if (suffix.isEmpty()) "$symbol${String.format(Locale.getDefault(), "%.0f", scaled)}"
        else "$symbol${String.format(Locale.getDefault(), "%.1f", scaled)}$suffix"
    }

    fun validName(value: String, max: Int): String? = when {
        value.trim().isEmpty() -> "Name is required"
        value.trim().length > max -> "Keep the name under $max characters"
        else -> null
    }

    fun validDate(value: String): String? = try {
        LocalDate.parse(value)
        null
    } catch (_: Exception) {
        "Use YYYY-MM-DD"
    }

    fun normaliseTags(values: List<String>): List<String> = values
        .flatMap { it.split(',') }
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() }
        .map { it.take(30) }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .take(12)

    fun recurrenceAnchorDay(date: String): Int {
        val d = LocalDate.parse(date)
        return if (d.dayOfMonth == d.lengthOfMonth()) 0 else d.dayOfMonth
    }

    fun nextRecurrence(
        date: String,
        frequency: RecurrenceFrequency,
        interval: Int,
        anchorDay: Int = recurrenceAnchorDay(date),
        monthEndMode: MonthEndMode = MonthEndMode.LAST_VALID_DAY
    ): String {
        val d = LocalDate.parse(date)
        val step = interval.coerceIn(1, 365)
        return when (frequency) {
            RecurrenceFrequency.DAILY -> d.plusDays(step.toLong())
            RecurrenceFrequency.WEEKLY -> d.plusWeeks(step.toLong())
            RecurrenceFrequency.MONTHLY -> nextValidMonth(YearMonth.from(d), step.toLong(), anchorDay, monthEndMode)
            RecurrenceFrequency.YEARLY -> {
                var month = YearMonth.of(d.year + step, d.month)
                while (anchorDay > 0 && anchorDay > month.lengthOfMonth() && monthEndMode == MonthEndMode.SKIP_INVALID_MONTH) {
                    month = YearMonth.of(month.year + step, d.month)
                }
                month.atDay(if (anchorDay == 0) month.lengthOfMonth() else min(anchorDay, month.lengthOfMonth()))
            }
        }.toString()
    }

    fun nextBillingDate(
        date: String,
        cadence: BillingCadence,
        anchorDay: Int = recurrenceAnchorDay(date)
    ): String {
        val (frequency, interval) = when (cadence) {
            BillingCadence.WEEKLY -> RecurrenceFrequency.WEEKLY to 1
            BillingCadence.MONTHLY -> RecurrenceFrequency.MONTHLY to 1
            BillingCadence.QUARTERLY -> RecurrenceFrequency.MONTHLY to 3
            BillingCadence.YEARLY -> RecurrenceFrequency.YEARLY to 1
        }
        return nextRecurrence(date, frequency, interval, anchorDay, MonthEndMode.LAST_VALID_DAY)
    }

    fun billingOccurrences(
        firstDueDate: String,
        cadence: BillingCadence,
        from: LocalDate,
        through: LocalDate,
        limit: Int = 1000
    ): List<LocalDate> {
        if (through.isBefore(from) || limit <= 0) return emptyList()
        val first = runCatching { LocalDate.parse(firstDueDate) }.getOrNull() ?: return emptyList()
        val anchorDay = recurrenceAnchorDay(firstDueDate)
        val rows = mutableListOf<LocalDate>()
        var date = first
        var safety = 0
        while (date.isBefore(from) && safety < limit) {
            date = LocalDate.parse(nextBillingDate(date.toString(), cadence, anchorDay))
            safety++
        }
        while (!date.isAfter(through) && safety < limit) {
            rows += date
            date = LocalDate.parse(nextBillingDate(date.toString(), cadence, anchorDay))
            safety++
        }
        return rows
    }

    private fun nextValidMonth(
        current: YearMonth,
        stepMonths: Long,
        anchorDay: Int,
        mode: MonthEndMode
    ): LocalDate {
        var month = current.plusMonths(stepMonths)
        while (anchorDay > 0 && anchorDay > month.lengthOfMonth() && mode == MonthEndMode.SKIP_INVALID_MONTH) {
            month = month.plusMonths(stepMonths)
        }
        return month.atDay(if (anchorDay == 0) month.lengthOfMonth() else min(anchorDay, month.lengthOfMonth()))
    }
}

data class DateWindow(val start: String, val end: String) {
    init {
        require(Utils.validDate(start) == null && Utils.validDate(end) == null && start <= end)
    }

    fun contains(date: String): Boolean = date in start..end
    fun lengthDays(): Long = ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(end)) + 1
}

object BudgetPeriodMath {
    fun windowFor(budget: BudgetEntity, referenceDate: String): DateWindow? {
        val reference = LocalDate.parse(referenceDate)
        val anchor = LocalDate.parse(budget.anchorDate)
        val window = when (BudgetPeriodType.from(budget.periodType)) {
            BudgetPeriodType.WEEKLY -> {
                val blockDays = 7L * budget.repeatInterval.coerceAtLeast(1)
                val diff = ChronoUnit.DAYS.between(anchor, reference)
                if (diff < 0) return null
                val start = anchor.plusDays((diff / blockDays) * blockDays)
                DateWindow(start.toString(), start.plusDays(blockDays - 1).toString())
            }
            BudgetPeriodType.MONTHLY -> {
                val anchorMonth = YearMonth.from(anchor)
                val refMonth = YearMonth.from(reference)
                val diff = ChronoUnit.MONTHS.between(anchorMonth, refMonth)
                if (diff < 0) return null
                val interval = budget.repeatInterval.coerceAtLeast(1).toLong()
                val startMonth = anchorMonth.plusMonths((diff / interval) * interval)
                DateWindow(
                    startMonth.atDay(1).toString(),
                    startMonth.plusMonths(interval).atDay(1).minusDays(1).toString()
                )
            }
            BudgetPeriodType.YEARLY -> {
                val interval = budget.repeatInterval.coerceAtLeast(1)
                val diff = reference.year - anchor.year
                if (diff < 0) return null
                val startYear = anchor.year + (diff / interval) * interval
                DateWindow(
                    LocalDate.of(startYear, 1, 1).toString(),
                    LocalDate.of(startYear + interval, 1, 1).minusDays(1).toString()
                )
            }
            BudgetPeriodType.CUSTOM -> {
                val end = budget.customEndDate?.let(LocalDate::parse) ?: anchor
                if (reference < anchor || reference > end) return null
                DateWindow(anchor.toString(), end.toString())
            }
        }
        val repeatUntil = budget.repeatUntil?.let(LocalDate::parse)
        return if (repeatUntil != null && LocalDate.parse(window.start) > repeatUntil) null else window
    }

    fun firstWindow(budget: BudgetEntity): DateWindow = when (BudgetPeriodType.from(budget.periodType)) {
        BudgetPeriodType.WEEKLY -> {
            val start = LocalDate.parse(budget.anchorDate)
            DateWindow(start.toString(), start.plusDays(7L * budget.repeatInterval.coerceAtLeast(1) - 1).toString())
        }
        BudgetPeriodType.MONTHLY -> {
            val start = YearMonth.from(LocalDate.parse(budget.anchorDate)).atDay(1)
            DateWindow(
                start.toString(),
                start.plusMonths(budget.repeatInterval.coerceAtLeast(1).toLong()).minusDays(1).toString()
            )
        }
        BudgetPeriodType.YEARLY -> {
            val start = LocalDate.of(LocalDate.parse(budget.anchorDate).year, 1, 1)
            DateWindow(
                start.toString(),
                start.plusYears(budget.repeatInterval.coerceAtLeast(1).toLong()).minusDays(1).toString()
            )
        }
        BudgetPeriodType.CUSTOM -> DateWindow(budget.anchorDate, budget.customEndDate ?: budget.anchorDate)
    }

    fun nextWindow(budget: BudgetEntity, current: DateWindow): DateWindow? {
        if (BudgetPeriodType.from(budget.periodType) == BudgetPeriodType.CUSTOM) return null
        val start = LocalDate.parse(current.end).plusDays(1)
        val end = when (BudgetPeriodType.from(budget.periodType)) {
            BudgetPeriodType.WEEKLY -> start.plusDays(7L * budget.repeatInterval.coerceAtLeast(1) - 1)
            BudgetPeriodType.MONTHLY -> start.plusMonths(budget.repeatInterval.coerceAtLeast(1).toLong()).minusDays(1)
            BudgetPeriodType.YEARLY -> start.plusYears(budget.repeatInterval.coerceAtLeast(1).toLong()).minusDays(1)
            BudgetPeriodType.CUSTOM -> start
        }
        val until = budget.repeatUntil?.let(LocalDate::parse)
        return if (until != null && start > until) null else DateWindow(start.toString(), end.toString())
    }
}

object ReportPeriodMath {
    fun window(
        preset: ReportPreset,
        referenceDate: String,
        customFrom: String? = null,
        customTo: String? = null,
        allTimeStart: String? = null
    ): DateWindow {
        val reference = LocalDate.parse(referenceDate)
        return when (preset) {
            ReportPreset.DAY -> DateWindow(reference.toString(), reference.toString())
            ReportPreset.WEEK -> {
                val start = reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                DateWindow(start.toString(), start.plusDays(6).toString())
            }
            ReportPreset.MONTH -> {
                val month = YearMonth.from(reference)
                DateWindow(month.atDay(1).toString(), month.atEndOfMonth().toString())
            }
            ReportPreset.QUARTER -> {
                val firstMonth = ((reference.monthValue - 1) / 3) * 3 + 1
                val start = LocalDate.of(reference.year, firstMonth, 1)
                DateWindow(start.toString(), start.plusMonths(3).minusDays(1).toString())
            }
            ReportPreset.SIX_MONTHS -> {
                val endMonth = YearMonth.from(reference)
                val start = endMonth.minusMonths(5).atDay(1)
                DateWindow(start.toString(), endMonth.atEndOfMonth().toString())
            }
            ReportPreset.YEAR -> DateWindow(
                LocalDate.of(reference.year, 1, 1).toString(),
                LocalDate.of(reference.year, 12, 31).toString()
            )
            ReportPreset.ALL_TIME -> DateWindow(minOf(allTimeStart ?: reference.toString(), reference.toString()), reference.toString())
            ReportPreset.CUSTOM -> {
                val from = customFrom?.takeIf { Utils.validDate(it) == null } ?: reference.toString()
                val to = customTo?.takeIf { Utils.validDate(it) == null } ?: from
                DateWindow(minOf(from, to), maxOf(from, to))
            }
        }
    }

    fun previous(current: DateWindow): DateWindow {
        val end = LocalDate.parse(current.start).minusDays(1)
        val start = end.minusDays(current.lengthDays() - 1)
        return DateWindow(start.toString(), end.toString())
    }

    fun shift(current: DateWindow, direction: Int): DateWindow {
        val days = current.lengthDays()
        val delta = days * direction.toLong()
        return DateWindow(
            LocalDate.parse(current.start).plusDays(delta).toString(),
            LocalDate.parse(current.end).plusDays(delta).toString()
        )
    }
}

object LedgerMath {
    fun balances(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): Map<String, Long> {
        val result = accounts.associate { it.id to it.openingBalanceMinor }.toMutableMap()
        transactions.filter { it.isCleared && !it.isDeleted }.forEach { tx ->
            when (TransactionType.from(tx.type)) {
                TransactionType.EXPENSE -> result[tx.accountId] = (result[tx.accountId] ?: 0L) - tx.amountMinor
                TransactionType.INCOME -> result[tx.accountId] = (result[tx.accountId] ?: 0L) + tx.amountMinor
                TransactionType.TRANSFER -> {
                    result[tx.accountId] = (result[tx.accountId] ?: 0L) - tx.amountMinor - tx.transferFeeMinor
                    tx.destinationAccountId?.let { id -> result[id] = (result[id] ?: 0L) + tx.destinationAmountMinor }
                }
            }
        }
        return result
    }

    fun accountImpact(tx: TransactionEntity, accountId: String): Long {
        if (!tx.isCleared || tx.isDeleted) return 0L
        return when (TransactionType.from(tx.type)) {
            TransactionType.EXPENSE -> if (tx.accountId == accountId) -tx.amountMinor else 0L
            TransactionType.INCOME -> if (tx.accountId == accountId) tx.amountMinor else 0L
            TransactionType.TRANSFER -> when (accountId) {
                tx.accountId -> -tx.amountMinor - tx.transferFeeMinor
                tx.destinationAccountId -> tx.destinationAmountMinor
                else -> 0L
            }
        }
    }
}
