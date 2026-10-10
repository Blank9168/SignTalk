package com.example.signtalk.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.signtalk.R
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentOnboardingBinding
import kotlinx.coroutines.launch

/**
 * Short first-time tutorial (shown once after the splash; re-open it from Home > "How to use"
 * or Settings). Covers camera position, distance, lighting and each main feature, with one
 * idea per page and little text.
 */
class OnboardingFragment : Fragment() {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    private data class Page(@DrawableRes val icon: Int, val title: String, val body: String)

    private val pages = listOf(
        Page(
            R.drawable.ic_sign_hand,
            "Welcome to SignTalk",
            "• Sign to the camera: SignTalk shows the word and can say it out loud.\n" +
                "• Type or speak a word: SignTalk shows its sign.\n" +
                "• It knows 50 selected signs: 25 FSL and 25 ASL."
        ),
        Page(
            R.drawable.ic_videocam,
            "Set up the camera",
            "• Hold or prop the phone at chest height.\n" +
                "• Stand about an arm's length away (50–80 cm).\n" +
                "• Keep your head, shoulders and hands inside the frame on screen."
        ),
        Page(
            R.drawable.ic_info,
            "Light and background",
            "• Face the light. Avoid a bright window behind you.\n" +
                "• A plain background works best.\n" +
                "• If it is too dark, SignTalk will tell you."
        ),
        Page(
            R.drawable.ic_check_circle,
            "Reading the result",
            "• Detecting: hold the sign steady.\n" +
                "• Recognized (green): the word is accepted and spoken once.\n" +
                "• Not sure (amber): repeat the sign. Tap Try again to start over.\n" +
                "• The phone vibrates when a sign is accepted."
        ),
        Page(
            R.drawable.ic_book,
            "More features",
            "• Text / Speech to Sign: shows the dictionary sign for each word. It is not full FSL sentence translation.\n" +
                "• Dictionary: search in English or Filipino.\n" +
                "• Add a Sign: saves a sign on your phone and can send it for review.\n" +
                "• Settings: speech, vibration, sounds and high contrast."
        )
    )

    private var index = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        index = savedInstanceState?.getInt(KEY_INDEX) ?: 0
        binding.backButton.setOnClickListener { if (index > 0) { index--; render() } }
        binding.nextButton.setOnClickListener {
            if (index < pages.lastIndex) { index++; render() } else finish()
        }
        binding.skipButton.setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val page = pages[index]
        binding.pageIcon.setImageResource(page.icon)
        binding.pageTitle.text = page.title
        binding.pageBody.text = page.body
        binding.pageIndicator.text = "Step ${index + 1} of ${pages.size}"
        binding.backButton.isEnabled = index > 0
        binding.nextButton.text = if (index == pages.lastIndex) "Start" else "Next"
        binding.skipButton.visibility = if (index == pages.lastIndex) View.INVISIBLE else View.VISIBLE
        binding.pageContent.alpha = 0f
        binding.pageContent.animate().alpha(1f).setDuration(200).start()
        binding.pageContent.announceForAccessibility("${page.title}. ${page.body}")
    }

    private fun finish() {
        val container = (requireActivity().application as SignTalkApp).container
        viewLifecycleOwner.lifecycleScope.launch {
            container.settingsRepository.setOnboardingDone(true)
            val nav = findNavController()
            if (!nav.popBackStack()) {
                // Opened from the splash (no back stack): go to Home and drop the tutorial.
                nav.navigate(
                    R.id.homeFragment, null,
                    NavOptions.Builder().setPopUpTo(R.id.onboardingFragment, true).build()
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_INDEX, index)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val KEY_INDEX = "page_index"
    }
}
