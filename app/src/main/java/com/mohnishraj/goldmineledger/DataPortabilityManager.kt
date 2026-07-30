package com.mohnishraj.goldmineledger

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PassphraseRequiredException : IllegalArgumentException("This backup is encrypted. Enter its passphrase.")

data class BackupReport(val bytesWritten: Long, val attachmentCount: Int, val encrypted: Boolean)
data class RestorePreview(val profileName: String, val accountCount: Int, val transactionCount: Int, val attachmentCount: Int, val databaseVersion: Int)

class DataPortabilityManager(
    private val context: Context,
    private val db: LedgerDatabase,
    private val repository: LedgerRepository
) {
    private val resolver get() = context.contentResolver
    private val databaseFile get() = context.getDatabasePath(DATABASE_NAME)
    private val attachmentsFolder get() = File(context.filesDir, "attachments")

    suspend fun createBackup(uri: Uri, passphrase: CharArray?): BackupReport = withContext(Dispatchers.IO) {
        require(passphrase == null || passphrase.size >= 6) { "Use at least 6 characters for an encrypted backup" }
        runCatching { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close() }
        require(databaseFile.isFile) { "Ledger database is not ready" }
        val tempZip = File.createTempFile("ledgerly-backup-", ".zip", context.cacheDir)
        try {
            val checksums = linkedMapOf<String, String>()
            var attachmentCount = 0
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempZip))).use { zip ->
                addFile(zip, databaseFile, "database/$DATABASE_NAME", checksums)
                val settingsFile = File(context.filesDir, "datastore/goldmine_settings.preferences_pb")
                if (settingsFile.isFile) addFile(zip, settingsFile, "settings/goldmine_settings.preferences_pb", checksums)
                if (attachmentsFolder.isDirectory) {
                    attachmentsFolder.walkTopDown().filter { it.isFile }.forEach { file ->
                        val relative = file.relativeTo(attachmentsFolder).invariantSeparatorsPath
                        addFile(zip, file, "attachments/$relative", checksums)
                        attachmentCount++
                    }
                }
                val manifest = JSONObject()
                    .put("format", "ledgerly-backup")
                    .put("formatVersion", 1)
                    .put("appVersion", "1.0.0")
                    .put("databaseVersion", 3)
                    .put("createdAt", Instant.now().toString())
                    .put("attachmentCount", attachmentCount)
                    .put("checksums", JSONObject(checksums as Map<*, *>))
                addBytes(zip, manifest.toString(2).toByteArray(StandardCharsets.UTF_8), "manifest.json")
            }
            resolver.openOutputStream(uri, "w").use { raw ->
                requireNotNull(raw) { "Could not open the selected destination" }
                if (passphrase == null) copyFile(tempZip, raw)
                else encryptFile(tempZip, raw, passphrase)
            }
            BackupReport(tempZip.length(), attachmentCount, passphrase != null)
        } finally {
            tempZip.delete()
        }
    }

    suspend fun prepareRestore(uri: Uri, passphrase: CharArray?): RestorePreview = withContext(Dispatchers.IO) {
        val source = File.createTempFile("ledgerly-restore-source-", ".bin", context.cacheDir)
        val zipFile = File.createTempFile("ledgerly-restore-", ".zip", context.cacheDir)
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not read the selected backup" }
                FileOutputStream(source).use { output -> input.copyTo(output) }
            }
            val encrypted = source.inputStream().buffered().use { input ->
                val header = ByteArray(MAGIC.size)
                input.read(header) == MAGIC.size && header.contentEquals(MAGIC)
            }
            if (encrypted) {
                if (passphrase == null) throw PassphraseRequiredException()
                decryptFile(source, zipFile, passphrase)
            } else {
                source.copyTo(zipFile, overwrite = true)
            }
            val pending = File(context.filesDir, PENDING_FOLDER)
            if (pending.exists()) pending.deleteRecursively()
            pending.mkdirs()
            extractAndValidate(zipFile, pending)
            inspectDatabase(File(pending, "database/$DATABASE_NAME"), pending)
        } finally {
            source.delete()
            zipFile.delete()
        }
    }

    fun commitPreparedRestore() {
        val pending = File(context.filesDir, PENDING_FOLDER)
        require(File(pending, "database/$DATABASE_NAME").isFile) { "Prepared restore is no longer available" }
        File(context.filesDir, PENDING_MARKER).writeText(pending.absolutePath)
    }

    fun discardPreparedRestore() {
        File(context.filesDir, PENDING_MARKER).delete()
        File(context.filesDir, PENDING_FOLDER).deleteRecursively()
    }

    suspend fun exportTransactionsCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val profile = db.profiles().get() ?: error("Profile not found")
        val accounts = db.accounts().observeSnapshot(profile.id).associateBy { it.id }
        val categories = db.categories().getAll(profile.id).associateBy { it.id }
        val transactions = db.transactions().getAll(profile.id)
        requireNotNull(resolver.openOutputStream(uri, "w")) { "Could not open the selected destination" }.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("date,type,amount,currency,account,destination_account,destination_amount,destination_currency,transfer_fee,category,payee,note,cleared,tags")
            transactions.forEach { tx ->
                val tags = db.tags().getForTransaction(tx.id).joinToString("|") { it.name }
                val values = listOf(
                    tx.transactionDate, tx.type, Utils.plain(tx.amountMinor, tx.currencyCode), tx.currencyCode,
                    accounts[tx.accountId]?.name.orEmpty(), tx.destinationAccountId?.let { accounts[it]?.name }.orEmpty(),
                    tx.destinationCurrencyCode?.let { Utils.plain(tx.destinationAmountMinor, it) }.orEmpty(),
                    tx.destinationCurrencyCode.orEmpty(), Utils.plain(tx.transferFeeMinor, tx.currencyCode),
                    tx.categoryId?.let { categories[it]?.name }.orEmpty(), tx.payee, tx.note,
                    tx.isCleared.toString(), tags
                )
                writer.appendLine(values.joinToString(",", transform = ::csvEscape))
            }
        }
        transactions.size
    }

    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val profile = db.profiles().get() ?: error("Profile not found")
        val accounts = db.accounts().observeSnapshot(profile.id)
        val categories = db.categories().getAll(profile.id)
        val transactions = db.transactions().getAll(profile.id)
        val budgets = db.budgets().getAll(profile.id)
        val recurring = db.recurring().getAll(profile.id)
        val workspace = db.workspace().getAllItems(profile.id)
        val workspaceEvents = db.workspace().getAllEvents()
        val savedFilters = db.savedFilters().getAll(profile.id)
        val attachments = db.attachments().getAll()
        val root = JSONObject()
            .put("format", "ledgerly-json")
            .put("version", 1)
            .put("exportedAt", Instant.now().toString())
            .put("profile", profile.toJson())
            .put("accounts", JSONArray(accounts.map { it.toJson() }))
            .put("categories", JSONArray(categories.map { it.toJson() }))
            .put("transactions", JSONArray(transactions.map { tx ->
                tx.toJson().put("tags", JSONArray(db.tags().getForTransaction(tx.id).map { it.name }))
            }))
            .put("budgets", JSONArray(budgets.map { it.toJson() }))
            .put("recurring", JSONArray(recurring.map { it.toJson() }))
            .put("workspace", JSONArray(workspace.map { it.toJson() }))
            .put("workspaceEvents", JSONArray(workspaceEvents.map { it.toJson() }))
            .put("savedFilters", JSONArray(savedFilters.map { it.toJson() }))
            .put("attachments", JSONArray(attachments.map { it.toJson() }))
        requireNotNull(resolver.openOutputStream(uri, "w")) { "Could not open the selected destination" }.bufferedWriter(StandardCharsets.UTF_8).use { it.write(root.toString(2)) }
        transactions.size
    }

    suspend fun importTransactionsCsv(uri: Uri): ImportReport = withContext(Dispatchers.IO) {
        val profile = db.profiles().get() ?: error("Profile not found")
        val accountList = db.accounts().observeSnapshot(profile.id)
        val categoryList = db.categories().getAll(profile.id)
        val accountByName = accountList.associateBy { it.name.lowercase(Locale.ROOT) }
        val categoryByName = categoryList.associateBy { it.name.lowercase(Locale.ROOT) }
        val existing = db.transactions().getAll(profile.id).map { signature(it) }.toMutableSet()
        val lines = requireNotNull(resolver.openInputStream(uri)) { "Could not read the selected CSV" }.bufferedReader(StandardCharsets.UTF_8).use { it.readLines() }
        require(lines.isNotEmpty()) { "CSV file is empty" }
        val header = parseCsvLine(lines.first()).map { it.trim().lowercase(Locale.ROOT) }
        val required = listOf("date", "type", "amount", "currency", "account")
        require(required.all(header::contains)) { "CSV must include: ${required.joinToString()}" }
        fun index(name: String) = header.indexOf(name)
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<String>()
        lines.drop(1).forEachIndexed { rowIndex, line ->
            if (line.isBlank()) return@forEachIndexed
            try {
                val row = parseCsvLine(line)
                fun value(name: String): String = index(name).takeIf { it >= 0 }?.let { row.getOrNull(it) }.orEmpty().trim()
                val date = value("date")
                require(Utils.validDate(date) == null) { "invalid date" }
                val typeName = value("type").uppercase(Locale.ROOT)
                require(typeName in TransactionType.entries.map { it.name }) { "type must be EXPENSE, INCOME or TRANSFER" }
                val type = TransactionType.valueOf(typeName)
                val currency = value("currency").uppercase(Locale.ROOT)
                require(Utils.validCurrency(currency) == null) { "invalid currency" }
                val account = accountByName[value("account").lowercase(Locale.ROOT)] ?: error("account '${value("account")}' not found")
                require(account.currencyCode == currency) { "account currency is ${account.currencyCode}, not $currency" }
                val destinationName = value("destination_account")
                val destination = destinationName.takeIf { it.isNotBlank() }?.let { accountByName[it.lowercase(Locale.ROOT)] }
                val categoryName = value("category")
                val category = categoryName.takeIf { it.isNotBlank() }?.let { categoryByName[it.lowercase(Locale.ROOT)] }
                val amount = Utils.parseMinor(value("amount"), currency).getOrThrow()
                val destinationCurrency = value("destination_currency").ifBlank { destination?.currencyCode.orEmpty() }
                val destinationAmount = value("destination_amount").takeIf { it.isNotBlank() }?.let { Utils.parseMinor(it, destinationCurrency).getOrThrow() }
                val fee = value("transfer_fee").takeIf { it.isNotBlank() }?.let { Utils.parseMinor(it, currency).getOrThrow() } ?: 0L
                val cleared = value("cleared").ifBlank { "true" }.toBooleanStrictOrNull() ?: true
                val draft = TransactionDraft(
                    id = null, type = type, accountId = account.id, destinationAccountId = destination?.id,
                    categoryId = category?.id, amountMinor = amount, destinationAmountMinor = destinationAmount,
                    transferFeeMinor = fee, splits = emptyList(), date = date, payee = value("payee"),
                    note = value("note"), tags = value("tags").split('|').filter { it.isNotBlank() },
                    cleared = cleared, attachmentUris = emptyList(), removeExistingAttachments = false
                )
                val sig = listOf(date, type.name, account.id, amount.toString(), draft.payee.trim().lowercase(Locale.ROOT)).joinToString("|")
                if (sig in existing) {
                    skipped++
                } else {
                    repository.saveTransaction(draft)
                    existing += sig
                    imported++
                }
            } catch (error: Throwable) {
                skipped++
                if (errors.size < 25) errors += "Row ${rowIndex + 2}: ${error.message ?: "invalid row"}"
            }
        }
        ImportReport(imported, skipped, errors)
    }

    suspend fun writeCsvTemplate(uri: Uri) = withContext(Dispatchers.IO) {
        requireNotNull(resolver.openOutputStream(uri, "w")) { "Could not open the selected destination" }.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            writer.appendLine("date,type,amount,currency,account,destination_account,destination_amount,destination_currency,transfer_fee,category,payee,note,cleared,tags")
            writer.appendLine("${LocalDate.now()},EXPENSE,12.50,INR,Cash,,,,0,Food & dining,Example shop,Template row,true,Imported|Example")
        }
    }

    private fun addFile(zip: ZipOutputStream, file: File, path: String, checksums: MutableMap<String, String>) {
        val digest = MessageDigest.getInstance("SHA-256")
        zip.putNextEntry(ZipEntry(path))
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                zip.write(buffer, 0, count)
            }
        }
        zip.closeEntry()
        checksums[path] = digest.digest().toHex()
    }

    private fun addBytes(zip: ZipOutputStream, bytes: ByteArray, path: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun copyFile(file: File, output: OutputStream) {
        FileInputStream(file).use { input -> input.copyTo(output) }
    }

    private fun encryptFile(source: File, output: OutputStream, passphrase: CharArray) {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        output.write(MAGIC)
        output.write(salt)
        output.write(iv)
        CipherOutputStream(output, cipher).use { encrypted -> FileInputStream(source).use { it.copyTo(encrypted) } }
    }

    private fun decryptFile(source: File, output: File, passphrase: CharArray) {
        FileInputStream(source).buffered().use { raw ->
            val magic = raw.readExactly(MAGIC.size)
            require(magic.contentEquals(MAGIC)) { "Invalid encrypted backup header" }
            val salt = raw.readExactly(16)
            val iv = raw.readExactly(12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            }
            try {
                CipherInputStream(raw, cipher).use { decrypted -> FileOutputStream(output).use { decrypted.copyTo(it) } }
            } catch (_: Throwable) {
                output.delete()
                throw IllegalArgumentException("Wrong passphrase or damaged backup")
            }
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val encoded = factory.generateSecret(PBEKeySpec(passphrase, salt, 120_000, 256)).encoded
        return SecretKeySpec(encoded, "AES")
    }

    private fun extractAndValidate(zipFile: File, destination: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(destination, entry.name)
                val canonicalRoot = destination.canonicalPath + File.separator
                require(out.canonicalPath.startsWith(canonicalRoot)) { "Unsafe path in backup" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
        val manifestFile = File(destination, "manifest.json")
        require(manifestFile.isFile) { "Backup manifest is missing" }
        val manifest = JSONObject(manifestFile.readText())
        require(manifest.optString("format") == "ledgerly-backup") { "This is not a Ledgito backup" }
        val checksums = manifest.getJSONObject("checksums")
        checksums.keys().forEach { path ->
            val file = File(destination, path)
            require(file.isFile) { "Backup entry is missing: $path" }
            require(file.sha256() == checksums.getString(path)) { "Backup integrity check failed: $path" }
        }
    }

    private fun inspectDatabase(file: File, pending: File): RestorePreview {
        require(file.isFile) { "Backup database is missing" }
        val sqlite = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return sqlite.use { database ->
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
            require(version in 1..3) { "Unsupported database version: $version" }
            val profileName = database.rawQuery("SELECT name FROM profiles LIMIT 1", null).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "My finances" }
            val accounts = database.rawQuery("SELECT COUNT(*) FROM accounts", null).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            val transactions = if (version >= 2) database.rawQuery("SELECT COUNT(*) FROM transactions WHERE isDeleted=0", null).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) } else 0
            val attachments = File(pending, "attachments").walkTopDown().count { it.isFile }
            RestorePreview(profileName, accounts, transactions, attachments, version)
        }
    }

    private suspend fun AccountDao.observeSnapshot(profileId: String): List<AccountEntity> {
        // Room does not expose a synchronous all-accounts query in older project versions.
        return observeAll(profileId).first()
    }

    private fun signature(tx: TransactionEntity): String = listOf(
        tx.transactionDate, tx.type, tx.accountId, tx.amountMinor.toString(), tx.payee.trim().lowercase(Locale.ROOT)
    ).joinToString("|")

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"$escaped\"" else escaped
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { current.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { result += current.toString(); current.clear() }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString()
        return result
    }

    private fun ProfileEntity.toJson() = JSONObject().put("id", id).put("name", name).put("baseCurrency", baseCurrency).put("localeTag", localeTag).put("createdAt", createdAt).put("updatedAt", updatedAt)
    private fun AccountEntity.toJson() = JSONObject().put("id", id).put("name", name).put("type", type).put("currencyCode", currencyCode).put("openingBalanceMinor", openingBalanceMinor).put("openingDate", openingDate).put("includeInTotal", includeInTotal).put("isArchived", isArchived)
    private fun CategoryEntity.toJson() = JSONObject().put("id", id).put("parentId", parentId).put("name", name).put("kind", kind).put("iconKey", iconKey).put("isArchived", isArchived)
    private fun TransactionEntity.toJson() = JSONObject().put("id", id).put("type", type).put("accountId", accountId).put("destinationAccountId", destinationAccountId).put("categoryId", categoryId).put("amountMinor", amountMinor).put("currencyCode", currencyCode).put("destinationAmountMinor", destinationAmountMinor).put("destinationCurrencyCode", destinationCurrencyCode).put("transferFeeMinor", transferFeeMinor).put("transactionDate", transactionDate).put("payee", payee).put("note", note).put("isCleared", isCleared)
    private fun BudgetEntity.toJson() = JSONObject().put("id", id).put("name", name).put("categoryId", categoryId).put("amountMinor", amountMinor).put("currencyCode", currencyCode).put("periodType", periodType).put("anchorDate", anchorDate).put("carryoverMode", carryoverMode).put("isActive", isActive)
    private fun RecurringRuleEntity.toJson() = JSONObject().put("id", id).put("name", name).put("type", type).put("accountId", accountId).put("destinationAccountId", destinationAccountId).put("categoryId", categoryId).put("amountMinor", amountMinor).put("currencyCode", currencyCode).put("frequency", frequency).put("intervalCount", intervalCount).put("postingMode", postingMode).put("nextDueDate", nextDueDate).put("endDate", endDate).put("isActive", isActive)
    private fun WorkspaceItemEntity.toJson() = JSONObject().put("id", id).put("type", type).put("title", title).put("amountMinor", amountMinor).put("currentMinor", currentMinor).put("currencyCode", currencyCode).put("secondaryCode", secondaryCode).put("startDate", startDate).put("dueDate", dueDate).put("status", status).put("note", note).put("metadata", metadata)
    private fun WorkspaceEventEntity.toJson() = JSONObject().put("id", id).put("itemId", itemId).put("kind", kind).put("label", label).put("amountMinor", amountMinor).put("eventDate", eventDate).put("isCompleted", isCompleted).put("note", note)
    private fun SavedFilterEntity.toJson() = JSONObject().put("id", id).put("name", name).put("query", query).put("type", type).put("accountId", accountId).put("categoryId", categoryId).put("currencyCode", currencyCode).put("fromDate", fromDate).put("toDate", toDate).put("sort", sort)
    private fun AttachmentEntity.toJson() = JSONObject().put("id", id).put("transactionId", transactionId).put("displayName", displayName).put("mimeType", mimeType).put("sizeBytes", sizeBytes).put("createdAt", createdAt)

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun InputStream.readExactly(size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(result, offset, size - offset)
            require(count >= 0) { "Backup header is incomplete" }
            offset += count
        }
        return result
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val DATABASE_NAME = "goldmine_ledger.db"
        const val PENDING_FOLDER = "ledgerly_pending_restore"
        const val PENDING_MARKER = "ledgerly_restore_pending.txt"
        private val MAGIC = "LEDGERLY1".toByteArray(StandardCharsets.US_ASCII)
    }
}

