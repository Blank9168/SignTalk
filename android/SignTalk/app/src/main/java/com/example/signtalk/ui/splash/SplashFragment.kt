package com.example.signtalk.ui.splash

import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.signtalk.R
import com.example.signtalk.SignTalkApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Brief branded splash, then the first-time tutorial (once) or Home -- no login gate. */
class SplashFragment : Fragment(R.layout.fragment_splash) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        animateIn(view)

        viewLifecycleOwner.lifecycleScope.launch {
            val settings = (requireActivity().application as SignTalkApp).container.settingsRepository
            delay(SPLASH_DURATION_MS)
            val onboardingDone = settings.settings.first().onboardingDone
            findNavController().navigate(
                if (onboardingDone) R.id.action_splash_to_home else R.id.action_splash_to_onboarding
            )
        }
    }

    /** Logo pops in, then the name and tagline rise and fade in after it. */
    private fun animateIn(root: View) {
        val logo = root.findViewById<View>(R.id.splashLogo)
        val title = root.findViewById<View>(R.id.splashTitle)
        val tagline = root.findViewById<View>(R.id.splashTagline)
        val rise = 16f * resources.displayMetrics.density

        logo.alpha = 0f
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420).setInterpolator(OvershootInterpolator(1.6f)).start()

        listOf(title, tagline).forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = rise
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(180L + index * 80L).setDuration(320).start()
        }
    }

    companion object {
        private const val SPLASH_DURATION_MS = 900L
    }
}
