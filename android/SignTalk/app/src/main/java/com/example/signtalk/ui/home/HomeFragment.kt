package com.example.signtalk.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.signtalk.R
import com.example.signtalk.databinding.FragmentHomeBinding
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Fragment instances survive on the back stack, so this keeps the entrance
    // animation to the first time Home appears instead of every return to it.
    private var hasAnimatedIn = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.timeGreetingText.text = timeOfDayGreeting()

        binding.recognitionCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_recognition)
        }
        binding.translateCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_translate)
        }
        binding.dictionaryCard.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_dictionary)
        }
        binding.settingsButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }

        if (!hasAnimatedIn && savedInstanceState == null) {
            hasAnimatedIn = true
            animateIn()
        }
    }

    /** The same three greetings the app can sign (FSL Magandang Umaga / Hapon / Gabi). */
    private fun timeOfDayGreeting(): String =
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Magandang umaga"
            in 12..17 -> "Magandang hapon"
            else -> "Magandang gabi"
        }

    /** Hero and cards rise into place one after another. */
    private fun animateIn() {
        val rise = 24f * resources.displayMetrics.density
        val views = listOf(
            binding.heroCard,
            binding.recognitionCard,
            binding.translateCard,
            binding.dictionaryCard
        )
        views.forEachIndexed { index, v ->
            v.alpha = 0f
            v.translationY = rise
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L * index)
                .setDuration(380)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
