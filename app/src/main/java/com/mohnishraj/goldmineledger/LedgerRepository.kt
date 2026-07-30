package com.mohnishraj.goldmineledger

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max

class LedgerRepository(
    private val db: LedgerDatabase,
    private val settings: SettingsRepository,
    private val attachmentStorage: AttachmentStorage
) {
    fun observeProfile(): Flow<ProfileEntity?> = db.profiles().observe()
    fun observeAccounts(profileId: String): Flow<List<AccountEntity>> = db.accounts().observeAll(profileId)
    fun observeCategories(profileId: String): Flow<List<CategoryEntity>> = db.categories().observeAll(profileId)
    fun observeTransactions(profileId: String): Flow<List<TransactionEntity>> = db.transactions().observeAll(profileId)
    fun observeTags(profileId: String): Flow<List<TagEntity>> = db.tags().observeAll(profileId)
    fun observeTagRefs(): Flow<List<TransactionTagCrossRef>> = db.tags().observeRefs()
    fun observeAttachments(): Flow<List<AttachmentEntity>> = db.attachments().observeAll()
    fun observeSplits(): Flow<List<TransactionSplitEntity>> = db.transactions().observeSplits()
    fun observeRevisions(): Flow<List<TransactionRevisionEntity>> = db.transactions().observeRevisions()
    fun observeRecurring(profileId: String): Flow<List<RecurringRuleEntity>> = db.recurring().observeAll(profileId)
    fun observeBudgets(profileId: String): Flow<List<BudgetEntity>> = db.budgets().observeAll(profileId)
    fun observeBudgetPeriods(): Flow<List<BudgetPeriodEntity>> = db.budgets().observePeriods()
    fun observeAudit(profileId: String): Flow<List<AuditEventEntity>> = db.audit().observeRecent(profileId)
    suspend fun profile(): ProfileEntity? = db.profiles().get()

    suspend fun finishOnboarding(
        currency: String,
        accountName: String,
        type: AccountType,
        openingMinor: Long,
        openingDate: String,
        addSavings: Boolean
    ) {
        var finalCurrency = currency
        db.withTransaction {
            db.profiles().get()?.let {
                finalCurrency = it.baseCurrency
                return@withTransaction
            }
            require(Utils.validCurrency(currency) == null) { "Choose a valid currency" }
            require(Utils.validDate(openingDate) == null) { "Choose a valid opening date" }
            require(Utils.validName(accountName, 50) == null) { Utils.validName(accountName, 50) ?: "Invalid account name" }
            val now = System.currentTimeMillis()
            val profileId = Utils.id()
            db.profiles().insert(ProfileEntity(profileId, "My finances", currency, Locale.getDefault().toLanguageTag(), now, now))
            db.accounts().insert(AccountEntity(
                Utils.id(), profileId, accountName.trim(), type.name, currency, openingMinor,
                openingDate, true, false, 0, 0xFF2F6B4F.toInt(), now, now
            ))
            if (addSavings) {
                db.accounts().insert(AccountEntity(
                    Utils.id(), profileId, "Savings", AccountType.BANK.name, currency, 0,
                    openingDate, true, false, 1, 0xFF315D8C.toInt(), now, now
                ))
            }
            db.categories().insertAll(defaultCategories(profileId, now))
            audit(profileId, "PROFILE", profileId, "CREATED")
        }
        settings.finishOnboarding(finalCurrency)
    }

    suspend fun saveAccount(draft: AccountDraft) = db.withTransaction {
        val profileId = db.profiles().get()?.id ?: error("Profile not found")
        require(Utils.validName(draft.name, 50) == null) { Utils.validName(draft.name, 50) ?: "Invalid account name" }
        require(Utils.validCurrency(draft.currency) == null) { "Choose a valid currency" }
        require(Utils.validDate(draft.openingDate) == null) { "Choose a valid opening date" }
        val now = System.currentTimeMillis()
        val old = draft.id?.let { db.accounts().get(it) }
        require(db.accounts().duplicateNameCount(profileId, draft.name.trim(), old?.id ?: "") == 0) {
            "An account with this name already exists"
        }
        if (old != null && old.currencyCode != draft.currency) {
            require(db.accounts().referenceCount(old.id) == 0) {
                "Currency cannot change after transactions or recurring rules use this account"
            }
        }
        val item = if (old == null) {
            AccountEntity(
                Utils.id(), profileId, draft.name.trim(), draft.type.name, draft.currency,
                draft.openingMinor, draft.openingDate, draft.includeInTotal, draft.archived,
                db.accounts().nextOrder(profileId), draft.colourArgb, now, now
            ).also { db.accounts().insert(it) }
        } else {
            old.copy(
                name = draft.name.trim(), type = draft.type.name, currencyCode = draft.currency,
                openingBalanceMinor = draft.openingMinor, openingDate = draft.openingDate,
                includeInTotal = draft.includeInTotal, isArchived = draft.archived,
                colourArgb = draft.colourArgb, updatedAt = now
            ).also { db.accounts().update(it) }
        }
        audit(profileId, "ACCOUNT", item.id, if (old == null) "CREATED" else "UPDATED")
    }

    suspend fun archiveAccount(item: AccountEntity, archived: Boolean) = db.withTransaction {
        db.accounts().update(item.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
        audit(item.profileId, "ACCOUNT", item.id, if (archived) "ARCHIVED" else "RESTORED")
    }

    suspend fun deleteAccount(item: AccountEntity) = db.withTransaction {
        require(db.accounts().referenceCount(item.id) == 0) {
            "Archive this account instead. Transactions or recurring rules still reference it."
        }
        db.accounts().delete(item)
        audit(item.profileId, "ACCOUNT", item.id, "DELETED")
    }

    suspend fun saveCategory(draft: CategoryDraft) = db.withTransaction {
        val profileId = db.profiles().get()?.id ?: error("Profile not found")
        require(Utils.validName(draft.name, 50) == null) { Utils.validName(draft.name, 50) ?: "Invalid category name" }
        require(draft.parentId != draft.id) { "A category cannot be its own parent" }
        val now = System.currentTimeMillis()
        val old = draft.id?.let { db.categories().get(it) }
        if (old != null && (draft.parentId != null || old.kind != draft.kind.name)) {
            require(db.categories().childCount(old.id) == 0) {
                "Move or delete subcategories before changing this category's level or type"
            }
        }
        if (old != null && old.kind != draft.kind.name) {
            require(
                db.categories().transactionReferenceCount(old.id) == 0 &&
                    db.categories().recurringReferenceCount(old.id) == 0 &&
                    db.categories().budgetReferenceCount(old.id) == 0
            ) { "Category type cannot change while transactions, recurring rules or budgets reference it" }
        }
        val parent = draft.parentId?.let { db.categories().get(it) }
        require(parent == null || (parent.parentId == null && parent.kind == draft.kind.name && !parent.isArchived)) {
            "Choose an active top-level category of the same type"
        }
        require(db.categories().duplicateNameCount(profileId, draft.kind.name, draft.name.trim(), old?.id ?: "") == 0) {
            "A ${draft.kind.label.lowercase()} category with this name already exists"
        }
        val item = if (old == null) {
            CategoryEntity(
                Utils.id(), profileId, draft.parentId, draft.name.trim(), draft.kind.name,
                draft.iconKey, draft.colourArgb, false, draft.archived,
                db.categories().nextOrder(profileId, draft.kind.name), now, now
            ).also { db.categories().insert(it) }
        } else {
            old.copy(
                parentId = draft.parentId, name = draft.name.trim(), kind = draft.kind.name,
                iconKey = draft.iconKey, colourArgb = draft.colourArgb,
                isArchived = draft.archived, updatedAt = now
            ).also { db.categories().update(it) }
        }
        audit(profileId, "CATEGORY", item.id, if (old == null) "CREATED" else "UPDATED")
    }

    suspend fun archiveCategory(item: CategoryEntity, archived: Boolean) = db.withTransaction {
        val now = System.currentTimeMillis()
        db.categories().update(item.copy(isArchived = archived, updatedAt = now))
        if (archived && item.parentId == null) db.categories().archiveChildren(item.id, now)
        audit(item.profileId, "CATEGORY", item.id, if (archived) "ARCHIVED" else "RESTORED")
    }

    suspend fun deleteCategory(item: CategoryEntity) = db.withTransaction {
        require(!item.isSystem) { "Default categories can be archived but not deleted" }
        require(db.categories().childCount(item.id) == 0) { "Move or delete subcategories first" }
        require(
            db.categories().transactionReferenceCount(item.id) == 0 &&
                db.categories().recurringReferenceCount(item.id) == 0 &&
                db.categories().budgetReferenceCount(item.id) == 0
        ) { "Archive this category instead. Existing transactions, recurring rules or budgets still reference it." }
        db.categories().delete(item)
        audit(item.profileId, "CATEGORY", item.id, "DELETED")
    }

    suspend fun saveTransaction(draft: TransactionDraft) {
        val copied = mutableListOf<AttachmentEntity>()
        val oldFiles = mutableListOf<AttachmentEntity>()
        try {
            db.withTransaction {
                val profileId = db.profiles().get()?.id ?: error("Profile not found")
                val old = draft.id?.let { db.transactions().get(it) }
                require(old == null || !old.isDeleted) { "Restore this transaction before editing it" }
                require(draft.attachmentUris.distinct().size <= 5) { "Add no more than 5 attachments at a time" }
                require(Utils.validDate(draft.date) == null) { "Invalid transaction date" }
                require(draft.transferFeeMinor >= 0) { "Transfer fee cannot be negative" }
                val validated = validateTransaction(
                    type = draft.type,
                    accountId = draft.accountId,
                    destinationAccountId = draft.destinationAccountId,
                    categoryId = if (draft.splits.isEmpty()) draft.categoryId else null,
                    amountMinor = draft.amountMinor,
                    allowedArchivedAccountIds = setOfNotNull(old?.accountId, old?.destinationAccountId),
                    allowedArchivedCategoryId = old?.categoryId,
                    allowMissingCategory = draft.splits.isNotEmpty()
                )
                validateTransactionDate(draft.date, validated)
                val destinationAmount = destinationAmount(
                    draft.type, validated.account, validated.destination, draft.amountMinor, draft.destinationAmountMinor
                )
                validateSplits(draft.type, draft.amountMinor, draft.splits, old)
                require(draft.type == TransactionType.TRANSFER || draft.transferFeeMinor == 0L) {
                    "Transfer fee is available only for transfers"
                }
                val now = System.currentTimeMillis()
                val transactionId = old?.id ?: Utils.id()
                if (old != null) insertRevision(old, "BEFORE_EDIT")
                val item = TransactionEntity(
                    id = transactionId,
                    profileId = profileId,
                    type = draft.type.name,
                    accountId = validated.account.id,
                    destinationAccountId = validated.destination?.id,
                    categoryId = if (draft.splits.isEmpty()) validated.category?.id else null,
                    amountMinor = draft.amountMinor,
                    currencyCode = validated.account.currencyCode,
                    destinationAmountMinor = destinationAmount,
                    destinationCurrencyCode = validated.destination?.currencyCode,
                    transferFeeMinor = draft.transferFeeMinor,
                    transactionDate = draft.date,
                    payee = draft.payee.trim().take(80),
                    note = draft.note.trim().take(500),
                    isCleared = draft.cleared,
                    recurringRuleId = old?.recurringRuleId,
                    isDeleted = false,
                    deletedAt = null,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now
                )
                if (old == null) db.transactions().insert(item) else db.transactions().update(item)
                db.transactions().deleteSplits(transactionId)
                if (draft.splits.isNotEmpty()) {
                    db.transactions().insertSplits(draft.splits.mapIndexed { index, split ->
                        TransactionSplitEntity(Utils.id(), transactionId, split.categoryId, split.amountMinor, split.memo.trim().take(120), index)
                    })
                }
                replaceTags(profileId, transactionId, draft.tags)
                if (draft.removeExistingAttachments) {
                    oldFiles += db.attachments().getForTransaction(transactionId)
                    db.attachments().deleteForTransaction(transactionId)
                }
                val existingCount = if (draft.removeExistingAttachments) 0 else db.attachments().getForTransaction(transactionId).size
                require(existingCount + draft.attachmentUris.distinct().size <= 10) { "A transaction can hold up to 10 attachments" }
                val newItems = draft.attachmentUris.distinct().map { uri ->
                    attachmentStorage.copy(transactionId, uri).also { copied += it }
                }
                if (newItems.isNotEmpty()) db.attachments().insertAll(newItems)
                insertRevision(item, if (old == null) "CREATED" else "EDITED")
                audit(profileId, "TRANSACTION", transactionId, if (old == null) "CREATED" else "UPDATED")
            }
            attachmentStorage.delete(oldFiles)
        } catch (error: Throwable) {
            attachmentStorage.delete(copied)
            throw error
        }
    }

    suspend fun deleteTransaction(item: TransactionEntity) = db.withTransaction {
        val current = db.transactions().get(item.id) ?: return@withTransaction
        if (current.isDeleted) return@withTransaction
        insertRevision(current, "BEFORE_DELETE")
        val now = System.currentTimeMillis()
        db.transactions().update(current.copy(isDeleted = true, deletedAt = now, updatedAt = now))
        audit(current.profileId, "TRANSACTION", current.id, "DELETED_UNDO_AVAILABLE")
    }

    suspend fun undoDeleteTransaction(id: String) = db.withTransaction {
        val current = db.transactions().get(id) ?: error("Transaction no longer exists")
        require(current.isDeleted) { "Transaction is already active" }
        val now = System.currentTimeMillis()
        val restored = current.copy(isDeleted = false, deletedAt = null, updatedAt = now)
        db.transactions().update(restored)
        insertRevision(restored, "RESTORED")
        audit(restored.profileId, "TRANSACTION", restored.id, "RESTORED")
    }

    suspend fun transactionHistory(id: String): List<TransactionRevisionEntity> = db.transactions().getRevisions(id)

    suspend fun saveRecurring(draft: RecurringRuleDraft) = db.withTransaction {
        val profileId = db.profiles().get()?.id ?: error("Profile not found")
        require(Utils.validName(draft.name, 60) == null) { Utils.validName(draft.name, 60) ?: "Invalid rule name" }
        require(draft.transferFeeMinor >= 0) { "Transfer fee cannot be negative" }
        val old = draft.id?.let { db.recurring().get(it) }
        val validated = validateTransaction(
            draft.type, draft.accountId, draft.destinationAccountId, draft.categoryId, draft.amountMinor,
            allowedArchivedAccountIds = setOfNotNull(old?.accountId, old?.destinationAccountId),
            allowedArchivedCategoryId = old?.categoryId
        )
        val received = destinationAmount(draft.type, validated.account, validated.destination, draft.amountMinor, draft.destinationAmountMinor)
        require(draft.type == TransactionType.TRANSFER || draft.transferFeeMinor == 0L) {
            "Transfer fee is available only for transfers"
        }
        require(draft.intervalCount in 1..365) { "Interval must be between 1 and 365" }
        require(draft.occurrencesRemaining == null || draft.occurrencesRemaining in 1..10000) { "Occurrences must be 1 to 10000" }
        require(Utils.validDate(draft.nextDueDate) == null) { "Invalid next due date" }
        validateTransactionDate(draft.nextDueDate, validated)
        draft.endDate?.takeIf { it.isNotBlank() }?.let {
            require(Utils.validDate(it) == null) { "Invalid end date" }
            require(it >= draft.nextDueDate) { "End date must be on or after the next due date" }
        }
        val now = System.currentTimeMillis()
        val anchorDay = if (old != null && old.frequency == draft.frequency.name) old.anchorDay else Utils.recurrenceAnchorDay(draft.nextDueDate)
        val item = RecurringRuleEntity(
            id = old?.id ?: Utils.id(), profileId = profileId, name = draft.name.trim().take(60),
            type = draft.type.name, accountId = validated.account.id,
            destinationAccountId = validated.destination?.id, categoryId = validated.category?.id,
            amountMinor = draft.amountMinor, currencyCode = validated.account.currencyCode,
            destinationAmountMinor = received, destinationCurrencyCode = validated.destination?.currencyCode,
            transferFeeMinor = draft.transferFeeMinor,
            payee = draft.payee.trim().take(80), note = draft.note.trim().take(500),
            tagsCsv = Utils.normaliseTags(draft.tags).joinToString(", "), frequency = draft.frequency.name,
            intervalCount = draft.intervalCount, anchorDay = anchorDay, monthEndMode = draft.monthEndMode.name,
            postingMode = draft.postingMode.name, occurrencesRemaining = draft.occurrencesRemaining,
            nextDueDate = draft.nextDueDate, endDate = draft.endDate?.trim()?.ifBlank { null }, isActive = draft.active,
            createdAt = old?.createdAt ?: now, updatedAt = now
        )
        if (old == null) db.recurring().insert(item) else db.recurring().update(item)
        audit(profileId, "RECURRING", item.id, if (old == null) "CREATED" else "UPDATED")
    }

    suspend fun deleteRecurring(item: RecurringRuleEntity) = db.withTransaction {
        db.recurring().delete(item)
        audit(item.profileId, "RECURRING", item.id, "DELETED")
    }

    suspend fun setRecurringActive(item: RecurringRuleEntity, active: Boolean) = db.withTransaction {
        val current = db.recurring().get(item.id) ?: error("Recurring rule not found")
        db.recurring().update(current.copy(isActive = active, updatedAt = System.currentTimeMillis()))
        audit(current.profileId, "RECURRING", current.id, if (active) "RESUMED" else "PAUSED")
    }

    suspend fun skipRecurring(item: RecurringRuleEntity) = db.withTransaction {
        val current = db.recurring().get(item.id) ?: error("Recurring rule not found")
        val next = nextRuleDate(current)
        val active = ruleRemainsActive(current, next)
        db.recurring().update(current.copy(nextDueDate = next, isActive = active, updatedAt = System.currentTimeMillis()))
        audit(current.profileId, "RECURRING", current.id, "SKIPPED_${current.nextDueDate}")
    }

    suspend fun runRecurringNow(item: RecurringRuleEntity, date: String = LocalDate.now().toString()): String = db.withTransaction {
        require(Utils.validDate(date) == null) { "Invalid posting date" }
        val current = db.recurring().get(item.id) ?: error("Recurring rule not found")
        require(db.transactions().recurringOccurrenceCount(current.id, date) == 0) { "This rule already created an entry for $date" }
        val id = postRecurringOccurrence(current, date)
        val next = nextRuleDate(current)
        val remaining = current.occurrencesRemaining?.minus(1)
        val active = ruleRemainsActive(current.copy(occurrencesRemaining = remaining), next)
        db.recurring().update(current.copy(nextDueDate = next, occurrencesRemaining = remaining, isActive = active, updatedAt = System.currentTimeMillis()))
        audit(current.profileId, "RECURRING", current.id, "RUN_NOW_$date")
        id
    }

    suspend fun postDueRecurring(today: String = LocalDate.now().toString()): Int {
        require(Utils.validDate(today) == null) { "Invalid processing date" }
        val profileId = db.profiles().get()?.id ?: return 0
        var created = 0
        db.withTransaction {
            val initial = db.recurring().dueAuto(profileId, today)
            initial.forEach { original ->
                var rule = original
                var safety = 0
                while (rule.isActive && rule.nextDueDate <= today && safety < 366) {
                    if (db.transactions().recurringOccurrenceCount(rule.id, rule.nextDueDate) == 0) {
                        postRecurringOccurrence(rule, rule.nextDueDate)
                        created++
                    }
                    safety++
                    val next = nextRuleDate(rule)
                    val remaining = rule.occurrencesRemaining?.minus(1)
                    val active = ruleRemainsActive(rule.copy(occurrencesRemaining = remaining), next)
                    rule = rule.copy(nextDueDate = next, occurrencesRemaining = remaining, isActive = active, updatedAt = System.currentTimeMillis())
                    db.recurring().update(rule)
                }
            }
            if (created > 0) audit(profileId, "RECURRING", profileId, "POSTED_$created")
        }
        return created
    }

    private suspend fun postRecurringOccurrence(rule: RecurringRuleEntity, date: String): String {
        val validated = validateTransaction(
            TransactionType.from(rule.type),
            rule.accountId,
            rule.destinationAccountId,
            rule.categoryId,
            rule.amountMinor,
            allowedArchivedAccountIds = setOfNotNull(rule.accountId, rule.destinationAccountId),
            allowedArchivedCategoryId = rule.categoryId
        )
        validateTransactionDate(date, validated)
        val now = System.currentTimeMillis()
        val transactionId = Utils.id()
        val tx = TransactionEntity(
            id = transactionId, profileId = rule.profileId, type = rule.type, accountId = validated.account.id,
            destinationAccountId = validated.destination?.id, categoryId = validated.category?.id,
            amountMinor = rule.amountMinor, currencyCode = rule.currencyCode,
            destinationAmountMinor = rule.destinationAmountMinor, destinationCurrencyCode = rule.destinationCurrencyCode,
            transferFeeMinor = rule.transferFeeMinor, transactionDate = date, payee = rule.payee, note = rule.note,
            isCleared = true, recurringRuleId = rule.id, isDeleted = false, deletedAt = null, createdAt = now, updatedAt = now
        )
        db.transactions().insert(tx)
        replaceTags(rule.profileId, transactionId, rule.tagsCsv.split(','))
        insertRevision(tx, "CREATED_FROM_RECURRING")
        return transactionId
    }

    private fun nextRuleDate(rule: RecurringRuleEntity): String = Utils.nextRecurrence(
        rule.nextDueDate, RecurrenceFrequency.from(rule.frequency), rule.intervalCount, rule.anchorDay,
        MonthEndMode.from(rule.monthEndMode)
    )

    private fun ruleRemainsActive(rule: RecurringRuleEntity, next: String): Boolean {
        val remaining = rule.occurrencesRemaining
        val endDate = rule.endDate
        val remainingOk = remaining == null || remaining > 0
        val endOk = endDate == null || next <= endDate
        return rule.isActive && remainingOk && endOk
    }

    suspend fun saveBudget(draft: BudgetDraft) = db.withTransaction {
        val profileId = db.profiles().get()?.id ?: error("Profile not found")
        require(Utils.validName(draft.name, 60) == null) { Utils.validName(draft.name, 60) ?: "Invalid budget name" }
        require(draft.amountMinor > 0) { "Budget amount must be greater than zero" }
        require(Utils.validCurrency(draft.currencyCode) == null) { "Choose a valid currency" }
        require(Utils.validDate(draft.anchorDate) == null) { "Choose a valid start date" }
        require(draft.repeatInterval in 1..24) { "Repeat interval must be between 1 and 24" }
        val customEnd = draft.customEndDate?.trim()?.ifBlank { null }
        if (draft.periodType == BudgetPeriodType.CUSTOM) {
            require(customEnd != null && Utils.validDate(customEnd) == null && customEnd >= draft.anchorDate) {
                "Custom end date must be on or after the start date"
            }
        }
        val repeatUntil = draft.repeatUntil?.trim()?.ifBlank { null }
        repeatUntil?.let {
            require(Utils.validDate(it) == null && it >= draft.anchorDate) {
                "Repeat-until date must be on or after the start date"
            }
        }
        val old = draft.id?.let { db.budgets().get(it) }
        val category = draft.categoryId?.let { db.categories().get(it) }
        require(draft.categoryId == null || category != null) { "Choose an expense category" }
        require(category == null || category.kind == CategoryKind.EXPENSE.name) { "Budgets can only use expense categories" }
        require(category == null || !category.isArchived || category.id == old?.categoryId) { "Choose an active expense category" }

        require(db.budgets().duplicateNameCount(profileId, draft.name.trim(), old?.id ?: "") == 0) {
            "A budget with this name already exists"
        }
        if (old != null && db.budgets().periodCount(old.id) > 0) {
            require(
                old.categoryId == draft.categoryId &&
                    old.currencyCode == draft.currencyCode &&
                    old.periodType == draft.periodType.name &&
                    old.anchorDate == draft.anchorDate &&
                    old.customEndDate == customEnd &&
                    old.repeatInterval == draft.repeatInterval &&
                    old.carryoverMode == draft.carryover.name
            ) { "Period, category, currency and carry-over cannot change after budget history exists. Create a new budget instead." }
        }
        val now = System.currentTimeMillis()
        val item = BudgetEntity(
            id = old?.id ?: Utils.id(),
            profileId = profileId,
            name = draft.name.trim(),
            categoryId = category?.id,
            amountMinor = draft.amountMinor,
            currencyCode = draft.currencyCode,
            periodType = draft.periodType.name,
            anchorDate = draft.anchorDate,
            customEndDate = customEnd,
            repeatInterval = draft.repeatInterval,
            repeatUntil = repeatUntil,
            carryoverMode = draft.carryover.name,
            isActive = draft.active,
            createdAt = old?.createdAt ?: now,
            updatedAt = now
        )
        if (old == null) db.budgets().insert(item) else db.budgets().update(item)
        ensureBudgetPeriodsInternal(profileId, LocalDate.now().toString(), setOf(item.id))
        audit(profileId, "BUDGET", item.id, if (old == null) "CREATED" else "UPDATED")
    }

    suspend fun deleteBudget(item: BudgetEntity) = db.withTransaction {
        db.budgets().delete(item)
        audit(item.profileId, "BUDGET", item.id, "DELETED")
    }

    suspend fun ensureBudgetPeriods(referenceDate: String = LocalDate.now().toString()): Int {
        require(Utils.validDate(referenceDate) == null) { "Invalid budget reference date" }
        val profileId = db.profiles().get()?.id ?: return 0
        return db.withTransaction { ensureBudgetPeriodsInternal(profileId, referenceDate, null) }
    }

    private suspend fun ensureBudgetPeriodsInternal(
        profileId: String,
        referenceDate: String,
        onlyBudgetIds: Set<String>?
    ): Int {
        val budgets = db.budgets().getAll(profileId).filter { onlyBudgetIds == null || it.id in onlyBudgetIds }
        val transactions = db.transactions().getAll(profileId)
        val splits = db.transactions().getAllSplits()
        val categories = db.categories().getAll(profileId)
        var inserted = 0
        budgets.forEach { budget ->
            val target = BudgetPeriodMath.windowFor(budget, referenceDate) ?: return@forEach
            val existing = db.budgets().getPeriods(budget.id).sortedBy { it.periodStart }
            var previous = existing.lastOrNull()
            var window = if (previous == null) BudgetPeriodMath.firstWindow(budget)
            else BudgetPeriodMath.nextWindow(budget, DateWindow(previous.periodStart, previous.periodEnd))
            var safety = 0
            while (window != null && window.start <= target.start && safety < 600) {
                val carryIn = previous?.let { prior ->
                    val spent = budgetSpent(budget, prior.periodStart, prior.periodEnd, transactions, categories, splits)
                    val remainder = prior.allocatedMinor + prior.carryInMinor - spent
                    when (BudgetCarryover.from(budget.carryoverMode)) {
                        BudgetCarryover.OFF -> 0L
                        BudgetCarryover.POSITIVE_ONLY -> max(0L, remainder)
                        BudgetCarryover.FULL -> remainder
                    }
                } ?: 0L
                val period = BudgetPeriodEntity(
                    id = Utils.id(), budgetId = budget.id,
                    periodStart = window.start, periodEnd = window.end,
                    allocatedMinor = budget.amountMinor, carryInMinor = carryIn,
                    createdAt = System.currentTimeMillis()
                )
                if (db.budgets().insertPeriod(period) != -1L) {
                    inserted++
                    previous = period
                } else {
                    previous = db.budgets().getPeriod(budget.id, window.start)
                }
                window = BudgetPeriodMath.nextWindow(budget, window)
                safety++
            }
        }
        if (inserted > 0) audit(profileId, "BUDGET_PERIOD", profileId, "GENERATED_$inserted")
        return inserted
    }

    private fun budgetSpent(
        budget: BudgetEntity,
        from: String,
        to: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        splits: List<TransactionSplitEntity>
    ): Long {
        val categoryIds = if (budget.categoryId == null) null else {
            categories.filter { it.id == budget.categoryId || it.parentId == budget.categoryId }.map { it.id }.toSet()
        }
        val splitsByTransaction = splits.groupBy { it.transactionId }
        return transactions.asSequence()
            .filter { it.isCleared && it.currencyCode == budget.currencyCode && it.transactionDate in from..to }
            .sumOf { tx ->
                when (TransactionType.from(tx.type)) {
                    TransactionType.EXPENSE -> {
                        val txSplits = splitsByTransaction[tx.id].orEmpty()
                        if (txSplits.isEmpty()) {
                            if (categoryIds == null || tx.categoryId in categoryIds) tx.amountMinor else 0L
                        } else {
                            txSplits.filter { categoryIds == null || it.categoryId in categoryIds }.sumOf { it.amountMinor }
                        }
                    }
                    TransactionType.TRANSFER -> if (categoryIds == null) tx.transferFeeMinor else 0L
                    TransactionType.INCOME -> 0L
                }
            }
    }

    fun observeWorkspaceItems(profileId: String): Flow<List<WorkspaceItemEntity>> = db.workspace().observeItems(profileId)
    fun observeWorkspaceEvents(): Flow<List<WorkspaceEventEntity>> = db.workspace().observeEvents()
    fun observeSavedFilters(profileId: String): Flow<List<SavedFilterEntity>> = db.savedFilters().observeAll(profileId)

    suspend fun saveWorkspaceItem(draft: WorkspaceItemDraft) = db.withTransaction {
        val profileId = db.profiles().get()?.id ?: error("Profile not found")
        require(Utils.validName(draft.title, 70) == null) { Utils.validName(draft.title, 70) ?: "Invalid title" }
        require(draft.amountMinor >= 0 && draft.currentMinor >= 0 && draft.secondaryMinor >= 0) { "Values cannot be negative" }
        require(Utils.validCurrency(draft.currencyCode) == null) { "Choose a valid currency" }
        if (draft.type == WorkspaceType.CURRENCY_RATE) {
            require(Utils.validCurrency(draft.secondaryCode) == null) { "Choose a valid quote currency" }
            require(draft.secondaryCode != draft.currencyCode) { "Choose two different currencies" }
            require(draft.currentMinor > 0) { "Enter a rate greater than zero" }
        }
        if (draft.type == WorkspaceType.SUBSCRIPTION && draft.secondaryCode.isNotBlank()) {
            require(BillingCadence.entries.any { it.name.equals(draft.secondaryCode, ignoreCase = true) }) {
                "Choose a valid billing cycle"
            }
        }
        draft.startDate?.takeIf { it.isNotBlank() }?.let { require(Utils.validDate(it) == null) { "Invalid start date" } }
        draft.dueDate?.takeIf { it.isNotBlank() }?.let { require(Utils.validDate(it) == null) { "Invalid due date" } }
        val old = draft.id?.let { db.workspace().getItem(it) }
        require(db.workspace().duplicateTitleCount(profileId, draft.type.name, draft.title.trim(), old?.id ?: "") == 0) {
            "A ${draft.type.label.lowercase()} item with this title already exists"
        }
        draft.accountId?.let { accountId ->
            val account = db.accounts().get(accountId) ?: error("Selected account no longer exists")
            require(!account.isArchived || account.id == old?.accountId) { "Choose an active account" }
            require(account.currencyCode == draft.currencyCode) {
                "Choose an account that uses ${draft.currencyCode}, or change this item's currency"
            }
        }
        draft.categoryId?.let { categoryId ->
            val category = db.categories().get(categoryId) ?: error("Selected category no longer exists")
            require(category.kind == CategoryKind.EXPENSE.name) { "Workspace posting uses an expense category" }
            require(!category.isArchived || category.id == old?.categoryId) { "Choose an active expense category" }
        }
        val now = System.currentTimeMillis()
        val item = WorkspaceItemEntity(
            id = old?.id ?: Utils.id(), profileId = profileId, type = draft.type.name,
            title = draft.title.trim(), amountMinor = draft.amountMinor,
            currentMinor = draft.currentMinor, secondaryMinor = draft.secondaryMinor,
            currencyCode = draft.currencyCode,
            secondaryCode = if (draft.type == WorkspaceType.SUBSCRIPTION) {
                BillingCadence.from(draft.secondaryCode).name
            } else draft.secondaryCode.trim().uppercase(Locale.ROOT),
            startDate = draft.startDate?.trim()?.ifBlank { null }, dueDate = draft.dueDate?.trim()?.ifBlank { null },
            accountId = draft.accountId, categoryId = draft.categoryId,
            linkedTransactionId = draft.linkedTransactionId ?: old?.linkedTransactionId,
            status = draft.status.name, note = draft.note.trim().take(800), metadata = draft.metadata.trim().take(300),
            createdAt = old?.createdAt ?: now, updatedAt = now
        )
        if (old == null) db.workspace().insertItem(item) else db.workspace().updateItem(item)
        if (draft.type in setOf(
                WorkspaceType.GOAL, WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY,
                WorkspaceType.EMI, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST,
                WorkspaceType.SHARED_EXPENSE, WorkspaceType.LOYALTY
            )) {
            recalculateWorkspaceItem(item.id)
        }
        audit(profileId, "WORKSPACE_${draft.type.name}", item.id, if (old == null) "CREATED" else "UPDATED")
    }

    suspend fun deleteWorkspaceItem(item: WorkspaceItemEntity) = db.withTransaction {
        val current = db.workspace().getItem(item.id) ?: return@withTransaction
        db.workspace().deleteItem(current)
        audit(current.profileId, "WORKSPACE_${current.type}", current.id, "DELETED")
    }

    suspend fun setWorkspaceStatus(item: WorkspaceItemEntity, status: WorkspaceStatus) = db.withTransaction {
        val current = db.workspace().getItem(item.id) ?: error("Item no longer exists")
        db.workspace().updateItem(current.copy(status = status.name, updatedAt = System.currentTimeMillis()))
        audit(current.profileId, "WORKSPACE_${current.type}", current.id, "STATUS_${status.name}")
    }

    suspend fun saveWorkspaceEvent(draft: WorkspaceEventDraft) = db.withTransaction {
        val item = db.workspace().getItem(draft.itemId) ?: error("Item no longer exists")
        require(Utils.validName(draft.label, 80) == null) { Utils.validName(draft.label, 80) ?: "Enter a label" }
        val kind = draft.kind.trim().uppercase(Locale.ROOT)
        if (kind in setOf("CONTRIBUTION", "PAYMENT", "SETTLEMENT", "POINTS")) {
            require(draft.amountMinor > 0) { "Amount must be greater than zero" }
        } else {
            require(draft.amountMinor >= 0) { "Amount cannot be negative" }
        }
        require(Utils.validDate(draft.eventDate) == null) { "Invalid event date" }
        val remainingForEvent = when (WorkspaceType.from(item.type)) {
            WorkspaceType.BILL -> (item.amountMinor - item.currentMinor).coerceAtLeast(0L)
            WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI -> item.currentMinor.coerceAtLeast(0L)
            WorkspaceType.SHARED_EXPENSE -> (item.amountMinor - item.currentMinor).coerceAtLeast(0L)
            else -> null
        }
        if (remainingForEvent != null && kind in setOf("PAYMENT", "SETTLEMENT")) {
            require(remainingForEvent > 0L) { "This item is already complete" }
            require(draft.amountMinor <= remainingForEvent) { "Amount cannot exceed the remaining balance" }
        }
        val event = WorkspaceEventEntity(
            id = Utils.id(), itemId = item.id, kind = kind,
            label = draft.label.trim(), amountMinor = draft.amountMinor, eventDate = draft.eventDate,
            isCompleted = draft.isCompleted, note = draft.note.trim().take(300), createdAt = System.currentTimeMillis()
        )
        db.workspace().insertEvent(event)
        if (draft.postToLedger) {
            require(kind == "PAYMENT") { "Only payment activity can be posted to the ledger" }
            val accountId = item.accountId ?: error("Edit this item and choose an account first")
            val categoryId = item.categoryId ?: error("Edit this item and choose an expense category first")
            val validated = validateTransaction(TransactionType.EXPENSE, accountId, null, categoryId, draft.amountMinor)
            validateTransactionDate(draft.eventDate, validated)
            val transaction = TransactionEntity(
                id = Utils.id(), profileId = item.profileId, type = TransactionType.EXPENSE.name,
                accountId = validated.account.id, destinationAccountId = null, categoryId = validated.category?.id,
                amountMinor = draft.amountMinor, currencyCode = validated.account.currencyCode,
                destinationAmountMinor = 0, destinationCurrencyCode = null, transferFeeMinor = 0,
                transactionDate = draft.eventDate, payee = item.title.take(80),
                note = listOf("Payment recorded from ${WorkspaceType.from(item.type).label}", draft.note)
                    .filter { it.isNotBlank() }.joinToString(" - ").take(500),
                isCleared = true, recurringRuleId = null, isDeleted = false, deletedAt = null,
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            db.transactions().insert(transaction)
            replaceTags(item.profileId, transaction.id, listOf("Planner", WorkspaceType.from(item.type).label))
            insertRevision(transaction, "CREATED_FROM_WORKSPACE_PAYMENT")
            audit(item.profileId, "TRANSACTION", transaction.id, "CREATED_FROM_WORKSPACE_PAYMENT")
        }
        recalculateWorkspaceItem(item.id)
        val updated = db.workspace().getItem(item.id)
        val repeatingPaymentTypes = setOf(
            WorkspaceType.EMI.name, WorkspaceType.LOAN.name,
            WorkspaceType.DEBT.name, WorkspaceType.LIABILITY.name
        )
        if (kind == "PAYMENT" && updated != null && updated.type in repeatingPaymentTypes &&
            updated.status == WorkspaceStatus.ACTIVE.name && !updated.dueDate.isNullOrBlank()
        ) {
            val due = runCatching { LocalDate.parse(updated.dueDate) }.getOrNull()
            val paidOn = runCatching { LocalDate.parse(draft.eventDate) }.getOrNull()
            if (due != null && paidOn != null && !due.isAfter(paidOn)) {
                db.workspace().updateItem(updated.copy(dueDate = due.plusMonths(1).toString(), updatedAt = System.currentTimeMillis()))
            }
        }
        audit(item.profileId, "WORKSPACE_EVENT", event.id, "${event.kind}_CREATED")
    }

    suspend fun setWorkspaceEventCompleted(event: WorkspaceEventEntity, completed: Boolean) = db.withTransaction {
        db.workspace().updateEvent(event.copy(isCompleted = completed))
        recalculateWorkspaceItem(event.itemId)
    }

    suspend fun deleteWorkspaceEvent(event: WorkspaceEventEntity) = db.withTransaction {
        db.workspace().deleteEvent(event)
        recalculateWorkspaceItem(event.itemId)
    }

    private suspend fun recalculateWorkspaceItem(itemId: String) {
        val item = db.workspace().getItem(itemId) ?: return
        val events = db.workspace().getEvents(itemId)
        val type = WorkspaceType.from(item.type)
        var amount = item.amountMinor
        var current = item.currentMinor
        when (type) {
            WorkspaceType.GOAL -> current = events.filter { it.kind == "CONTRIBUTION" }.sumOf { it.amountMinor }
            WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI ->
                current = max(0L, item.amountMinor - events.filter { it.kind == "PAYMENT" }.sumOf { it.amountMinor })
            WorkspaceType.BILL -> current = events.filter { it.kind == "PAYMENT" }.sumOf { it.amountMinor }
            WorkspaceType.SHOPPING_LIST -> {
                amount = events.filter { it.kind == "ITEM" }.sumOf { it.amountMinor }
                current = events.filter { it.kind == "ITEM" && it.isCompleted }.sumOf { it.amountMinor }
            }
            WorkspaceType.SHARED_EXPENSE -> current = events.filter { it.kind == "SETTLEMENT" }.sumOf { it.amountMinor }
            WorkspaceType.INVESTMENT, WorkspaceType.MUTUAL_FUND, WorkspaceType.GOLD,
            WorkspaceType.FIXED_DEPOSIT, WorkspaceType.PPF, WorkspaceType.EPF, WorkspaceType.CRYPTO,
            WorkspaceType.ASSET, WorkspaceType.CREDIT -> {
                current = events.filter { it.kind == "VALUE" }
                    .maxWithOrNull(compareBy<WorkspaceEventEntity> { it.eventDate }.thenBy { it.createdAt })
                    ?.amountMinor ?: item.currentMinor
            }
            WorkspaceType.LOYALTY -> current = events.filter { it.kind == "POINTS" }.sumOf { it.amountMinor }
            else -> Unit
        }
        val eventDriven = type in setOf(
            WorkspaceType.GOAL, WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY,
            WorkspaceType.EMI, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST,
            WorkspaceType.SHARED_EXPENSE, WorkspaceType.LOYALTY
        )
        val targetReached = when (type) {
            WorkspaceType.GOAL, WorkspaceType.BILL, WorkspaceType.SHOPPING_LIST,
            WorkspaceType.SHARED_EXPENSE, WorkspaceType.LOYALTY -> amount > 0 && current >= amount
            WorkspaceType.DEBT, WorkspaceType.LOAN, WorkspaceType.LIABILITY, WorkspaceType.EMI -> current == 0L
            else -> false
        }
        val status = when {
            item.status == WorkspaceStatus.ARCHIVED.name -> item.status
            targetReached -> WorkspaceStatus.COMPLETED.name
            eventDriven && item.status == WorkspaceStatus.COMPLETED.name -> WorkspaceStatus.ACTIVE.name
            else -> item.status
        }
        db.workspace().updateItem(item.copy(amountMinor = amount, currentMinor = current, status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun postWorkspaceItem(itemId: String): String = db.withTransaction {
        val item = db.workspace().getItem(itemId) ?: error("Item no longer exists")
        val type = WorkspaceType.from(item.type)
        require(item.status in setOf(WorkspaceStatus.ACTIVE.name, WorkspaceStatus.PAUSED.name)) { "Restore or reactivate this item before posting it" }
        require(type in setOf(
            WorkspaceType.PLANNED_PAYMENT, WorkspaceType.BILL, WorkspaceType.EMI,
            WorkspaceType.SUBSCRIPTION, WorkspaceType.SHOPPING_LIST
        )) {
            "This item cannot be posted to the ledger"
        }
        val amount = if (type == WorkspaceType.SHOPPING_LIST) {
            db.workspace().getEvents(item.id).filter { it.kind == "ITEM" && it.isCompleted }.sumOf { it.amountMinor }
        } else item.amountMinor
        val accountId = item.accountId ?: error("Edit this item and choose an account first")
        val categoryId = item.categoryId ?: error("Edit this item and choose an expense category first")
        val validated = validateTransaction(TransactionType.EXPENSE, accountId, null, categoryId, amount)
        val date = LocalDate.now().toString()
        validateTransactionDate(date, validated)
        val now = System.currentTimeMillis()
        val transaction = TransactionEntity(
            id = Utils.id(), profileId = item.profileId, type = TransactionType.EXPENSE.name,
            accountId = validated.account.id, destinationAccountId = null, categoryId = validated.category?.id,
            amountMinor = amount, currencyCode = validated.account.currencyCode,
            destinationAmountMinor = 0, destinationCurrencyCode = null, transferFeeMinor = 0,
            transactionDate = date, payee = item.title.take(80),
            note = listOf("Posted from ${type.label}", item.note).filter { it.isNotBlank() }.joinToString(" - ").take(500),
            isCleared = true, recurringRuleId = null, isDeleted = false, deletedAt = null,
            createdAt = now, updatedAt = now
        )
        db.transactions().insert(transaction)
        replaceTags(item.profileId, transaction.id, listOf("Workspace", type.label))
        insertRevision(transaction, "CREATED_FROM_WORKSPACE")
        val nextDue = if (type == WorkspaceType.SUBSCRIPTION) {
            item.dueDate?.let { due ->
                runCatching { Utils.nextBillingDate(due, BillingCadence.from(item.secondaryCode)) }.getOrNull()
            }
        } else item.dueDate
        val nextStatus = if (type == WorkspaceType.SUBSCRIPTION) WorkspaceStatus.ACTIVE.name else WorkspaceStatus.COMPLETED.name
        db.workspace().updateItem(item.copy(linkedTransactionId = transaction.id, dueDate = nextDue, status = nextStatus, updatedAt = now))
        audit(item.profileId, "WORKSPACE_${item.type}", item.id, "POSTED_${transaction.id}")
        transaction.id
    }

    suspend fun saveSavedFilter(name: String, filter: TransactionFilter) = db.withTransaction {
        val profileId = db.profiles().get()?.id ?: error("Profile not found")
        require(Utils.validName(name, 40) == null) { Utils.validName(name, 40) ?: "Invalid filter name" }
        require(db.savedFilters().duplicateNameCount(profileId, name.trim(), "") == 0) { "A saved filter with this name already exists" }
        db.savedFilters().insert(SavedFilterEntity(
            id = Utils.id(), profileId = profileId, name = name.trim(), query = filter.query,
            type = filter.type?.name, accountId = filter.accountId, categoryId = filter.categoryId,
            tagId = filter.tagId, currencyCode = filter.currencyCode, minMinor = filter.minMinor,
            maxMinor = filter.maxMinor, hasAttachment = filter.hasAttachment, cleared = filter.cleared,
            recurringOnly = filter.recurringOnly, fromDate = filter.fromDate, toDate = filter.toDate,
            sort = filter.sort.name, createdAt = System.currentTimeMillis()
        ))
        audit(profileId, "SAVED_FILTER", profileId, "CREATED_${name.trim()}")
    }

    suspend fun deleteSavedFilter(item: SavedFilterEntity) = db.withTransaction {
        db.savedFilters().delete(item)
        audit(item.profileId, "SAVED_FILTER", item.id, "DELETED")
    }

    suspend fun duplicateTransaction(item: TransactionEntity) {
        val current = db.transactions().get(item.id) ?: error("Transaction no longer exists")
        require(!current.isDeleted) { "Restore this transaction before duplicating it" }
        val splits = db.transactions().getSplits(current.id).map { TransactionSplitDraft(it.categoryId ?: "", it.amountMinor, it.memo) }
            .filter { it.categoryId.isNotBlank() }
        val tagNames = db.tags().getForTransaction(current.id).map { it.name }
        saveTransaction(TransactionDraft(
            id = null, type = TransactionType.from(current.type), accountId = current.accountId,
            destinationAccountId = current.destinationAccountId, categoryId = current.categoryId,
            amountMinor = current.amountMinor, destinationAmountMinor = current.destinationAmountMinor.takeIf { it > 0 },
            transferFeeMinor = current.transferFeeMinor, splits = splits, date = LocalDate.now().toString(),
            payee = current.payee, note = current.note, tags = tagNames + "Duplicated",
            cleared = current.isCleared, attachmentUris = emptyList(), removeExistingAttachments = false
        ))
    }

    suspend fun adjustAccountBalance(account: AccountEntity, targetMinor: Long, reason: String) = db.withTransaction {
        require(reason.trim().length in 3..180) { "Add a short reason for the adjustment" }
        val currentAccount = db.accounts().get(account.id) ?: error("Account no longer exists")
        require(!currentAccount.isArchived) { "Restore the account before adjusting it" }
        val transactions = db.transactions().getAll(currentAccount.profileId)
        val current = LedgerMath.balances(listOf(currentAccount), transactions)[currentAccount.id] ?: currentAccount.openingBalanceMinor
        val difference = targetMinor - current
        require(difference != 0L) { "The account already has this balance" }
        val now = System.currentTimeMillis()
        val transaction = TransactionEntity(
            id = Utils.id(), profileId = currentAccount.profileId,
            type = if (difference > 0) TransactionType.INCOME.name else TransactionType.EXPENSE.name,
            accountId = currentAccount.id, destinationAccountId = null, categoryId = null,
            amountMinor = kotlin.math.abs(difference), currencyCode = currentAccount.currencyCode,
            destinationAmountMinor = 0, destinationCurrencyCode = null, transferFeeMinor = 0,
            transactionDate = LocalDate.now().toString(), payee = "Balance adjustment",
            note = reason.trim().take(180), isCleared = true, recurringRuleId = null,
            isDeleted = false, deletedAt = null, createdAt = now, updatedAt = now
        )
        db.transactions().insert(transaction)
        insertRevision(transaction, "BALANCE_ADJUSTMENT")
        audit(currentAccount.profileId, "ACCOUNT", currentAccount.id, "BALANCE_ADJUSTED_${transaction.id}")
    }

    suspend fun mergeCategory(source: CategoryEntity, target: CategoryEntity) = db.withTransaction {
        require(source.id != target.id) { "Choose a different target category" }
        val currentSource = db.categories().get(source.id) ?: error("Source category no longer exists")
        val currentTarget = db.categories().get(target.id) ?: error("Target category no longer exists")
        require(currentSource.kind == currentTarget.kind) { "Categories must have the same type" }
        require(!currentTarget.isArchived) { "Choose an active target category" }
        val now = System.currentTimeMillis()
        db.categories().moveTransactions(currentSource.id, currentTarget.id, now)
        db.categories().moveSplits(currentSource.id, currentTarget.id)
        db.categories().moveRecurring(currentSource.id, currentTarget.id, now)
        db.categories().moveBudgets(currentSource.id, currentTarget.id, now)
        if (currentTarget.parentId == null) db.categories().moveChildren(currentSource.id, currentTarget.id, now)
        db.categories().update(currentSource.copy(isArchived = true, updatedAt = now))
        audit(currentSource.profileId, "CATEGORY", currentSource.id, "MERGED_INTO_${currentTarget.id}")
    }

    suspend fun scanAttachmentIntegrity(): IntegrityReport {
        val rows = db.attachments().getAll()
        val missing = rows.filter { attachment ->
            val file = java.io.File(attachment.localPath)
            !file.isFile || file.length() <= 0
        }.map { it.displayName }
        return IntegrityReport(rows.size, rows.size - missing.size, missing)
    }

    suspend fun updateSettings(theme: ThemeMode, currency: String) {
        require(Utils.validCurrency(currency) == null) { "Choose a valid currency" }
        db.withTransaction {
            val profile = db.profiles().get() ?: error("Profile not found")
            db.profiles().update(profile.copy(baseCurrency = currency, updatedAt = System.currentTimeMillis()))
            audit(profile.id, "PROFILE", profile.id, "SETTINGS_UPDATED")
        }
        settings.setPreferences(theme, currency)
    }

    private data class ValidatedTransaction(
        val account: AccountEntity,
        val destination: AccountEntity?,
        val category: CategoryEntity?
    )

    private suspend fun validateTransaction(
        type: TransactionType,
        accountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        amountMinor: Long,
        allowedArchivedAccountIds: Set<String> = emptySet(),
        allowedArchivedCategoryId: String? = null,
        allowMissingCategory: Boolean = false
    ): ValidatedTransaction {
        require(amountMinor > 0) { "Amount must be greater than zero" }
        val account = db.accounts().get(accountId) ?: error("Choose an account")
        require(!account.isArchived || account.id in allowedArchivedAccountIds) { "Choose an active account" }
        return when (type) {
            TransactionType.TRANSFER -> {
                val destination = destinationAccountId?.let { db.accounts().get(it) } ?: error("Choose a destination account")
                require(destination.id != account.id) { "Source and destination accounts must be different" }
                require(!destination.isArchived || destination.id in allowedArchivedAccountIds) { "Choose an active destination account" }
                ValidatedTransaction(account, destination, null)
            }
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                val category = categoryId?.let { db.categories().get(it) }
                require(category != null || allowMissingCategory) { "Choose a category" }
                if (category != null) {
                    require(!category.isArchived || category.id == allowedArchivedCategoryId) { "Choose an active category" }
                    val expected = if (type == TransactionType.EXPENSE) CategoryKind.EXPENSE.name else CategoryKind.INCOME.name
                    require(category.kind == expected) { "Choose a matching ${type.label.lowercase()} category" }
                }
                ValidatedTransaction(account, null, category)
            }
        }
    }

    private fun destinationAmount(
        type: TransactionType,
        source: AccountEntity,
        destination: AccountEntity?,
        sentMinor: Long,
        requestedMinor: Long?
    ): Long {
        if (type != TransactionType.TRANSFER) return 0L
        val target = destination ?: error("Choose a destination account")
        val value = if (source.currencyCode == target.currencyCode) sentMinor else requestedMinor ?: 0L
        require(value > 0) { "Enter the amount received in ${target.currencyCode}" }
        return value
    }

    private suspend fun validateSplits(
        type: TransactionType,
        totalMinor: Long,
        splits: List<TransactionSplitDraft>,
        old: TransactionEntity?
    ) {
        require(type != TransactionType.TRANSFER || splits.isEmpty()) { "Transfers cannot be split into categories" }
        if (splits.isEmpty()) return
        require(splits.size in 2..20) { "Use 2 to 20 split lines" }
        require(splits.all { it.amountMinor > 0 }) { "Every split amount must be greater than zero" }
        require(splits.sumOf { it.amountMinor } == totalMinor) { "Split amounts must equal the transaction total" }
        val expected = if (type == TransactionType.EXPENSE) CategoryKind.EXPENSE.name else CategoryKind.INCOME.name
        val oldSplitIds = old?.id?.let { db.transactions().getSplits(it).mapNotNull(TransactionSplitEntity::categoryId).toSet() }.orEmpty()
        splits.forEach { split ->
            val category = db.categories().get(split.categoryId) ?: error("A split category no longer exists")
            require(category.kind == expected) { "Every split category must match the transaction type" }
            require(!category.isArchived || category.id in oldSplitIds) { "Choose active split categories" }
        }
    }

    private fun validateTransactionDate(date: String, validated: ValidatedTransaction) {
        require(date >= validated.account.openingDate) {
            "Date cannot be before ${validated.account.name}'s opening balance date (${validated.account.openingDate})"
        }
        validated.destination?.let { destination ->
            require(date >= destination.openingDate) {
                "Date cannot be before ${destination.name}'s opening balance date (${destination.openingDate})"
            }
        }
    }

    private suspend fun replaceTags(profileId: String, transactionId: String, rawTags: List<String>) {
        db.tags().deleteRefs(transactionId)
        Utils.normaliseTags(rawTags).forEach { name ->
            val tag = db.tags().find(profileId, name) ?: TagEntity(
                Utils.id(), profileId, name, System.currentTimeMillis()
            ).also { db.tags().insert(it) }
            db.tags().insertRef(TransactionTagCrossRef(transactionId, tag.id))
        }
    }

    private suspend fun insertRevision(item: TransactionEntity, action: String) {
        val summary = buildString {
            append(TransactionType.from(item.type).label)
            append(" • ")
            append(Utils.money(item.amountMinor, item.currencyCode))
            val destinationCurrency = item.destinationCurrencyCode
            if (item.type == TransactionType.TRANSFER.name && destinationCurrency != null) {
                append(" → ")
                append(Utils.money(item.destinationAmountMinor, destinationCurrency))
                if (item.transferFeeMinor > 0) append(" • fee ${Utils.money(item.transferFeeMinor, item.currencyCode)}")
            }
            append(" • ${item.transactionDate}")
            if (item.payee.isNotBlank()) append(" • ${item.payee}")
        }
        db.transactions().insertRevision(TransactionRevisionEntity(Utils.id(), item.id, action, summary.take(500), System.currentTimeMillis()))
    }

    private suspend fun audit(profileId: String, type: String, entityId: String, action: String) {
        db.audit().insert(AuditEventEntity(Utils.id(), profileId, type, entityId, action, System.currentTimeMillis()))
    }

    private fun defaultCategories(profileId: String, now: Long): List<CategoryEntity> {
        val expense = listOf(
            Triple("Food & dining", "food", 0xFFB45F36.toInt()),
            Triple("Transport", "transport", 0xFF315D8C.toInt()),
            Triple("Shopping", "shopping", 0xFF7B4D8E.toInt()),
            Triple("Bills & utilities", "bills", 0xFF8A5A00.toInt()),
            Triple("Health", "health", 0xFFB3261E.toInt()),
            Triple("Education", "education", 0xFF416A59.toInt()),
            Triple("Entertainment", "entertainment", 0xFF805500.toInt()),
            Triple("Other expense", "other", 0xFF6B6F72.toInt())
        )
        val income = listOf(
            Triple("Salary", "salary", 0xFF1F6F43.toInt()),
            Triple("Business", "business", 0xFF2F6B4F.toInt()),
            Triple("Gift", "gift", 0xFF547A5E.toInt()),
            Triple("Other income", "other", 0xFF6B6F72.toInt())
        )
        return buildList {
            expense.forEachIndexed { i, x -> add(CategoryEntity(Utils.id(), profileId, null, x.first, CategoryKind.EXPENSE.name, x.second, x.third, true, false, i, now, now)) }
            income.forEachIndexed { i, x -> add(CategoryEntity(Utils.id(), profileId, null, x.first, CategoryKind.INCOME.name, x.second, x.third, true, false, i, now, now)) }
        }
    }
}
