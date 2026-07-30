package com.mohnishraj.goldmineledger

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch
import java.util.Date

class SettingsFragment : Fragment() {
    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private val vm get() = (requireActivity() as MainActivity).viewModel

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (_b == null) return@registerForActivityResult
        b.remindersSwitch.isChecked = granted
        vm.setReminders(granted)
        ReminderWorker.configure(requireContext().applicationContext, granted)
        Snackbar.make(b.root, if (granted) "Daily money reminders enabled" else "Notification permission was not granted", Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.currencyInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, Utils.currencies.map(Utils::currencyLabel)))
        b.dataVaultCard.setOnClickListener { findNavController().navigate(R.id.dataToolsFragment) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                vm.settingsState.collect { settings ->
                    b.currencyInput.setText(Utils.currencyLabel(settings.currency), false)
                    b.hideAmountsSwitch.isChecked = settings.hideAmounts
                    b.appLockSwitch.isChecked = settings.appLock
                    b.remindersSwitch.isChecked = settings.reminders
                    b.backupStatus.text = if (settings.lastBackupAt <= 0L) {
                        "No verified backup yet • tap to protect your data"
                    } else {
                        "Last backup ${DateFormat.getMediumDateFormat(requireContext()).format(Date(settings.lastBackupAt))} • tap for tools"
                    }
                    b.themeGroup.check(when (settings.theme) {
                        ThemeMode.LIGHT -> R.id.themeLight
                        ThemeMode.DARK -> R.id.themeDark
                        ThemeMode.SYSTEM -> R.id.themeSystem
                    })
                }
            }
        }
        b.save.setOnClickListener { savePreferences() }
    }

    private fun savePreferences() {
        val theme = when (b.themeGroup.checkedRadioButtonId) {
            R.id.themeLight -> ThemeMode.LIGHT
            R.id.themeDark -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        b.currencyLayout.error = null
        val currency = Utils.currencyCode(b.currencyInput.text.toString())
        Utils.validCurrency(currency)?.let { b.currencyLayout.error = it; return }

        val lockRequested = b.appLockSwitch.isChecked
        val keyguard = requireContext().getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val lockAllowed = !lockRequested || keyguard.isDeviceSecure
        if (!lockAllowed) {
            b.appLockSwitch.isChecked = false
            Snackbar.make(b.root, "Set a secure phone screen lock before enabling Ledgito lock", Snackbar.LENGTH_LONG).show()
        }

        val remindersRequested = b.remindersSwitch.isChecked
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val remindersNow = remindersRequested && notificationGranted

        vm.updateSettings(theme, currency) { result ->
            if (result.isSuccess) {
                vm.setHideAmounts(b.hideAmountsSwitch.isChecked)
                vm.setAppLock(lockRequested && lockAllowed)
                vm.setReminders(remindersNow)
                ReminderWorker.configure(requireContext().applicationContext, remindersNow)
            }
            Snackbar.make(b.root, result.fold({ "Preferences saved" }, { it.message ?: "Could not save" }), Snackbar.LENGTH_LONG).show()
            if (result.isSuccess) {
                b.root.postDelayed({ applyTheme(theme) }, 350)
                if (remindersRequested && !notificationGranted && Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun applyTheme(theme: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(when (theme) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
