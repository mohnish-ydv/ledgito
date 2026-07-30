package com.mohnishraj.goldmineledger

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val app = application as GoldmineApp
            val ready = app.container.settings.onboarded.first() && app.container.repository.profile() != null
            if (ready) {
                runCatching { app.container.repository.postDueRecurring() }
                runCatching { app.container.repository.ensureBudgetPeriods() }
            }
            startActivity(Intent(this@SplashActivity, if (ready) MainActivity::class.java else OnboardingActivity::class.java))
            finish()
        }
    }
}
