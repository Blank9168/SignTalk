package com.example.signtalk

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.signtalk.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Single Activity + Jetpack Navigation Component (Fragments), XML layouts
 * throughout -- see docs/MOBILE_APP_NOTES.md for why this replaced the
 * original Jetpack Compose UI. The shared [MaterialToolbar] here supplies
 * every screen's title + automatic back button (via [AppBarConfiguration]);
 * only the splash screen hides it for a fullscreen brand moment.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    /** The four screens reachable from the bottom navigation bar. */
    private val tabDestinations = setOf(
        R.id.homeFragment,
        R.id.recognitionFragment,
        R.id.translateFragment,
        R.id.dictionaryListFragment
    )

    private var highContrast = false
    private var systemBarInsets: Insets = Insets.NONE
    private var imeBottomInset: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars on every Android version
        // (Android 15 forces this anyway), and pad content by the bar insets below.
        enableEdgeToEdge()
        // High-contrast option (Settings). Read once, synchronously, so the very first frame
        // already uses the right theme; SettingsFragment recreates the activity on change.
        highContrast = runCatching {
            runBlocking {
                (application as SignTalkApp).container.settingsRepository.settings.first().highContrast
            }
        }.getOrDefault(false)
        if (highContrast) setTheme(R.style.Theme_SignTalk_HighContrast)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // The status bar draws over the toolbar in edge-to-edge mode, so pad
        // the toolbar by the top system-bar inset.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        // The tab screens are top-level (no back arrow); Settings and sign
        // details get one automatically.
        appBarConfiguration = AppBarConfiguration(tabDestinations)
        setupActionBarWithNavController(navController, appBarConfiguration)
        setUpBottomNav()

        // Keep screen content clear of the navigation bar and the keyboard; on
        // Home (no toolbar) also clear of the status bar. Splash stays full-bleed.
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            applyContentInsets()
            insets
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in tabDestinations) {
                binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
            }
            applyContentInsets()
            // Splash sits on the dark teal gradient: always use light status-bar icons there.
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                destination.id != R.id.splashFragment && !isNightMode() && !highContrast
        }
    }

    /**
     * Tabs always land on a single copy of the screen just above Home, so Back
     * from any tab returns to Home instead of walking through every tab visited.
     */
    private fun setUpBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            val current = navController.currentDestination?.id
            if (current == item.itemId) return@setOnItemSelectedListener true
            if (item.itemId == R.id.homeFragment) {
                navController.popBackStack(R.id.homeFragment, false)
            } else {
                val options = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.homeFragment, false)
                    .setEnterAnim(R.anim.tab_enter)
                    .setExitAnim(R.anim.tab_exit)
                    .setPopEnterAnim(R.anim.tab_enter)
                    .setPopExitAnim(R.anim.tab_exit)
                    .build()
                navController.navigate(item.itemId, null, options)
            }
            true
        }
        // Re-tapping the current tab does nothing.
        binding.bottomNav.setOnItemReselectedListener { }
    }

    /**
     * Shows/hides the toolbar and bottom bar for the current screen and keeps
     * content clear of the system bars and keyboard. Home draws its own header,
     * so the shared toolbar would just repeat the app name there; Splash is
     * full-bleed. The bottom bar hides while the keyboard is open.
     */
    private fun applyContentInsets() {
        val destinationId = navController.currentDestination?.id
        val isSplash = destinationId == R.id.splashFragment
        // Home and the tutorial draw their own headers, so they skip the shared toolbar.
        val isHome = destinationId == R.id.homeFragment || destinationId == R.id.onboardingFragment
        val keyboardOpen = imeBottomInset > 0
        val showBottomNav = destinationId != null && destinationId in tabDestinations && !keyboardOpen

        binding.toolbar.isVisible = !(isSplash || isHome)
        binding.bottomNav.isVisible = showBottomNav
        binding.navHostFragment.updatePadding(
            top = if (isHome) systemBarInsets.top else 0,
            bottom = when {
                isSplash || showBottomNav -> 0 // the bottom bar pads itself for the system bar
                else -> maxOf(systemBarInsets.bottom, imeBottomInset)
            }
        )
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
