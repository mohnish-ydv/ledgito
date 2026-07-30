package com.mohnishraj.goldmineledger

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt LIMIT 1") fun observe(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profiles ORDER BY createdAt LIMIT 1") suspend fun get(): ProfileEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: ProfileEntity)
    @Update suspend fun update(item: ProfileEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE profileId=:profileId ORDER BY isArchived, sortOrder, name COLLATE NOCASE") fun observeAll(profileId: String): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE id=:id") suspend fun get(id: String): AccountEntity?
    @Query("SELECT COUNT(*) FROM accounts WHERE profileId=:profileId AND isArchived=0") fun observeActiveCount(profileId: String): Flow<Int>
    @Query("SELECT COALESCE(MAX(sortOrder),-1)+1 FROM accounts WHERE profileId=:profileId") suspend fun nextOrder(profileId: String): Int
    @Query("SELECT COUNT(*) FROM accounts WHERE profileId=:profileId AND name=:name COLLATE NOCASE AND id!=:excludedId") suspend fun duplicateNameCount(profileId: String, name: String, excludedId: String): Int
    @Query("SELECT (SELECT COUNT(*) FROM transactions WHERE accountId=:id OR destinationAccountId=:id) + (SELECT COUNT(*) FROM recurring_rules WHERE accountId=:id OR destinationAccountId=:id)") suspend fun referenceCount(id: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: AccountEntity)
    @Update suspend fun update(item: AccountEntity)
    @Delete suspend fun delete(item: AccountEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE profileId=:profileId ORDER BY kind DESC, isArchived, parentId IS NOT NULL, sortOrder, name COLLATE NOCASE") fun observeAll(profileId: String): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories WHERE profileId=:profileId ORDER BY sortOrder, name COLLATE NOCASE") suspend fun getAll(profileId: String): List<CategoryEntity>
    @Query("SELECT * FROM categories WHERE id=:id") suspend fun get(id: String): CategoryEntity?
    @Query("SELECT COUNT(*) FROM categories WHERE profileId=:profileId AND isArchived=0") fun observeActiveCount(profileId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM categories WHERE parentId=:id") suspend fun childCount(id: String): Int
    @Query("SELECT (SELECT COUNT(*) FROM transactions WHERE categoryId=:id) + (SELECT COUNT(*) FROM transaction_splits WHERE categoryId=:id)") suspend fun transactionReferenceCount(id: String): Int
    @Query("SELECT COUNT(*) FROM recurring_rules WHERE categoryId=:id") suspend fun recurringReferenceCount(id: String): Int
    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId=:id") suspend fun budgetReferenceCount(id: String): Int
    @Query("UPDATE categories SET isArchived=1, updatedAt=:updatedAt WHERE parentId=:parentId AND isArchived=0") suspend fun archiveChildren(parentId: String, updatedAt: Long)
    @Query("UPDATE categories SET parentId=:targetId, updatedAt=:updatedAt WHERE parentId=:sourceId") suspend fun moveChildren(sourceId: String, targetId: String, updatedAt: Long)
    @Query("UPDATE transactions SET categoryId=:targetId, updatedAt=:updatedAt WHERE categoryId=:sourceId") suspend fun moveTransactions(sourceId: String, targetId: String, updatedAt: Long)
    @Query("UPDATE transaction_splits SET categoryId=:targetId WHERE categoryId=:sourceId") suspend fun moveSplits(sourceId: String, targetId: String)
    @Query("UPDATE recurring_rules SET categoryId=:targetId, updatedAt=:updatedAt WHERE categoryId=:sourceId") suspend fun moveRecurring(sourceId: String, targetId: String, updatedAt: Long)
    @Query("UPDATE budgets SET categoryId=:targetId, updatedAt=:updatedAt WHERE categoryId=:sourceId") suspend fun moveBudgets(sourceId: String, targetId: String, updatedAt: Long)
    @Query("SELECT COUNT(*) FROM categories WHERE profileId=:profileId AND kind=:kind AND name=:name COLLATE NOCASE AND id!=:excludedId") suspend fun duplicateNameCount(profileId: String, kind: String, name: String, excludedId: String): Int
    @Query("SELECT COALESCE(MAX(sortOrder),-1)+1 FROM categories WHERE profileId=:profileId AND kind=:kind") suspend fun nextOrder(profileId: String, kind: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: CategoryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(items: List<CategoryEntity>)
    @Update suspend fun update(item: CategoryEntity)
    @Delete suspend fun delete(item: CategoryEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE profileId=:profileId AND isDeleted=0 ORDER BY transactionDate DESC, createdAt DESC") fun observeAll(profileId: String): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE profileId=:profileId AND isDeleted=0 ORDER BY transactionDate, createdAt") suspend fun getAll(profileId: String): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE id=:id") suspend fun get(id: String): TransactionEntity?
    @Query("SELECT COUNT(*) FROM transactions WHERE profileId=:profileId AND isDeleted=0") fun observeCount(profileId: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM transactions WHERE recurringRuleId=:ruleId AND transactionDate=:date") suspend fun recurringOccurrenceCount(ruleId: String, date: String): Int
    @Query("SELECT * FROM transaction_splits ORDER BY transactionId, sortOrder") fun observeSplits(): Flow<List<TransactionSplitEntity>>
    @Query("SELECT * FROM transaction_splits ORDER BY transactionId, sortOrder") suspend fun getAllSplits(): List<TransactionSplitEntity>
    @Query("SELECT * FROM transaction_splits WHERE transactionId=:transactionId ORDER BY sortOrder") suspend fun getSplits(transactionId: String): List<TransactionSplitEntity>
    @Query("DELETE FROM transaction_splits WHERE transactionId=:transactionId") suspend fun deleteSplits(transactionId: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSplits(items: List<TransactionSplitEntity>)
    @Query("SELECT * FROM transaction_revisions ORDER BY timestamp DESC") fun observeRevisions(): Flow<List<TransactionRevisionEntity>>
    @Query("SELECT * FROM transaction_revisions WHERE transactionId=:transactionId ORDER BY timestamp DESC") suspend fun getRevisions(transactionId: String): List<TransactionRevisionEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRevision(item: TransactionRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: TransactionEntity)
    @Update suspend fun update(item: TransactionEntity)
    @Delete suspend fun purge(item: TransactionEntity)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE profileId=:profileId ORDER BY name COLLATE NOCASE") fun observeAll(profileId: String): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags WHERE profileId=:profileId AND name=:name COLLATE NOCASE LIMIT 1") suspend fun find(profileId: String, name: String): TagEntity?
    @Query("SELECT * FROM transaction_tag_cross_ref") fun observeRefs(): Flow<List<TransactionTagCrossRef>>
    @Query("SELECT t.* FROM tags t INNER JOIN transaction_tag_cross_ref r ON r.tagId=t.id WHERE r.transactionId=:transactionId ORDER BY t.name COLLATE NOCASE") suspend fun getForTransaction(transactionId: String): List<TagEntity>
    @Query("DELETE FROM transaction_tag_cross_ref WHERE transactionId=:transactionId") suspend fun deleteRefs(transactionId: String)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: TagEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRef(item: TransactionTagCrossRef)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY createdAt") fun observeAll(): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM attachments ORDER BY createdAt") suspend fun getAll(): List<AttachmentEntity>
    @Query("SELECT * FROM attachments WHERE transactionId=:transactionId") suspend fun getForTransaction(transactionId: String): List<AttachmentEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(items: List<AttachmentEntity>)
    @Query("DELETE FROM attachments WHERE transactionId=:transactionId") suspend fun deleteForTransaction(transactionId: String)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules WHERE profileId=:profileId ORDER BY isActive DESC, nextDueDate, name COLLATE NOCASE") fun observeAll(profileId: String): Flow<List<RecurringRuleEntity>>
    @Query("SELECT * FROM recurring_rules WHERE profileId=:profileId ORDER BY createdAt") suspend fun getAll(profileId: String): List<RecurringRuleEntity>
    @Query("SELECT * FROM recurring_rules WHERE id=:id") suspend fun get(id: String): RecurringRuleEntity?
    @Query("SELECT * FROM recurring_rules WHERE profileId=:profileId AND isActive=1 AND postingMode='AUTO' AND nextDueDate<=:date ORDER BY nextDueDate") suspend fun dueAuto(profileId: String, date: String): List<RecurringRuleEntity>
    @Query("SELECT COUNT(*) FROM recurring_rules WHERE profileId=:profileId AND isActive=1 AND nextDueDate<=:date") fun observeDueCount(profileId: String, date: String): Flow<Int>
    @Query("SELECT COUNT(*) FROM recurring_rules WHERE profileId=:profileId AND isActive=1 AND nextDueDate<=:date") suspend fun dueCount(profileId: String, date: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: RecurringRuleEntity)
    @Update suspend fun update(item: RecurringRuleEntity)
    @Delete suspend fun delete(item: RecurringRuleEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE profileId=:profileId ORDER BY isActive DESC, name COLLATE NOCASE") fun observeAll(profileId: String): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE profileId=:profileId ORDER BY createdAt") suspend fun getAll(profileId: String): List<BudgetEntity>
    @Query("SELECT * FROM budgets WHERE id=:id") suspend fun get(id: String): BudgetEntity?
    @Query("SELECT COUNT(*) FROM budgets WHERE profileId=:profileId AND name=:name COLLATE NOCASE AND id!=:excludedId") suspend fun duplicateNameCount(profileId: String, name: String, excludedId: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: BudgetEntity)
    @Update suspend fun update(item: BudgetEntity)
    @Delete suspend fun delete(item: BudgetEntity)
    @Query("SELECT * FROM budget_periods ORDER BY periodStart") fun observePeriods(): Flow<List<BudgetPeriodEntity>>
    @Query("SELECT * FROM budget_periods WHERE budgetId=:budgetId ORDER BY periodStart") suspend fun getPeriods(budgetId: String): List<BudgetPeriodEntity>
    @Query("SELECT COUNT(*) FROM budget_periods WHERE budgetId=:budgetId") suspend fun periodCount(budgetId: String): Int
    @Query("SELECT * FROM budget_periods WHERE budgetId=:budgetId AND periodStart=:periodStart LIMIT 1") suspend fun getPeriod(budgetId: String, periodStart: String): BudgetPeriodEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPeriod(item: BudgetPeriodEntity): Long
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace_items WHERE profileId=:profileId ORDER BY status='ARCHIVED', dueDate IS NULL, dueDate, updatedAt DESC") fun observeItems(profileId: String): Flow<List<WorkspaceItemEntity>>
    @Query("SELECT * FROM workspace_items WHERE profileId=:profileId ORDER BY createdAt") suspend fun getAllItems(profileId: String): List<WorkspaceItemEntity>
    @Query("SELECT * FROM workspace_items WHERE id=:id") suspend fun getItem(id: String): WorkspaceItemEntity?
    @Query("SELECT COUNT(*) FROM workspace_items WHERE profileId=:profileId AND type=:type AND title=:title COLLATE NOCASE AND id!=:excludedId") suspend fun duplicateTitleCount(profileId: String, type: String, title: String, excludedId: String): Int
    @Query("SELECT COUNT(*) FROM workspace_items WHERE profileId=:profileId AND status='ACTIVE' AND dueDate IS NOT NULL AND dueDate<=:date AND type IN ('PLANNED_PAYMENT','SUBSCRIPTION','WARRANTY','CREDIT')") suspend fun dueReminderCount(profileId: String, date: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertItem(item: WorkspaceItemEntity)
    @Update suspend fun updateItem(item: WorkspaceItemEntity)
    @Delete suspend fun deleteItem(item: WorkspaceItemEntity)
    @Query("SELECT * FROM workspace_events ORDER BY eventDate DESC, createdAt DESC") fun observeEvents(): Flow<List<WorkspaceEventEntity>>
    @Query("SELECT * FROM workspace_events WHERE itemId=:itemId ORDER BY eventDate DESC, createdAt DESC") suspend fun getEvents(itemId: String): List<WorkspaceEventEntity>
    @Query("SELECT * FROM workspace_events ORDER BY eventDate, createdAt") suspend fun getAllEvents(): List<WorkspaceEventEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEvent(item: WorkspaceEventEntity)
    @Update suspend fun updateEvent(item: WorkspaceEventEntity)
    @Delete suspend fun deleteEvent(item: WorkspaceEventEntity)
}

@Dao
interface SavedFilterDao {
    @Query("SELECT * FROM saved_filters WHERE profileId=:profileId ORDER BY name COLLATE NOCASE") fun observeAll(profileId: String): Flow<List<SavedFilterEntity>>
    @Query("SELECT * FROM saved_filters WHERE profileId=:profileId ORDER BY name COLLATE NOCASE") suspend fun getAll(profileId: String): List<SavedFilterEntity>
    @Query("SELECT COUNT(*) FROM saved_filters WHERE profileId=:profileId AND name=:name COLLATE NOCASE AND id!=:excludedId") suspend fun duplicateNameCount(profileId: String, name: String, excludedId: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: SavedFilterEntity)
    @Update suspend fun update(item: SavedFilterEntity)
    @Delete suspend fun delete(item: SavedFilterEntity)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_events WHERE profileId=:profileId ORDER BY timestamp DESC LIMIT 12") fun observeRecent(profileId: String): Flow<List<AuditEventEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(item: AuditEventEntity)
}

@Database(
    entities = [
        ProfileEntity::class, AccountEntity::class, CategoryEntity::class,
        TransactionEntity::class, TransactionSplitEntity::class, TransactionRevisionEntity::class,
        TagEntity::class, TransactionTagCrossRef::class, AttachmentEntity::class,
        RecurringRuleEntity::class, BudgetEntity::class, BudgetPeriodEntity::class, AuditEventEntity::class,
        WorkspaceItemEntity::class, WorkspaceEventEntity::class, SavedFilterEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun tags(): TagDao
    abstract fun attachments(): AttachmentDao
    abstract fun recurring(): RecurringDao
    abstract fun budgets(): BudgetDao
    abstract fun workspace(): WorkspaceDao
    abstract fun savedFilters(): SavedFilterDao
    abstract fun audit(): AuditDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `type` TEXT NOT NULL, `accountId` TEXT NOT NULL, `destinationAccountId` TEXT, `categoryId` TEXT, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `destinationAmountMinor` INTEGER NOT NULL, `destinationCurrencyCode` TEXT, `transferFeeMinor` INTEGER NOT NULL, `transactionDate` TEXT NOT NULL, `payee` TEXT NOT NULL, `note` TEXT NOT NULL, `isCleared` INTEGER NOT NULL, `recurringRuleId` TEXT, `isDeleted` INTEGER NOT NULL, `deletedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`destinationAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_profileId_transactionDate` ON `transactions` (`profileId`, `transactionDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_destinationAccountId` ON `transactions` (`destinationAccountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_createdAt` ON `transactions` (`createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_recurringRuleId_transactionDate` ON `transactions` (`recurringRuleId`, `transactionDate`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `transaction_splits` (`id` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `categoryId` TEXT, `amountMinor` INTEGER NOT NULL, `memo` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_transactionId` ON `transaction_splits` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_splits_categoryId` ON `transaction_splits` (`categoryId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `transaction_revisions` (`id` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `action` TEXT NOT NULL, `summary` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_revisions_transactionId_timestamp` ON `transaction_revisions` (`transactionId`, `timestamp`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT COLLATE NOCASE NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_profileId` ON `tags` (`profileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_profileId_name` ON `tags` (`profileId`, `name`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `transaction_tag_cross_ref` (`transactionId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`transactionId`, `tagId`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tag_cross_ref_tagId` ON `transaction_tag_cross_ref` (`tagId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `transactionId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `localPath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_transactionId` ON `attachments` (`transactionId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `accountId` TEXT NOT NULL, `destinationAccountId` TEXT, `categoryId` TEXT, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `destinationAmountMinor` INTEGER NOT NULL, `destinationCurrencyCode` TEXT, `transferFeeMinor` INTEGER NOT NULL, `payee` TEXT NOT NULL, `note` TEXT NOT NULL, `tagsCsv` TEXT NOT NULL, `frequency` TEXT NOT NULL, `intervalCount` INTEGER NOT NULL, `anchorDay` INTEGER NOT NULL, `monthEndMode` TEXT NOT NULL, `postingMode` TEXT NOT NULL, `occurrencesRemaining` INTEGER, `nextDueDate` TEXT NOT NULL, `endDate` TEXT, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`destinationAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_profileId_nextDueDate` ON `recurring_rules` (`profileId`, `nextDueDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_accountId` ON `recurring_rules` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_destinationAccountId` ON `recurring_rules` (`destinationAccountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` ON `recurring_rules` (`categoryId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT COLLATE NOCASE NOT NULL, `categoryId` TEXT, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `periodType` TEXT NOT NULL, `anchorDate` TEXT NOT NULL, `customEndDate` TEXT, `repeatInterval` INTEGER NOT NULL, `repeatUntil` TEXT, `carryoverMode` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_profileId` ON `budgets` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_profileId_name` ON `budgets` (`profileId`, `name`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `budget_periods` (`id` TEXT NOT NULL, `budgetId` TEXT NOT NULL, `periodStart` TEXT NOT NULL, `periodEnd` TEXT NOT NULL, `allocatedMinor` INTEGER NOT NULL, `carryInMinor` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`budgetId`) REFERENCES `budgets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_periods_budgetId` ON `budget_periods` (`budgetId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_periods_budgetId_periodStart` ON `budget_periods` (`budgetId`, `periodStart`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `workspace_items` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT COLLATE NOCASE NOT NULL, `amountMinor` INTEGER NOT NULL, `currentMinor` INTEGER NOT NULL, `secondaryMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, `secondaryCode` TEXT NOT NULL, `startDate` TEXT, `dueDate` TEXT, `accountId` TEXT, `categoryId` TEXT, `linkedTransactionId` TEXT, `status` TEXT NOT NULL, `note` TEXT NOT NULL, `metadata` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`linkedTransactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_items_profileId` ON `workspace_items` (`profileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_items_profileId_type` ON `workspace_items` (`profileId`, `type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_items_accountId` ON `workspace_items` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_items_categoryId` ON `workspace_items` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_items_linkedTransactionId` ON `workspace_items` (`linkedTransactionId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `workspace_events` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `label` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `eventDate` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`itemId`) REFERENCES `workspace_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_events_itemId` ON `workspace_events` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workspace_events_itemId_eventDate` ON `workspace_events` (`itemId`, `eventDate`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `saved_filters` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `name` TEXT COLLATE NOCASE NOT NULL, `query` TEXT NOT NULL, `type` TEXT, `accountId` TEXT, `categoryId` TEXT, `tagId` TEXT, `currencyCode` TEXT, `minMinor` INTEGER, `maxMinor` INTEGER, `hasAttachment` INTEGER, `cleared` INTEGER, `recurringOnly` INTEGER, `fromDate` TEXT, `toDate` TEXT, `sort` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_saved_filters_profileId` ON `saved_filters` (`profileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_filters_profileId_name` ON `saved_filters` (`profileId`, `name`)")
            }
        }

        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(
            context.applicationContext, LedgerDatabase::class.java, "goldmine_ledger.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
}
