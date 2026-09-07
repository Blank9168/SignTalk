package com.example.signtalk

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.signtalk.databinding.ActivityMainBinding

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // targetSdk 35 (Android 15) enforces edge-to-edge by default, so the
        // status bar would otherwise draw over the toolbar -- pad it by the
        // top system-bar inset instead of opting back out of edge-to-edge.
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Home is the "top" of the app from the user's point of view (post-login) --
        // no back arrow there, but every screen below it gets one automatically.
        appBarConfiguration = AppBarConfiguration(setOf(R.id.homeFragment))
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.visibility =
                if (destination.id == R.id.splashFragment) View.GONE else View.VISIBLE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        return navHostFragment.navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
