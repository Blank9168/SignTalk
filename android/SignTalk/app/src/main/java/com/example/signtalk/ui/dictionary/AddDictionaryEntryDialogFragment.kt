package com.example.signtalk.ui.dictionary

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.signtalk.SignTalkApp
import com.example.signtalk.data.remote.RetrofitClient
import com.example.signtalk.data.remote.dto.SubmissionRequestDto
import com.example.signtalk.databinding.DialogAddDictionaryEntryBinding
import com.example.signtalk.domain.model.DictionaryEntry
import com.example.signtalk.domain.model.ValidationStatus
import com.example.signtalk.domain.repository.DictionaryRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * "Add a sign".
 *
 * - Every required field from the expert review is enforced: name, English meaning,
 *   Filipino meaning, category, language, sign type, hands, source and a demonstration video
 *   (a file on the phone and/or a link reviewers can open).
 * - Duplicates are checked against the dictionary (name, label and synonyms) before saving.
 * - The entry is saved on this phone as a *personal, not verified* entry; it is never
 *   published to other users from here. With "Send for review" on, it is also posted to
 *   POST /api/submissions, which starts the Submitted -> For Review -> Verified -> Published
 *   workflow that only a validator or admin can move forward.
 * - Adding a sign never changes the recognition model (the dialog says so).
 */
class AddDictionaryEntryDialogFragment : DialogFragment() {

    private var _binding: DialogAddDictionaryEntryBinding? = null
    private val binding get() = _binding!!

    // Picked (not recorded) video; ACTION_OPEN_DOCUMENT so a persistable read permission can
    // be taken (GET_CONTENT's grant only lasts the current app process).
    private var selectedVideoUri: Uri? = null
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        selectedVideoUri = uri
        binding.addVideoButton.text = "Video selected (tap to change)"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddDictionaryEntryBinding.inflate(layoutInflater)

