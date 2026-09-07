package com.example.signtalk.ui.dictionary

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.signtalk.SignTalkApp
import com.example.signtalk.databinding.DialogAddDictionaryEntryBinding
import com.example.signtalk.domain.model.DictionaryEntry
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Plain-Views replacement for the old Compose `AddDictionaryEntryDialog`.
 * Talks straight to [com.example.signtalk.domain.repository.DictionaryRepository]
 * rather than sharing a ViewModel with the host fragment -- the host's list
 * is a Room [kotlinx.coroutines.flow.Flow] and refreshes on its own the
 * moment a new row is inserted, so no explicit callback is needed here.
 */
class AddDictionaryEntryDialogFragment : DialogFragment() {

    private var _binding: DialogAddDictionaryEntryBinding? = null
    private val binding get() = _binding!!

    // An optional video (picked from the gallery/file picker, not recorded)
    // showing this sign being performed. Uses ACTION_OPEN_DOCUMENT (via this
    // contract) rather than GET_CONTENT specifically because it's the one
    // that lets us take a *persistable* read permission below -- GET_CONTENT's
    // grant is only good for the current app process, so the URI would stop
    // resolving the next time the app is launched and this row is viewed.
    // Registered here (a property initializer, so it runs during the
    // fragment's construction) rather than inside onCreateDialog, since
    // registerForActivityResult must be called before the fragment reaches
    // STARTED.
    private var selectedVideoUri: Uri? = null
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        selectedVideoUri = uri
        binding.addVideoButton.text = "Video selected ✓ (tap to change)"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddDictionaryEntryBinding.inflate(layoutInflater)

        binding.addVideoButton.setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add a sign")
            .setView(binding.root)
            .setPositiveButton("Add", null) // wired manually in onStart so a blank name doesn't dismiss the dialog
            .setNegativeButton("Cancel") { _, _ -> dismiss() }
            .create()

        dialog.setOnShowListener {
            (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.nameInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    binding.nameInput.error = "Required"
                    return@setOnClickListener
                }
                addEntry(name)
            }
        }
        return dialog
    }

    private fun addEntry(name: String) {
        val appContainer = (requireActivity().application as SignTalkApp).container
        val category = binding.categoryInput.text?.toString()?.trim().orEmpty()
        val emoji = binding.emojiInput.text?.toString()?.trim().orEmpty()
        val description = binding.descriptionInput.text?.toString()?.trim().orEmpty()

        lifecycleScope.launch {
            appContainer.dictionaryRepository.addEntry(
                DictionaryEntry(
                    label = name.trim().lowercase().replace(Regex("\\s+"), "_"),
                    displayName = name,
                    category = category.ifBlank { "Custom" },
                    description = description,
                    emoji = emoji.ifBlank { "🤟" },
                    isUserAdded = true,
                    videoUri = selectedVideoUri?.toString()
                )
            )
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
