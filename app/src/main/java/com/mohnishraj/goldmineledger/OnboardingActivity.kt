package com.mohnishraj.goldmineledger

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mohnishraj.goldmineledger.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch
import java.time.LocalDate

class OnboardingActivity : AppCompatActivity() {
    private lateinit var b: ActivityOnboardingBinding
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(b.root)
        applySafeSystemBars(b.root)
        b.currencyInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, Utils.currencies.map(Utils::currencyLabel)))
        b.currencyInput.setText(Utils.currencyLabel(Utils.defaultCurrency()), false)
        b.typeInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, AccountType.entries.map { it.label }))
        b.typeInput.setText(AccountType.CASH.label, false)
        b.dateInput.setText(LocalDate.now().toString())
        b.dateInput.setOnClickListener { pickBalanceDate() }
        b.dateLayout.setEndIconOnClickListener { pickBalanceDate() }
        b.nextButton.setOnClickListener { if (step < 2) showStep(step + 1) else save() }
        b.backButton.setOnClickListener { if (step > 0) showStep(step - 1) }
        showStep(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (step > 0) showStep(step - 1) else super.onBackPressed() }

    private fun showStep(value: Int) {
        step = value
        b.flipper.displayedChild = step
        b.stepLabel.text = "Step ${step + 1} of 3"
        b.backButton.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        b.nextButton.text = if (step == 2) "Create workspace" else "Next"
    }

    private fun pickBalanceDate() {
        val initial = runCatching { LocalDate.parse(b.dateInput.text?.toString().orEmpty()) }
            .getOrDefault(LocalDate.now())
        DatePickerDialog(
            this,
            { _, year, month, day ->
                b.dateInput.setText(LocalDate.of(year, month + 1, day).toString())
                b.dateLayout.error = null
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    private fun save() {
        b.nameLayout.error = null
        b.currencyLayout.error = null
        b.balanceLayout.error = null
        b.dateLayout.error = null
        val name = b.nameInput.text?.toString().orEmpty()
        Utils.validName(name, 60)?.let { b.nameLayout.error = it; return }
        val currency = Utils.currencyCode(b.currencyInput.text.toString())
        Utils.validCurrency(currency)?.let { b.currencyLayout.error = it; return }
        val amount = Utils.parseMinor(b.balanceInput.text?.toString().orEmpty().ifBlank { "0" }, currency)
        if (amount.isFailure) { b.balanceLayout.error = amount.exceptionOrNull()?.message; return }
        val date = b.dateInput.text?.toString().orEmpty()
        Utils.validDate(date)?.let { b.dateLayout.error = it; return }
        val type = AccountType.entries.firstOrNull { it.label == b.typeInput.text.toString() } ?: AccountType.CASH
        b.progress.visibility = View.VISIBLE
        b.nextButton.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                (application as GoldmineApp).container.repository.finishOnboarding(
                    currency, name, type, amount.getOrThrow(), date, b.savingsSwitch.isChecked
                )
            }.onSuccess {
                startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
                finishAffinity()
            }.onFailure {
                b.progress.visibility = View.GONE
                b.nextButton.isEnabled = true
                Snackbar.make(b.root, it.message ?: "Could not create workspace", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