        binding.categoryInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, CATEGORIES)
        )
        binding.sourceInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, SOURCES)
        )
        binding.addVideoButton.setOnClickListener { pickVideoLauncher.launch(arrayOf("video/*")) }
        binding.submitSwitch.setOnCheckedChangeListener { _, checked ->
            binding.submitterLayout.visibility = if (checked) View.VISIBLE else View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add a sign")
            .setView(binding.root)
            .setPositiveButton("Save", null) // wired in setOnShowListener so errors don't dismiss the dialog
            .setNegativeButton("Cancel") { _, _ -> dismiss() }
            .create()

        dialog.setOnShowListener {
            (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val form = readValidForm() ?: return@setOnClickListener
                save(form)
            }
        }
        return dialog
    }

    private data class Form(
        val name: String,
        val meaningEn: String,
        val meaningFil: String,
        val category: String,
        val language: String,
        val signType: String,
        val hands: String,
        val source: String,
        val description: String,
        val emoji: String,
        val mediaUrl: String,
        val submit: Boolean,
        val submitterName: String
    )

    /** Validates every required field and shows the error next to it. Null if anything is missing. */
    private fun readValidForm(): Form? {
        fun text(v: android.widget.TextView) = v.text?.toString()?.trim().orEmpty()
        var ok = true
        fun required(value: String, layout: com.google.android.material.textfield.TextInputLayout): String {
            layout.error = if (value.isBlank()) { ok = false; "Required" } else null
            return value
        }
        val name = required(text(binding.nameInput), binding.nameLayout)
        val meaningEn = required(text(binding.meaningEnInput), binding.meaningEnLayout)
        val meaningFil = required(text(binding.meaningFilInput), binding.meaningFilLayout)
        val category = required(text(binding.categoryInput), binding.categoryLayout)
        val source = required(text(binding.sourceInput), binding.sourceLayout)

        val signType = when (binding.signTypeChips.checkedChipId) {
            binding.chipStatic.id -> "static"
            binding.chipDynamic.id -> "dynamic"
            else -> null
        }
        val hands = when (binding.handsChips.checkedChipId) {
            binding.chipOneHand.id -> "one"
            binding.chipTwoHands.id -> "two"
            else -> null
        }
        markLabel(binding.signTypeLabel, "Sign type *", signType == null).also { if (signType == null) ok = false }
        markLabel(binding.handsLabel, "Hands *", hands == null).also { if (hands == null) ok = false }

        val mediaUrl = text(binding.mediaUrlInput)
        if (mediaUrl.isNotBlank() && !(mediaUrl.startsWith("http") && Patterns.WEB_URL.matcher(mediaUrl).matches())) {
            binding.mediaUrlLayout.error = "Enter a full link starting with https://"
            ok = false
        } else {
            binding.mediaUrlLayout.error = null
        }
        val hasMedia = selectedVideoUri != null || mediaUrl.isNotBlank()
        markLabel(binding.mediaLabel, "Demonstration video * (pick a file, or paste a link, or both)", !hasMedia)
        if (!hasMedia) ok = false

        if (!ok) return null
        return Form(
            name = name,
            meaningEn = meaningEn,
            meaningFil = meaningFil,
            category = category,
            language = if (binding.languageChips.checkedChipId == binding.chipLangAsl.id) "ASL" else "FSL",
            signType = signType!!,
            hands = hands!!,
            source = source,
            description = text(binding.descriptionInput),
            emoji = text(binding.emojiInput),
            mediaUrl = mediaUrl,
            submit = binding.submitSwitch.isChecked,
            submitterName = text(binding.submitterInput)
        )
    }

    private fun markLabel(label: android.widget.TextView, text: String, missing: Boolean) {
        label.text = if (missing) "$text  (required)" else text
        label.setTextColor(
            com.google.android.material.color.MaterialColors.getColor(
                label,
                if (missing) com.google.android.material.R.attr.colorError
                else com.google.android.material.R.attr.colorOnSurface
            )
        )
    }

    private fun save(form: Form) {
        val appContainer = (requireActivity().application as SignTalkApp).container
        val repository = appContainer.dictionaryRepository
        val slug = slugify(form.name)
        val appContext = requireContext().applicationContext
        val activityScope = requireActivity().lifecycleScope

        lifecycleScope.launch {
            val duplicate = findDuplicate(repository, form, slug)
            if (duplicate != null) {
                binding.duplicateText.text =
                    "\"${duplicate.displayName}\" is already in the dictionary" +
                        (if (duplicate.isUserAdded) " (your entry)" else "") +
                        ". Open it from the Dictionary instead of adding it again."
                binding.duplicateText.visibility = View.VISIBLE
                return@launch
            }

            val entry = DictionaryEntry(
                label = slug,
                displayName = form.name,
                category = "${form.language} - ${form.category}",
                description = form.description,
                emoji = form.emoji.ifBlank { "🤟" },
                isUserAdded = true,
                videoUri = selectedVideoUri?.toString(),
                language = form.language,
                meaningEn = form.meaningEn,
                meaningFil = form.meaningFil,
                synonyms = listOf(form.meaningEn, form.meaningFil)
                    .map { it.lowercase() }
                    .filter { it != form.name.lowercase() }
                    .distinct(),
                signType = form.signType,
                hands = form.hands,
                source = form.source,
                validationStatus = ValidationStatus.PERSONAL
            )
            val id = repository.addEntry(entry)
            dismiss()

            if (!form.submit) {
                Toast.makeText(appContext, "Saved on this phone as a personal entry.", Toast.LENGTH_LONG).show()
                return@launch
            }
            // The submission outlives the dialog, so run it in the activity's scope.
            activityScope.launch {
                val message = submitForReview(form, repository, id)
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Same name, same label, or the name is already a synonym of another sign. */
    private suspend fun findDuplicate(repository: DictionaryRepository, form: Form, slug: String): DictionaryEntry? {
        repository.findByLabel(slug)?.let { return it }
        val lowered = form.name.lowercase()
        repository.findBySynonym(lowered)?.let { return it }
        return repository.getAll().firstOrNull {
            it.displayName.equals(form.name, ignoreCase = true) ||
                (it.meaningEn.isNotBlank() && it.meaningEn.equals(form.meaningEn, ignoreCase = true) &&
                    it.language == form.language)
        }
    }

    private suspend fun submitForReview(form: Form, repository: DictionaryRepository, localId: Long): String {
        return try {
            val response = RetrofitClient.apiService.submitSign(
                SubmissionRequestDto(
                    name = form.name,
                    meaningEn = form.meaningEn,
                    meaningFil = form.meaningFil,
                    category = "${form.language} - ${form.category}",
                    signType = form.signType,
                    hands = form.hands,
                    source = form.source,
                    language = form.language,
                    description = form.description.ifBlank { null },
                    mediaUrl = form.mediaUrl.ifBlank { null },
                    notes = if (selectedVideoUri != null) "Submitter also has the video on their phone." else null,
                    submitterName = form.submitterName.ifBlank { null }
                )
            )
            when (response.code()) {
                201, 200 -> {
                    repository.setValidationStatus(localId, ValidationStatus.SUBMITTED)
                    "Sent for review. It stays \"not verified\" until a validator approves it."
                }
                409 -> "Saved on this phone. Not sent: this sign is already in the dictionary or waiting for review."
                429 -> "Saved on this phone. Too many submissions right now; try again later."
                else -> "Saved on this phone. The server did not accept it (error ${response.code()})."
            }
        } catch (e: Exception) {
            Log.w("AddSign", "Submission failed: ${e.message}")
            "Saved on this phone. Couldn't reach the SignTalk server, so it wasn't sent for review."
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Same shape as the backend's slugify (letters/digits joined by underscores). */
        fun slugify(name: String): String =
            name.trim().lowercase()
                .replace("'", "")
                .replace(Regex("[^a-z0-9ñ]+"), "_")
                .trim('_')

        val CATEGORIES = listOf(
            "Greetings", "Basic Responses", "Numbers", "Colors", "Family", "People & Relationships",
            "Needs", "Emergency", "School", "Food", "Common Questions", "Other"
        )

        val SOURCES = listOf(
            "Recorded by me",
            "FSL teacher or interpreter",
            "Deaf community member",
            "Book or published dictionary",
            "Website or video"
        )
    }
}
