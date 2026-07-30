package com.mohnishraj.goldmineledger

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mohnishraj.goldmineledger.databinding.FragmentDataToolsBinding
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class DataToolsFragment : Fragment() {
    private var _b: FragmentDataToolsBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel
    private val portability get() = (requireActivity().application as GoldmineApp).container.portability
    private var pendingPassphrase: CharArray? = null
    private var busySnackbar: Snackbar? = null

    private val backupDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val passphrase = pendingPassphrase
        pendingPassphrase = null
        if (uri != null) runTask("Creating backup…") {
            try {
                val report = portability.createBackup(uri, passphrase)
                vm.markBackupComplete()
                "Backup complete • ${report.attachmentCount} attachment${if (report.attachmentCount == 1) "" else "s"} • ${if (report.encrypted) "encrypted" else "standard"}"
            } finally {
                passphrase?.fill('\u0000')
            }
        } else {
            passphrase?.fill('\u0000')
        }
    }

    private val csvDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runTask("Exporting CSV…") { "Exported ${portability.exportTransactionsCsv(uri)} transactions" }
    }

    private val jsonDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runTask("Exporting JSON…") { "Exported ${portability.exportJson(uri)} transactions plus supporting records" }
    }

    private val templateDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runTask("Writing template…") { portability.writeCsvTemplate(uri); "CSV template saved" }
    }

    private val restoreDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) prepareRestore(uri, null)
    }

    private val importDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import CSV transactions?")
            .setMessage("Rows are validated one by one. Existing exact matches are skipped. Nothing outside the selected CSV is changed.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import") { _, _ ->
                runTask("Importing CSV…") {
                    val report = portability.importTransactionsCsv(uri)
                    buildString {
                        append("Imported ${report.imported} • skipped ${report.skipped}")
                        if (report.errors.isNotEmpty()) append("\n\n${report.errors.take(8).joinToString("\n")}")
                    }
                }
            }.show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentDataToolsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.encryptedBackup.setOnClickListener {
            promptPassphrase("Protect this backup", confirmation = true) { passphrase ->
                pendingPassphrase = passphrase
                backupDocument.launch("Ledgito-${Utils.todayCompact()}.ledgitoe")
            }
        }
        b.standardBackup.setOnClickListener {
            pendingPassphrase = null
            backupDocument.launch("Ledgito-${Utils.todayCompact()}.ledgito")
        }
        b.restore.setOnClickListener { restoreDocument.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }
        b.exportCsv.setOnClickListener { csvDocument.launch("Ledgito-transactions-${Utils.todayCompact()}.csv") }
        b.exportJson.setOnClickListener { jsonDocument.launch("Ledgito-export-${Utils.todayCompact()}.json") }
        b.csvTemplate.setOnClickListener { templateDocument.launch("Ledgito-import-template.csv") }
        b.importCsv.setOnClickListener { importDocument.launch(arrayOf("text/csv", "text/plain", "*/*")) }
        b.scanAttachments.setOnClickListener {
            vm.scanAttachmentIntegrity { result ->
                result.onSuccess { report ->
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(if (report.missingAttachments.isEmpty()) "Attachment vault healthy" else "Attachment repair needed")
                        .setMessage(
                            if (report.missingAttachments.isEmpty()) "Checked ${report.totalAttachments} attachment${if (report.totalAttachments == 1) "" else "s"}. Every stored file is present and non-empty."
                            else "Healthy: ${report.healthyAttachments}/${report.totalAttachments}\n\nMissing or empty:\n${report.missingAttachments.take(20).joinToString("\n")}" 
                        )
                        .setPositiveButton("Close", null)
                        .show()
                }.onFailure { show(it.message ?: "Scan failed") }
            }
        }
        b.diagnostics.setOnClickListener { showDiagnostics() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settingsState.collect { state ->
                    b.backupStatus.text = if (state.lastBackupAt <= 0) "No completed backup recorded yet"
                    else "Last completed backup: ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastBackupAt))}"
                }
            }
        }
    }

    private fun prepareRestore(uri: android.net.Uri, passphrase: CharArray?) {
        viewLifecycleOwner.lifecycleScope.launch {
            setBusy(true, "Validating backup…")
            val result = runCatching { portability.prepareRestore(uri, passphrase) }
            passphrase?.fill('\u0000')
            setBusy(false, null)
            result.onSuccess(::showRestorePreview).onFailure { error ->
                if (error is PassphraseRequiredException) {
                    promptPassphrase("Unlock backup", confirmation = false) { entered -> prepareRestore(uri, entered) }
                } else show(error.message ?: "Could not validate backup")
            }
        }
    }

    private fun showRestorePreview(preview: RestorePreview) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restore is ready")
            .setMessage(
                "Profile: ${preview.profileName}\nAccounts: ${preview.accountCount}\nTransactions: ${preview.transactionCount}\nAttachments: ${preview.attachmentCount}\nDatabase version: ${preview.databaseVersion}\n\nThe current database and attachment vault will be replaced on the next launch. A rollback copy is kept locally during the operation."
            )
            .setNegativeButton("Cancel") { _, _ -> portability.discardPreparedRestore() }
            .setPositiveButton("Apply and close") { _, _ ->
                runCatching { portability.commitPreparedRestore() }
                    .onFailure { show(it.message ?: "Could not schedule restore") }
                    .onSuccess {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Reopen Ledgito")
                            .setMessage("Ledgito will now close. Open it once more to complete the validated restore.")
                            .setCancelable(false)
                            .setPositiveButton("Close app") { _, _ ->
                                requireActivity().finishAffinity()
                                android.os.Process.killProcess(android.os.Process.myPid())
                            }.show()
                    }
            }
            .setOnCancelListener { portability.discardPreparedRestore() }
            .show()
    }

    private fun promptPassphrase(title: String, confirmation: Boolean, done: (CharArray) -> Unit) {
        val wrapper = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
        }
        fun field(hint: String): Pair<TextInputLayout, TextInputEditText> {
            val input = TextInputEditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSingleLine = true
            }
            val layout = TextInputLayout(requireContext()).apply {
                this.hint = hint
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                addView(input)
            }
            wrapper.addView(layout)
            return layout to input
        }
        val first = field("Passphrase")
        val second = if (confirmation) field("Confirm passphrase") else null
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage("Ledgito never stores this passphrase. Losing it means the encrypted backup cannot be opened.")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.Dialog.BUTTON_POSITIVE).setOnClickListener {
                first.first.error = null
                second?.first?.error = null
                val value = first.second.text?.toString().orEmpty()
                if (value.length < 6) { first.first.error = "Use at least 6 characters"; return@setOnClickListener }
                if (second != null && value != second.second.text?.toString().orEmpty()) { second.first.error = "Passphrases do not match"; return@setOnClickListener }
                dialog.dismiss()
                done(value.toCharArray())
            }
        }
        dialog.show()
    }

    private fun showDiagnostics() {
        val profile = vm.profile.value
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ledgito diagnostics")
            .setMessage(
                "App version: 1.0.0\nDatabase schema: 3\nPackage: ${requireContext().packageName}\nProfile currency: ${profile?.baseCurrency ?: "Not set"}\nAccounts: ${vm.allAccounts.value.size}\nTransactions: ${vm.calendarTransactions.value.size}\nWorkspace records: ${vm.workspaceItems.value.size}\n\nCore mode: offline-first\nINTERNET permission: not requested\nPlatform auto-backup: disabled\nExplicit backup: available above"
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun runTask(progress: String, block: suspend () -> String) {
        viewLifecycleOwner.lifecycleScope.launch {
            setBusy(true, progress)
            val result = runCatching { block() }
            setBusy(false, null)
            show(result.fold({ it }, { it.message ?: "Action failed" }))
        }
    }

    private fun setBusy(busy: Boolean, message: String?) {
        if (_b == null) return
        listOf(b.encryptedBackup, b.standardBackup, b.restore, b.exportCsv, b.exportJson, b.importCsv, b.csvTemplate, b.scanAttachments, b.diagnostics).forEach { it.isEnabled = !busy }
        busySnackbar?.dismiss()
        busySnackbar = if (busy && message != null) Snackbar.make(b.root, message, Snackbar.LENGTH_INDEFINITE).also { it.show() } else null
    }

    private fun show(message: String) {
        if (_b != null) Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        busySnackbar?.dismiss()
        pendingPassphrase?.fill('\u0000')
        pendingPassphrase = null
        super.onDestroyView()
        _b = null
    }
}
