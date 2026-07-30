package com.mohnishraj.goldmineledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {
    @Test fun parsesTwoDecimalCurrenciesIntoMinorUnits() {
        assertEquals(123456L, Utils.parseMinor("1,234.56", "INR").getOrThrow())
    }

    @Test fun parsesZeroDecimalCurrenciesWithoutInventingDecimals() {
        assertEquals(1234L, Utils.parseMinor("1234", "JPY").getOrThrow())
    }

    @Test fun rejectsInvalidDatesAndCurrencies() {
        assertTrue(Utils.validDate("2026-02-30") != null)
        assertTrue(Utils.validCurrency("NOT") != null)
        assertNull(Utils.validCurrency("GBP"))
    }

    @Test fun preservesNegativeOpeningBalances() {
        assertEquals(-4250L, Utils.parseMinor("-42.50", "GBP").getOrThrow())
    }

    @Test fun normalisesAndDeduplicatesTags() {
        assertEquals(listOf("Work", "tax"), Utils.normaliseTags(listOf(" Work ", "work", "tax")))
    }

    @Test fun monthEndRecurrenceKeepsMonthEndAnchor() {
        val anchor = Utils.recurrenceAnchorDay("2026-01-31")
        val february = Utils.nextRecurrence(
            "2026-01-31", RecurrenceFrequency.MONTHLY, 1, anchor,
            MonthEndMode.LAST_VALID_DAY
        )
        val march = Utils.nextRecurrence(
            february, RecurrenceFrequency.MONTHLY, 1, anchor,
            MonthEndMode.LAST_VALID_DAY
        )
        assertEquals(0, anchor)
        assertEquals("2026-02-28", february)
        assertEquals("2026-03-31", march)
    }

    @Test fun numberedMonthlyAnchorRecoversAfterShortMonth() {
        val anchor = Utils.recurrenceAnchorDay("2026-01-30")
        val february = Utils.nextRecurrence(
            "2026-01-30", RecurrenceFrequency.MONTHLY, 1, anchor,
            MonthEndMode.LAST_VALID_DAY
        )
        val march = Utils.nextRecurrence(
            february, RecurrenceFrequency.MONTHLY, 1, anchor,
            MonthEndMode.LAST_VALID_DAY
        )
        assertEquals(30, anchor)
        assertEquals("2026-02-28", february)
        assertEquals("2026-03-30", march)
    }

    @Test fun skipInvalidMonthMovesToTheNextValidOccurrence() {
        val next = Utils.nextRecurrence(
            "2026-01-30", RecurrenceFrequency.MONTHLY, 1, 30,
            MonthEndMode.SKIP_INVALID_MONTH
        )
        assertEquals("2026-03-30", next)
    }

    @Test fun subscriptionCadencePreservesMonthEndAndQuarterlyIntervals() {
        assertEquals(
            "2026-02-28",
            Utils.nextBillingDate("2026-01-31", BillingCadence.MONTHLY)
        )
        assertEquals(
            "2026-04-30",
            Utils.nextBillingDate("2026-01-31", BillingCadence.QUARTERLY)
        )
    }

    @Test fun subscriptionOccurrencesCoverTheForecastWindow() {
        assertEquals(
            listOf("2026-02-28", "2026-03-31", "2026-04-30"),
            Utils.billingOccurrences(
                "2026-01-31",
                BillingCadence.MONTHLY,
                java.time.LocalDate.parse("2026-02-01"),
                java.time.LocalDate.parse("2026-04-30")
            ).map { it.toString() }
        )
    }

    @Test fun monthlyBudgetWindowsRemainStableAcrossMonths() {
        val budget = budget(period = BudgetPeriodType.MONTHLY, anchor = "2026-01-15")
        val first = BudgetPeriodMath.firstWindow(budget)
        val second = BudgetPeriodMath.nextWindow(budget, first)
        val third = second?.let { BudgetPeriodMath.nextWindow(budget, it) }
        assertEquals(DateWindow("2026-01-01", "2026-01-31"), first)
        assertEquals(DateWindow("2026-02-01", "2026-02-28"), second)
        assertEquals(DateWindow("2026-03-01", "2026-03-31"), third)
    }

    @Test fun customBudgetOnlyMatchesItsExactWindow() {
        val budget = budget(
            period = BudgetPeriodType.CUSTOM,
            anchor = "2026-07-10",
            customEnd = "2026-08-20"
        )
        assertNull(BudgetPeriodMath.windowFor(budget, "2026-07-09"))
        assertEquals(DateWindow("2026-07-10", "2026-08-20"), BudgetPeriodMath.windowFor(budget, "2026-08-01"))
        assertNull(BudgetPeriodMath.windowFor(budget, "2026-08-21"))
    }

    @Test fun reportPresetsProduceExpectedCalendarWindows() {
        assertEquals(
            DateWindow("2026-07-01", "2026-09-30"),
            ReportPeriodMath.window(ReportPreset.QUARTER, "2026-08-12")
        )
        assertEquals(
            DateWindow("2026-03-01", "2026-08-31"),
            ReportPeriodMath.window(ReportPreset.SIX_MONTHS, "2026-08-12")
        )
        assertEquals(
            DateWindow("2026-01-01", "2026-12-31"),
            ReportPeriodMath.window(ReportPreset.YEAR, "2026-08-12")
        )
    }

    @Test fun sameCurrencyTransferMovesMoneyAndChargesFeeOnce() {
        val accounts = listOf(account("cash", 10000L, "INR"), account("bank", 50000L, "INR"))
        val transfer = transaction(
            id = "transfer",
            type = TransactionType.TRANSFER,
            accountId = "bank",
            destinationAccountId = "cash",
            amountMinor = 10000L,
            destinationAmountMinor = 10000L,
            destinationCurrencyCode = "INR",
            transferFeeMinor = 250L
        )
        assertEquals(mapOf("cash" to 20000L, "bank" to 39750L), LedgerMath.balances(accounts, listOf(transfer)))
        assertEquals(-10250L, LedgerMath.accountImpact(transfer, "bank"))
        assertEquals(10000L, LedgerMath.accountImpact(transfer, "cash"))
    }

    @Test fun crossCurrencyTransferUsesExplicitReceivedAmount() {
        val accounts = listOf(account("bank", 50000L, "INR"), account("travel", 2500L, "GBP"))
        val transfer = transaction(
            id = "fx",
            type = TransactionType.TRANSFER,
            accountId = "bank",
            destinationAccountId = "travel",
            amountMinor = 11000L,
            currencyCode = "INR",
            destinationAmountMinor = 10000L,
            destinationCurrencyCode = "GBP",
            transferFeeMinor = 100L
        )
        assertEquals(mapOf("bank" to 38900L, "travel" to 12500L), LedgerMath.balances(accounts, listOf(transfer)))
    }

    @Test fun pendingAndDeletedTransactionsNeverChangeBalances() {
        val accounts = listOf(account("cash", 10000L, "INR"))
        val pending = transaction(
            id = "pending",
            type = TransactionType.EXPENSE,
            accountId = "cash",
            amountMinor = 9000L,
            isCleared = false
        )
        val deleted = transaction(
            id = "deleted",
            type = TransactionType.INCOME,
            accountId = "cash",
            amountMinor = 50000L,
            isDeleted = true
        )
        assertEquals(mapOf("cash" to 10000L), LedgerMath.balances(accounts, listOf(pending, deleted)))
        assertEquals(0L, LedgerMath.accountImpact(pending, "cash"))
        assertEquals(0L, LedgerMath.accountImpact(deleted, "cash"))
    }

    private fun account(id: String, openingBalanceMinor: Long, currency: String) = AccountEntity(
        id = id,
        profileId = "p",
        name = id,
        type = AccountType.CASH.name,
        currencyCode = currency,
        openingBalanceMinor = openingBalanceMinor,
        openingDate = "2026-01-01",
        includeInTotal = true,
        isArchived = false,
        sortOrder = 0,
        colourArgb = 1,
        createdAt = 1,
        updatedAt = 1
    )

    private fun transaction(
        id: String,
        type: TransactionType,
        accountId: String,
        destinationAccountId: String? = null,
        amountMinor: Long,
        currencyCode: String = "INR",
        destinationAmountMinor: Long = 0L,
        destinationCurrencyCode: String? = null,
        transferFeeMinor: Long = 0L,
        isCleared: Boolean = true,
        isDeleted: Boolean = false
    ) = TransactionEntity(
        id = id,
        profileId = "p",
        type = type.name,
        accountId = accountId,
        destinationAccountId = destinationAccountId,
        categoryId = if (type == TransactionType.TRANSFER) null else "category",
        amountMinor = amountMinor,
        currencyCode = currencyCode,
        destinationAmountMinor = destinationAmountMinor,
        destinationCurrencyCode = destinationCurrencyCode,
        transferFeeMinor = transferFeeMinor,
        transactionDate = "2026-01-02",
        payee = "",
        note = "",
        isCleared = isCleared,
        recurringRuleId = null,
        isDeleted = isDeleted,
        deletedAt = if (isDeleted) 2L else null,
        createdAt = 1,
        updatedAt = 1
    )

    private fun budget(
        period: BudgetPeriodType,
        anchor: String,
        customEnd: String? = null
    ) = BudgetEntity(
        id = "b",
        profileId = "p",
        name = "Budget",
        categoryId = null,
        amountMinor = 10000,
        currencyCode = "INR",
        periodType = period.name,
        anchorDate = anchor,
        customEndDate = customEnd,
        repeatInterval = 1,
        repeatUntil = null,
        carryoverMode = BudgetCarryover.OFF.name,
        isActive = true,
        createdAt = 1,
        updatedAt = 1
    )
}