object RestoreCoordinator {
    fun applyPending(context: Context) {
        val marker = File(context.filesDir, DataPortabilityManager.PENDING_MARKER)
        if (!marker.isFile) return
        val pending = runCatching { File(marker.readText().trim()) }.getOrNull() ?: return
        val sourceDb = File(pending, "database/${DataPortabilityManager.DATABASE_NAME}")
        if (!sourceDb.isFile) {
            marker.delete()
            pending.deleteRecursively()
            return
        }
        val dbFile = context.getDatabasePath(DataPortabilityManager.DATABASE_NAME)
        val rollback = File(context.filesDir, "restore_rollback").apply { deleteRecursively(); mkdirs() }
        runCatching {
            if (dbFile.isFile) dbFile.copyTo(File(rollback, DataPortabilityManager.DATABASE_NAME), overwrite = true)
            File(context.filesDir, "attachments").takeIf { it.exists() }?.copyRecursively(File(rollback, "attachments"), overwrite = true)
            val liveSettings = File(context.filesDir, "datastore/goldmine_settings.preferences_pb")
            if (liveSettings.isFile) liveSettings.copyTo(File(rollback, "goldmine_settings.preferences_pb"), overwrite = true)
            dbFile.parentFile?.mkdirs()
            sourceDb.copyTo(dbFile, overwrite = true)
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            val restoredAttachments = File(pending, "attachments")
            val targetAttachments = File(context.filesDir, "attachments")
            targetAttachments.deleteRecursively()
            if (restoredAttachments.exists()) restoredAttachments.copyRecursively(targetAttachments, overwrite = true)
            val restoredSettings = File(pending, "settings/goldmine_settings.preferences_pb")
            if (restoredSettings.isFile) {
                val targetSettings = File(context.filesDir, "datastore/goldmine_settings.preferences_pb")
                targetSettings.parentFile?.mkdirs()
                restoredSettings.copyTo(targetSettings, overwrite = true)
            }
            marker.delete()
            pending.deleteRecursively()
        }.onFailure {
            File(rollback, DataPortabilityManager.DATABASE_NAME).takeIf { it.isFile }?.copyTo(dbFile, overwrite = true)
            val rollbackAttachments = File(rollback, "attachments")
            if (rollbackAttachments.exists()) {
                File(context.filesDir, "attachments").deleteRecursively()
                rollbackAttachments.copyRecursively(File(context.filesDir, "attachments"), overwrite = true)
            }
            File(rollback, "goldmine_settings.preferences_pb").takeIf { it.isFile }?.let { settingsBackup ->
                val targetSettings = File(context.filesDir, "datastore/goldmine_settings.preferences_pb")
                targetSettings.parentFile?.mkdirs()
                settingsBackup.copyTo(targetSettings, overwrite = true)
            }
            marker.delete()
        }
    }
}
