#!/usr/bin/env python3
"""Deterministic pre-Gradle release gate for Ledgito 1.2 Professional Experience."""
from __future__ import annotations

import json
import re
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parents[1]

required = [
    "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/mohnishraj/goldmineledger/Models.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/LedgerDatabase.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/LedgerRepository.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/LedgerViewModel.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/MainActivity.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/SystemBars.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/UiPolish.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/DashboardCustomizeDialog.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/WorkspaceCatalog.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/WorkspaceFragment.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/WorkspaceItemDialog.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/WorkspaceEventDialog.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/PlanningHubFragment.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/WealthHubFragment.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/ReportsFragment.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/CalendarFragment.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/InsightVisuals.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/DataPortabilityManager.kt",
    "app/src/main/java/com/mohnishraj/goldmineledger/ReminderWorker.kt",
    "app/src/main/res/navigation/main_nav.xml",
    "app/src/main/res/layout/fragment_planning_hub.xml",
    "app/src/main/res/layout/fragment_wealth_hub.xml",
    "app/src/main/res/layout/fragment_reports.xml",
    "app/src/main/res/layout/fragment_calendar.xml",
    "app/src/main/res/layout/bottom_sheet_quick_add.xml",
    "app/src/main/res/layout/dialog_dashboard_customise.xml",
    ".github/workflows/build-apk.yml", "tools/test_schema.py",
    "README.md", "NOTICE.md", "docs/V1.1-FULL-SUITE.md",
    "docs/V1.2-PROFESSIONAL-EXPERIENCE.md", "docs/V1.2-COMPLETION.json",
    "docs/QA-V1.2.md", "docs/V1.2-LINT-FIX.md", "docs/TERMUX-PUSH.md",
]
missing = [path for path in required if not (root / path).is_file()]
if missing:
    raise SystemExit("Missing required files:\n- " + "\n- ".join(missing))

# Parse every structured file.
xmls = list((root / "app/src/main/res").rglob("*.xml")) + [root / "app/src/main/AndroidManifest.xml"]
for file in xmls:
    try:
        ElementTree.parse(file)
    except Exception as exc:
        raise SystemExit(f"Invalid XML: {file.relative_to(root)}: {exc}")
for file in root.rglob("*.json"):
    try:
        json.loads(file.read_text(encoding="utf-8"))
    except Exception as exc:
        raise SystemExit(f"Invalid JSON: {file.relative_to(root)}: {exc}")

android_ns = "{http://schemas.android.com/apk/res/android}"
app_ns = "{http://schemas.android.com/apk/res-auto}"
all_ids: set[str] = set()
for file in (root / "app/src/main/res").glob("layout*/*.xml"):
    seen: set[str] = set()
    for element in ElementTree.parse(file).iter():
        tag = element.tag.rsplit("}", 1)[-1]
        view_id = element.attrib.get(android_ns + "id", "")
        if view_id.startswith("@+id/"):
            clean = view_id.split("/", 1)[1]
            if clean in seen:
                raise SystemExit(f"Duplicate ID in {file.relative_to(root)}: {clean}")
            seen.add(clean)
            all_ids.add(clean)
        if tag.endswith("LinearLayout") and android_ns + "orientation" not in element.attrib:
            raise SystemExit(f"LinearLayout missing orientation: {file.relative_to(root)} {view_id or '(no id)'}")
        if tag.endswith("RecyclerView") and app_ns + "layoutManager" not in element.attrib:
            raise SystemExit(f"RecyclerView missing layoutManager: {file.relative_to(root)} {view_id or '(no id)'}")

main_sources = sorted((root / "app/src/main/java").rglob("*.kt"))
test_sources = sorted((root / "app/src/test").rglob("*.kt"))
source = "\n".join(file.read_text(encoding="utf-8") for file in main_sources)
resource_text = "\n".join(file.read_text(encoding="utf-8") for file in (root / "app/src/main/res").rglob("*.xml"))
all_ids.update(re.findall(r"@\+id/([A-Za-z0-9_]+)", resource_text))

# Privacy and app identity.
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
for token in [
    'android:allowBackup="false"', 'android:dataExtractionRules="@xml/data_extraction_rules"',
    'androidx.core.content.FileProvider', '@xml/file_paths',
    'android.permission.POST_NOTIFICATIONS', 'tools:targetApi="tiramisu"',
]:
    if token not in manifest:
        raise SystemExit(f"Manifest contract missing: {token}")
if "android.permission.INTERNET" in manifest:
    raise SystemExit("Offline-first Ledgito must not request INTERNET permission")

