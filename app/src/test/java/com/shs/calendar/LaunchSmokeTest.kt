package com.shs.calendar

import com.shs.calendar.ui.MainActivity
import com.shs.calendar.ui.appearance.Accent
import com.shs.calendar.ui.appearance.AppearanceStore
import com.shs.calendar.ui.appearance.Theme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Launch smoke test (M11 hardening).
 *
 * Boots the real [ShsCalendarApp] and the real [MainActivity] on the JVM
 * Android framework (Robolectric). Any crash on the startup path — theme
 * resolution, Application.onCreate, layout inflation, Activity.onCreate —
 * fails this test with the actual stack trace, so a build that cannot open
 * on a device can never ship.
 *
 * This is not hypothetical: v2.0.0 shipped a crash where
 * AppearanceStore dereferenced Context.getApplicationContext() from
 * Application.attachBaseContext, which is null on a real device during that
 * hook — the process died before any Activity existed, so the app installed
 * fine and never opened. Unit tests could not see it (no Robolectric then);
 * this file exists so it can never come back silently.
 *
 * Runs in each shipped locale (via [Config.qualifiers]): a crash that only
 * happens when Resources resolve against values-bn/ or values-ar/ must be
 * caught here too, because the devices our users hold are Bengali- and
 * Arabic-locale devices.
 */
abstract class LaunchSmokeTest {

    /** Launch the real MainActivity through its full lifecycle to onResume. */
    private fun launchMainActivity() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup() // onCreate -> onStart -> onRestore -> onResume
        // Reaching here means every lifecycle callback completed without a
        // crash. Decor view must exist and the activity must not be finishing.
        val activity = controller.get()
        check(activity.window.decorView != null)
        check(!activity.isFinishing)
        controller.pause().stop().destroy()
    }

    /** Persist an appearance choice first, then launch with it active. */
    internal fun launchWithAppearance(theme: Theme, accent: Accent) {
        // The companion caches per process; Robolectric builds a fresh
        // Application per test, so drop the stale instance first.
        AppearanceStore.resetForTest()
        AppearanceStore.get(RuntimeEnvironment.getApplication()).apply {
            saveTheme(theme)
            saveAccent(accent)
        }
        launchMainActivity()
    }

    @Test
    fun mainActivityLaunches() = launchMainActivity()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ShsCalendarApp::class)
class LaunchSmokeTestEn : LaunchSmokeTest()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ShsCalendarApp::class, qualifiers = "bn")
class LaunchSmokeTestBengali : LaunchSmokeTest()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ShsCalendarApp::class, qualifiers = "ar")
class LaunchSmokeTestArabic : LaunchSmokeTest()

/**
 * The non-default appearance paths. ThemeManager hands these to setTheme()
 * before super.onCreate(); a style with a wrong parent here would strip the
 * Material3 base attributes and crash the FIRST inflation instead — exactly
 * the class of bug the v2.0.0 overlays carried (parent=""), so each variant
 * is pinned by a launch test rather than trusted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ShsCalendarApp::class)
class LaunchSmokeTestAppearance : LaunchSmokeTest() {

    @Test
    fun mainActivityLaunchesWithAmoledTheme() = launchWithAppearance(Theme.AMOLED, Accent.CYAN)

    @Test
    fun mainActivityLaunchesWithTealAccentLightTheme() = launchWithAppearance(Theme.LIGHT, Accent.TEAL)

    @Test
    fun mainActivityLaunchesWithAmberAccentDarkTheme() = launchWithAppearance(Theme.DARK, Accent.AMBER)
}
