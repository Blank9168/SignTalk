package com.example.signtalk.ui.splash

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.signtalk.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brief branded splash, then routes straight to Home -- no login gate. */
class SplashFragment : Fragment(R.layout.fragment_splash) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            delay(700)
            findNavController().navigate(R.id.action_splash_to_home)
        }
    }
}
