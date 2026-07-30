package com.mohnishraj.goldmineledger

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.ActivityMainBinding
import com.mohnishraj.goldmineledger.databinding.BottomSheetQuickAddBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val app get() = application as GoldmineApp
    val viewModel: LedgerViewModel by viewModels { LedgerViewModel.factory(app) }

    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            app.sessionUnlocked = true
            binding.root.visibility = View.VISIBLE
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeSystemBars(binding.root)
        configureNavigation()
        verifyAppLock()
    }

    override fun onResume() {
        super.onResume()
        keepQuickAddAboveDock()
    }

    private fun configureNavigation() {
        val host = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navController = host.navController
        val rootDestinations = setOf(
            R.id.dashboardFragment,
            R.id.transactionsFragment,
            R.id.reportsFragment,
            R.id.moreFragment
        )
        binding.bottomNav.menu.findItem(R.id.navAddPlaceholder).isEnabled = false
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId !in rootDestinations) return@setOnItemSelectedListener false
            openRootDestination(navController, item.itemId)
        }
        binding.bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId in rootDestinations) openRootDestination(navController, item.itemId)
        }
        binding.quickAdd.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showQuickAdd()
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isRoot = destination.id in rootDestinations
            binding.bottomDock.visibility = if (isRoot) View.VISIBLE else View.GONE
            if (isRoot) {
                binding.quickAdd.show()
                binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
                keepQuickAddAboveDock()
            } else {
                binding.quickAdd.hide()
            }
        }
        binding.root.doOnLayout { keepQuickAddAboveDock() }
    }

    /**
     * The centre action is deliberately positioned inside the dock instead of translated through
     * it. Bringing it to front after layout also protects it from OEM elevation/clipping quirks.
     */
    private fun keepQuickAddAboveDock() {
        if (!::binding.isInitialized || binding.bottomDock.visibility != View.VISIBLE) return
        binding.quickAdd.post {
            binding.quickAdd.bringToFront()
            binding.quickAdd.translationZ = resources.displayMetrics.density * 24f
            if (!binding.quickAdd.isShown) binding.quickAdd.show()
        }
    }

    private fun showQuickAdd() {
        val sheet = BottomSheetDialog(this)
        val content = BottomSheetQuickAddBinding.inflate(layoutInflater)
        sheet.setContentView(content.root)

        fun transaction(type: TransactionType) {
            sheet.dismiss()
            TransactionDialog.newInstance(null, type)
                .show(supportFragmentManager, "quick-add-${type.name.lowercase()}")
        }
        fun workspace(type: WorkspaceType) {
            sheet.dismiss()
            navController.navigate(
                R.id.workspaceFragment,
                Bundle().apply { putString(WorkspaceFragment.ARG_TYPE, type.name) }
            )
        }
        content.quickExpense.setOnClickListener { transaction(TransactionType.EXPENSE) }
        content.quickIncome.setOnClickListener { transaction(TransactionType.INCOME) }
        content.quickTransfer.setOnClickListener { transaction(TransactionType.TRANSFER) }
        content.quickBill.setOnClickListener { workspace(WorkspaceType.BILL) }
        content.quickGoal.setOnClickListener { workspace(WorkspaceType.GOAL) }
        content.quickInvestment.setOnClickListener { workspace(WorkspaceType.INVESTMENT) }
        sheet.setOnShowListener { keepQuickAddAboveDock() }
        sheet.show()
    }

    private fun openRootDestination(navController: NavController, destinationId: Int): Boolean {
        return runCatching {
            val options = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.dashboardFragment, false)
                .setLaunchSingleTop(true)
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .setPopEnterAnim(android.R.anim.fade_in)
                .setPopExitAnim(android.R.anim.fade_out)
                .build()
            navController.navigate(destinationId, null, options)
        }.isSuccess
    }

    private fun verifyAppLock() {
        lifecycleScope.launch {
            val enabled = app.container.settings.appLock.first()
            if (!enabled || app.sessionUnlocked) {
                binding.root.visibility = View.VISIBLE
                return@launch
            }
            binding.root.visibility = View.INVISIBLE
            val manager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!manager.isDeviceSecure) {
                app.container.settings.setAppLock(false)
                binding.root.visibility = View.VISIBLE
                Snackbar.make(binding.root, "App lock disabled because this phone has no secure screen lock", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            launchCredentialPrompt(manager)
        }
    }

    @Suppress("DEPRECATION")
    private fun launchCredentialPrompt(manager: KeyguardManager) {
        val intent: Intent? = manager.createConfirmDeviceCredentialIntent(
            "Unlock Ledgito",
            "Confirm your phone screen lock to open your private finance vault"
        )
        if (intent == null) {
            binding.root.visibility = View.VISIBLE
            return
        }
        unlockLauncher.launch(intent)
    }
}
