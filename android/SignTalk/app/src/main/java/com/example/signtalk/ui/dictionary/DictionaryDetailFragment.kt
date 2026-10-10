package com.example.signtalk.ui.dictionary

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.FragmentDictionaryDetailBinding
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.model.ValidationStatus
import com.example.signtalk.ui.common.SignVideoPlayer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * One sign: its reference video (looping, with Replay and 0.5x Slow), how to sign it, and an
 * "About this sign" card that keeps the sign's gloss separate from its English and Filipino
 * meanings and shows sign type, hands, source and validation status.
 *
 * Video playback uses [SignVideoPlayer] (TextureView + MediaPlayer; a SurfaceView-based
 * VideoView rendered as a black box inside Fragments).
 */
class DictionaryDetailFragment : Fragment() {

    private var _binding: FragmentDictionaryDetailBinding? = null
    private val binding get() = _binding!!

    private var entryId: Long = 0L
    private var player: SignVideoPlayer? = null

    // Lets a *custom* entry (one with no bundled clip) get a video attached or replaced. Uses
    // ACTION_OPEN_DOCUMENT so a persistable read permission can be taken (GET_CONTENT's grant
    // only lasts the current process). Registered as a property because
    // registerForActivityResult must be called before the fragment reaches STARTED.
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val appContainer = (requireActivity().application as SignTalkApp).container
        viewLifecycleOwner.lifecycleScope.launch {
            appContainer.dictionaryRepository.setVideoUri(entryId, uri.toString())
            showVideo(uri)
            binding.addVideoButton.visibility = View.GONE
            binding.changeVideoButton.visibility = View.VISIBLE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionaryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val appContainer = (requireActivity().application as SignTalkApp).container
        entryId = arguments?.getLong("entryId") ?: 0L
        player = SignVideoPlayer(requireContext(), binding.entryVideoView, "DictionaryDetail")

        binding.addVideoButton.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }
        binding.changeVideoButton.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }
        binding.replayVideoButton.setOnClickListener { player?.restart() }
        binding.slowVideoButton.addOnCheckedChangeListener { _, checked ->
            player?.speed = if (checked) 0.5f else 1f
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val entry = appContainer.dictionaryRepository.getEntry(entryId)
            if (entry == null) {
                binding.loadingText.text = "Sign not found."
                return@launch
            }
            binding.loadingText.visibility = View.GONE
            binding.contentGroup.visibility = View.VISIBLE
            binding.entryEmoji.text = entry.emoji
            binding.entryName.text = entry.displayName
            binding.entryCategory.text = entry.category
            binding.entryCategory.visibility = if (entry.category.isBlank()) View.GONE else View.VISIBLE
            binding.entryDescription.text = entry.description.ifBlank { "No description yet." }
            binding.entryFacts.text = facts(entry)
            binding.entryValidation.text = validationText(entry)
            bindVideo(entry)

            if (entry.isUserAdded) {
                // Only the user's own personal entries can be deleted, and only on this phone.
                // Shared dictionary content is never deleted from the app (admins archive it).
                binding.deleteButton.visibility = View.VISIBLE
                binding.deleteButton.setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Delete \"${entry.displayName}\"?")
                        .setMessage("This removes your personal entry from this phone.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete") { _, _ ->
                            viewLifecycleOwner.lifecycleScope.launch {
                                appContainer.dictionaryRepository.deleteEntry(entry.id)
                                findNavController().popBackStack()
                            }
                        }
                        .show()
                }
            }
        }
    }

    private fun facts(entry: DictionaryEntry): String = buildList {
        val language = when (entry.language) {
            "FSL" -> "Filipino Sign Language (FSL)"
            "ASL" -> "American Sign Language (ASL)"
            else -> null
        }
        language?.let { add("Language: $it") }
        if (entry.gloss.isNotBlank()) add("Sign gloss: ${entry.gloss}")
        if (entry.meaningEn.isNotBlank()) add("English meaning: ${entry.meaningEn}")
        if (entry.meaningFil.isNotBlank()) add("Filipino meaning: ${entry.meaningFil}")
        when (entry.hands) {
            "one" -> add("Hands: one hand")
            "two" -> add("Hands: two hands")
        }
        when (entry.signType) {
            "static" -> add("Movement: static (held handshape)")
            "dynamic" -> add("Movement: dynamic (moving sign)")
        }
        if (entry.synonyms.isNotEmpty()) add("Also found by: ${entry.synonyms.joinToString(", ")}")
        if (entry.source.isNotBlank()) add("Video source: ${entry.source}")
    }.joinToString("\n").ifBlank { "No details yet." }

    private fun validationText(entry: DictionaryEntry): String = when (entry.validationStatus) {
        ValidationStatus.VERIFIED ->
            "Verified by an FSL validator" + if (entry.validatedBy.isNotBlank()) " (${entry.validatedBy})." else "."
        ValidationStatus.PERSONAL ->
            "Personal entry, not verified. Only on this phone; the camera cannot recognize it."
        ValidationStatus.SUBMITTED ->
            "Sent for review, not verified yet. The camera cannot recognize it."
        else ->
            "Not yet verified by an FSL validator."
    }

    /**
     * Shows this sign's bundled reference clip if one exists (all 50 bundled signs).
     * Otherwise (a personal entry) shows the video the user attached, with "Change video", or
     * an "Add a video" prompt.
     */
    private suspend fun bindVideo(entry: DictionaryEntry) {
        val bundled = SignVideoPlayer.bundledClipUri(requireContext(), entry.label)
        if (bundled != null) {
            showVideo(bundled)
            binding.addVideoButton.visibility = View.GONE
            binding.changeVideoButton.visibility = View.GONE
            return
        }
        val uriString = entry.videoUri
        if (uriString.isNullOrBlank()) {
            binding.addVideoButton.visibility = if (entry.isUserAdded) View.VISIBLE else View.GONE
            binding.entryVideoView.visibility = View.GONE
            binding.videoControls.visibility = View.GONE
            binding.changeVideoButton.visibility = View.GONE
        } else {
            showVideo(Uri.parse(uriString))
            binding.addVideoButton.visibility = View.GONE
            binding.changeVideoButton.visibility = View.VISIBLE
        }
    }

    private fun showVideo(uri: Uri) {
        binding.entryVideoView.visibility = View.VISIBLE
        binding.videoControls.visibility = View.VISIBLE
        player?.play(uri)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }
}
