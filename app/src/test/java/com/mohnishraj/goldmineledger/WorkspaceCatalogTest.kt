package com.mohnishraj.goldmineledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCatalogTest {
    @Test fun everyWorkspaceTypeHasOneConfiguration() {
        assertEquals(WorkspaceType.entries.toSet(), WorkspaceCatalog.all.map { it.type }.toSet())
        assertEquals(WorkspaceType.entries.size, WorkspaceCatalog.all.size)
    }

    @Test fun planningPaymentsThatCanPostRequireLedgerLinks() {
        val paymentTypes = setOf(
            WorkspaceType.BILL,
            WorkspaceType.EMI,
            WorkspaceType.DEBT,
            WorkspaceType.LOAN,
            WorkspaceType.LIABILITY
        )
        paymentTypes.forEach { type ->
            val config = WorkspaceCatalog.forType(type)
            assertEquals("PAYMENT", config.eventKind)
            assertTrue(config.supportsLedgerPost)
            assertTrue(config.showAccountAndCategory)
        }
    }

    @Test fun wealthTypesExposeManualValueUpdates() {
        val wealthTypes = setOf(
            WorkspaceType.INVESTMENT,
            WorkspaceType.MUTUAL_FUND,
            WorkspaceType.GOLD,
            WorkspaceType.FIXED_DEPOSIT,
            WorkspaceType.PPF,
            WorkspaceType.EPF,
            WorkspaceType.CRYPTO,
            WorkspaceType.ASSET,
            WorkspaceType.CREDIT
        )
        wealthTypes.forEach { type ->
            assertEquals("VALUE", WorkspaceCatalog.forType(type).eventKind)
        }
    }
}