app_gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
for token in [
    'namespace = "com.mohnishraj.goldmineledger"',
    'applicationId = "com.mohnishraj.goldmineledger"',
    'compileSdk = 35', 'minSdk = 26', 'targetSdk = 35',
    'versionCode = 10', 'versionName = "1.2.0-professional-experience"',
    'viewBinding = true', 'abortOnError = true', 'checkReleaseBuilds = true',
    'room.schemaLocation', 'androidx.work:work-runtime-ktx:2.10.0',
]:
    if token not in app_gradle:
        raise SystemExit(f"App configuration mismatch: {token}")
if 'rootProject.name = "Ledgito"' not in (root / "settings.gradle.kts").read_text(encoding="utf-8"):
    raise SystemExit("Gradle root project has not been renamed to Ledgito")
if '<string name="app_name">Ledgito</string>' not in (root / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8"):
    raise SystemExit("Visible app name is not Ledgito")

# Room compatibility and security.
database = (root / "app/src/main/java/com/mohnishraj/goldmineledger/LedgerDatabase.kt").read_text(encoding="utf-8")
for token in [
    "version = 3", "MIGRATION_1_2", "MIGRATION_2_3", "WorkspaceItemEntity::class",
    "WorkspaceEventEntity::class", "SavedFilterEntity::class",
    ".addMigrations(MIGRATION_1_2, MIGRATION_2_3)",
]:
    if token not in database:
        raise SystemExit(f"Room v3 migration contract missing: {token}")
if "fallbackToDestructiveMigration" in source:
    raise SystemExit("Destructive Room migration is forbidden")
for token in [
    "DataPortabilityManager", "PBKDF2WithHmacSHA256", "AES/GCM/NoPadding",
    '"ledgerly-backup"', "scanAttachmentIntegrity", "setAppLock", "setReminders",
    "setHideAmounts",
]:
    if token not in source:
        raise SystemExit(f"Privacy/portability contract missing: {token}")

# Full-suite implementation contracts.
for token in [
    "PLANNED_PAYMENT", "BILL", "EMI", "GOAL", "DEBT", "LOAN", "SUBSCRIPTION",
    "INVESTMENT", "MUTUAL_FUND", "GOLD", "FIXED_DEPOSIT", "PPF", "EPF", "CRYPTO",
    "ASSET", "LIABILITY", "CREDIT", "SHOPPING_LIST", "WARRANTY", "LOYALTY",
    "SHARED_EXPENSE", "CURRENCY_RATE", "BillingCadence", "nextBillingDate", "billingOccurrences",
    "postToLedger", "recalculateWorkspaceItem",
    "PlanningHubFragment", "WealthHubFragment", "SpendingHeatmapView", "financialScore",
    "savingsRatePercent", "budgetUsedPercent", "spendingDailyRows", "goalProgress",
    "performanceValue", "duplicateTransaction", "adjustAccountBalance", "mergeCategory",
    "DashboardCustomizeDialog", "DashboardSections", "setDashboardSections",
    "BottomSheetQuickAddBinding", "quickExpense", "quickBill", "quickInvestment",
    "AllocationDonutView", "allocationDonut", "diversificationMessage",
    "planHealthScore", "debtStrategyButton", "showDebtStrategies",
    "averageDailySpendMinor", "noSpendDays", "spendingVolatilityPercent",
    "incomeStabilityLabel", "topPayeeRows", "payeeContainer",
    "ItemTouchHelper.LEFT", "ItemTouchHelper.RIGHT", "Transaction restored",
]:
    if token not in source + resource_text:
        raise SystemExit(f"Professional-experience contract missing: {token}")

repository = (root / "app/src/main/java/com/mohnishraj/goldmineledger/LedgerRepository.kt").read_text(encoding="utf-8")
for token in [
    "Amount cannot exceed the remaining balance", "CREATED_FROM_WORKSPACE_PAYMENT",
    "due.plusMonths(1)", "Utils.nextBillingDate", "BillingCadence.from(item.secondaryCode)",
    "account.currencyCode == draft.currencyCode",
]:
    if token not in repository:
        raise SystemExit(f"Planning integrity contract missing: {token}")
view_model = (root / "app/src/main/java/com/mohnishraj/goldmineledger/LedgerViewModel.kt").read_text(encoding="utf-8")
for token in [
    "occurrencesRemaining", "endDate", "Bills use their unpaid amount",
    "active budgets are over their current limits", "buildForecast", "buildReport",
]:
    if token not in view_model:
        raise SystemExit(f"Forecast/analytics contract missing: {token}")
# GitHub compile regression from the 1.2 professional-experience build.
if "if (totalDays <= 92L" in view_model:
    raise SystemExit("Undefined totalDays regression: use elapsedDays for no-spend insight windows")
if "if (elapsedDays <= 92L && noSpendDays > 0)" not in view_model:
    raise SystemExit("No-spend insight must use the elapsed-day window")
if not re.search(r"volatilityPercent\?\.let\s*\{.*?when\s*\{.*?else\s*->\s*Unit", view_model, re.S):
    raise SystemExit("Volatility insight when-expression must be exhaustive")

# Source hygiene and compiler-regression guards.
if not re.search(r"class\s+LedgerViewModel\s*\(.*?private\s+val\s+settings\s*:\s*SettingsRepository", view_model, re.S):
    raise SystemExit("LedgerViewModel must retain SettingsRepository as a private constructor property")
if "settings.setHideAmounts(value)" not in view_model:
    raise SystemExit("Hide-amounts action is disconnected")
if re.search(r"\.map\([A-Za-z0-9_]+::toJson\)", source):
    raise SystemExit("Member-extension callable references do not compile; use lambdas")
for forbidden in ["NotImplementedError", "TODO(", "TODO:", "FIXME", "android.app.AlertDialog"]:
    if forbidden in source:
        raise SystemExit(f"Forbidden production token: {forbidden}")
if re.search(r"getLongArray\([^)]*\)\.orEmpty\(\)", source):
    raise SystemExit("Primitive LongArray cannot use orEmpty()")
if "com.google.android.material.animation.AnimationUtils" in source or "AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR" in source:
    raise SystemExit("Restricted Material AnimationUtils API is forbidden; use the public PathInterpolator easing")
if "PathInterpolator(0.4f, 0f, 0.2f, 1f)" not in source:
    raise SystemExit("Public Material-standard easing interpolator contract is missing")
for file in main_sources + test_sources:
    text = file.read_text(encoding="utf-8")
    if text.count("{") != text.count("}"):
        raise SystemExit(f"Brace mismatch: {file.relative_to(root)}")

# Resource/ViewBinding cross-checks.
refs = set(re.findall(r"(?<![A-Za-z0-9_.])R\.id\.([A-Za-z0-9_]+)", source))
missing_ids = sorted(refs - all_ids)
if missing_ids:
    raise SystemExit("Missing resource IDs: " + ", ".join(missing_ids))
layouts = {file.stem for file in (root / "app/src/main/res/layout").glob("*.xml")}
def camel_to_snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()
bindings = set(re.findall(r"import com\.mohnishraj\.goldmineledger\.databinding\.([A-Za-z0-9]+)Binding", source))
for binding in bindings:
    if camel_to_snake(binding) not in layouts:
        raise SystemExit(f"Missing layout for {binding}Binding: {camel_to_snake(binding)}.xml")

# Navigation and previous-device regression guards.
nav = (root / "app/src/main/res/navigation/main_nav.xml").read_text(encoding="utf-8")
bottom = (root / "app/src/main/res/menu/bottom_nav.xml").read_text(encoding="utf-8")
for destination in ["dashboardFragment", "transactionsFragment", "reportsFragment", "moreFragment"]:
    if destination not in nav or destination not in bottom:
        raise SystemExit(f"Root navigation mismatch: {destination}")
for destination in [
    "accountsFragment", "categoriesFragment", "settingsFragment", "recurringFragment",
    "budgetsFragment", "calendarFragment", "workspaceFragment", "planningHubFragment",
    "wealthHubFragment", "netWorthFragment", "forecastFragment", "dataToolsFragment",
]:
    if destination not in nav:
        raise SystemExit(f"Navigation destination missing: {destination}")
if "navAddPlaceholder" not in bottom:
    raise SystemExit("Central Quick Add placeholder is missing")

main_activity = (root / "app/src/main/java/com/mohnishraj/goldmineledger/MainActivity.kt").read_text(encoding="utf-8")
activity_layout = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")
onboarding_activity = (root / "app/src/main/java/com/mohnishraj/goldmineledger/OnboardingActivity.kt").read_text(encoding="utf-8")
onboarding_layout = (root / "app/src/main/res/layout/activity_onboarding.xml").read_text(encoding="utf-8")
system_bars = (root / "app/src/main/java/com/mohnishraj/goldmineledger/SystemBars.kt").read_text(encoding="utf-8")
dashboard_layout = (root / "app/src/main/res/layout/fragment_dashboard.xml").read_text(encoding="utf-8")
visuals = (root / "app/src/main/java/com/mohnishraj/goldmineledger/InsightVisuals.kt").read_text(encoding="utf-8")
for token in [
    "openRootDestination", ".setPopUpTo(R.id.dashboardFragment, false)",
    ".setLaunchSingleTop(true)", "HapticFeedbackConstants",
    "keepQuickAddAboveDock", "bringToFront", "BottomSheetDialog",
]:
    if token not in main_activity:
        raise SystemExit(f"Root/FAB contract missing: {token}")
for token in [
    'android:clipChildren="false"',
    'app:layout_constraintBottom_toBottomOf="@id/bottomDock"',
    'android:layout_marginBottom="18dp"',
    'app:fabCustomSize="64dp"',
    'android:translationZ="24dp"',
]:
    if token not in activity_layout:
        raise SystemExit(f"OEM-safe dock Quick Add contract missing: {token}")
if 'android:translationY=' in activity_layout:
    raise SystemExit("Quick Add must not rely on a fragile Y translation")
for token in ["applySafeSystemBars(binding.root)", "applySafeSystemBars(b.root)"]:
    if token not in main_activity + onboarding_activity:
        raise SystemExit(f"System-bar application missing: {token}")
for token in ["WindowInsetsCompat.Type.systemBars()", "WindowInsetsCompat.Type.displayCutout()"]:
    if token not in system_bars:
        raise SystemExit(f"System-bar inset contract missing: {token}")
for token in ['app:endIconDrawable="@drawable/ic_calendar"', 'android:focusable="false"', 'android:inputType="none"']:
    if token not in onboarding_layout:
        raise SystemExit(f"Calendar-only onboarding contract missing: {token}")
if "DatePickerDialog" not in onboarding_activity:
    raise SystemExit("Onboarding date picker is disconnected")
if 'android:layout_height="292dp"' not in dashboard_layout:
    raise SystemExit("Dashboard hero height regression: cash-flow ring may clip")
if "val diameter = (min(width, height)" not in visuals:
    raise SystemExit("FlowRingView must draw inside a centred square")
if "contentDescription = \"Cash-flow ring:" not in visuals or "Spending heatmap with activity" not in visuals:
    raise SystemExit("Custom finance visuals need accessibility descriptions")

# Theme API guards.
for path in ["app/src/main/res/values/themes.xml", "app/src/main/res/values-night/themes.xml"]:
    theme = (root / path).read_text(encoding="utf-8")
    if not re.search(r'android:windowLightNavigationBar"\s+tools:targetApi="27"', theme):
        raise SystemExit(f"API-27 navigation-bar lint annotation missing: {path}")

workflow = (root / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
for token in [
    "python3 tools/validate_project.py", "python3 tools/test_schema.py",
    "testDebugUnitTest", "lintDebug", "assembleRelease", "lint-results-debug.txt",
    "Ledgito-v1.2.0-Professional-Experience.apk", "Ledgito-v1.2.0-Professional-Experience-APK",
    "if-no-files-found: error",
]:
    if token not in workflow:
        raise SystemExit(f"GitHub Actions gate missing: {token}")

completion = json.loads((root / "docs/V1.2-COMPLETION.json").read_text(encoding="utf-8"))
for key, expected in {
    "product": "Ledgito", "release": "1.2 Professional Experience", "versionCode": 10,
    "versionName": "1.2.0-professional-experience", "databaseVersion": 3,
    "packageId": "com.mohnishraj.goldmineledger",
    "buildArtifact": "Ledgito-v1.2.0-Professional-Experience.apk",
    "githubArtifact": "Ledgito-v1.2.0-Professional-Experience-APK",
    "internetPermission": False, "platformAutoBackup": False,
    "explicitEncryptedBackup": True, "cloudDeferred": True,
    "quickAddBottomSheet": True, "dockFabRegressionFixed": True,
    "customHome": True, "swipeEditDeleteUndo": True,
    "planningHealth": True, "debtStrategyComparison": True,
    "allocationDonut": True, "behaviourAnalytics": True,
    "offlineSmartInsights": True,
}.items():
    if completion.get(key) != expected:
        raise SystemExit(f"Completion metadata mismatch: {key}={completion.get(key)!r}, expected {expected!r}")

print(
    f"Ledgito 1.2 validation passed: {len(xmls)} XML, {len(main_sources)} main Kotlin, "
    f"{len(test_sources)} test Kotlin, {len(bindings)} ViewBinding contracts, Room v3."
)
