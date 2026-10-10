package com.example.signtalk.domain.model

/**
 * A single entry in the sign dictionary: one sign plus human-readable info about it.
 * [isUserAdded] distinguishes entries the user created via "Add a sign" from the bundled set.
 *
 * The linguistic fields keep the sign separate from its spoken-language meanings, so users
 * don't read FSL as "English or Tagalog spelled with hands":
 * - [gloss]: the sign's gloss (FSL/ASL label), e.g. "ONE" or "THANKYOU"
 * - [meaningEn] / [meaningFil]: what it means in English and in Filipino
 */
data class DictionaryEntry(
    val id: Long = 0L,
    val label: String,
    val displayName: String,
    val category: String,
    val description: String,
    val emoji: String,
    val isUserAdded: Boolean = false,
    // Content-resolver URI (string form) of a video the user attached showing
    // this sign performed, or null if none has been added yet.
    val videoUri: String? = null,
    /** "FSL", "ASL" or "" (unknown, e.g. a personal entry). */
    val language: String = "",
    val gloss: String = "",
    val meaningEn: String = "",
    val meaningFil: String = "",
    /** Extra search words (lowercase), e.g. "one", "1", "uno" for Isa. */
    val synonyms: List<String> = emptyList(),
    /** "static" or "dynamic". */
    val signType: String = "",
    /** "one" or "two". */
    val hands: String = "",
    /** Where the reference video comes from, e.g. "FSL-105 dataset (DLSU / DOST, CC BY 4.0)". */
    val source: String = "",
    /** See [ValidationStatus]. */
    val validationStatus: String = ValidationStatus.UNVERIFIED,
    val validatedBy: String = ""
)

/** Values of [DictionaryEntry.validationStatus]. */
object ValidationStatus {
    /** Bundled sign not yet checked by an FSL validator. */
    const val UNVERIFIED = "unverified"
    /** Checked and approved by a named FSL validator. */
    const val VERIFIED = "verified"
    /** Added by this user on this phone only; not reviewed, never shared. */
    const val PERSONAL = "personal"
    /** Added by this user and sent to the SignTalk team for review. */
    const val SUBMITTED = "submitted"
}
