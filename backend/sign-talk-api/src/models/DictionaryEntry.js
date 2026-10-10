const { mongoose } = require("../config/db");
const { LANGUAGES, SIGN_TYPES, HANDS, VALIDATION_STATUSES } = require("../config/constants");

// Mirrors android/.../data/dictionary/DictionaryEntity + ai/dataset/labels_50.json,
// so this collection can serve as (or eventually replace) DictionarySeed.kt as
// the source of truth the app reads its dictionary from.
const dictionaryEntrySchema = new mongoose.Schema(
  {
    // slug is the stable id shared with the Android app and the model's label
    // list -- it is never editable after creation (PATCH rejects it).
    slug: { type: String, required: true, unique: true, index: true, trim: true },
    label: { type: String, required: true, trim: true },
    language: { type: String, enum: LANGUAGES, required: true },
    category: { type: String, required: true, trim: true },
    description: { type: String, required: true },
    emoji: { type: String, default: "" },
    // Whether a real bundled video exists for this entry (sign_videos/<slug>.mp4)
    // and whether real landmark training data exists (ai/dataset/raw/<slug>/*.npy).
    // Both are true for all 50 current entries -- see proposal-notes.md.
    hasVideo: { type: Boolean, default: false },
    hasTrainingData: { type: Boolean, default: false },

    // --- Linguistic metadata (requested by the FSL expert review) ---
    gloss: { type: String, trim: true, default: "" }, // FSL/ASL gloss, e.g. "ISA"
    meaningEn: { type: String, trim: true, default: "" },
    meaningFil: { type: String, trim: true, default: "" },
    synonyms: { type: [{ type: String, lowercase: true, trim: true }], default: [] },
    signType: { type: String, enum: SIGN_TYPES },
    hands: { type: String, enum: HANDS },
    // Provenance, e.g. "FSL-105 (DLSU/DOST)", "ASL Citizen (Microsoft Research)", "Team-recorded"
    source: { type: String, trim: true, default: "" },

    // --- Expert validation ---
    validationStatus: { type: String, enum: VALIDATION_STATUSES, default: "unverified", index: true },
    validatedBy: { type: String, default: null }, // Firebase uid of the validator/admin
    validatedAt: { type: Date, default: null },

    // --- Soft delete (entries are archived, never hard-deleted) ---
    archived: { type: Boolean, default: false, index: true },
    archivedAt: { type: Date, default: null },
    archivedBy: { type: String, default: null },

    // --- Authorship (Firebase uids; "seed-script" for seeded rows) ---
    createdBy: { type: String, default: null },
    updatedBy: { type: String, default: null },
  },
  { timestamps: true }
);

dictionaryEntrySchema.index({ language: 1, category: 1 });
// Text search for GET /api/dictionary?q=... across label, gloss, synonyms,
// both meanings, and description.
//
// language_override is required here: MongoDB text indexes reserve a field
// named "language" by default to pick per-document stemming rules, which
// collides with our real `language` field ("FSL"/"ASL" aren't valid
// stemming languages and every write fails with "language override
// unsupported: FSL" otherwise). Pointing the override at an unused field
// name sidesteps the collision entirely. Do not remove it.
//
// default_language "none" disables English stemming/stop-words, which would
// otherwise mangle Filipino words. A collection may only have ONE text index,
// so the old {label, description} index must be dropped when this one is
// introduced -- server.js and seedDictionary.js call syncIndexes() for that.
dictionaryEntrySchema.index(
  { label: "text", gloss: "text", synonyms: "text", meaningEn: "text", meaningFil: "text", description: "text" },
  {
    name: "dictionary_text_search",
    language_override: "textSearchLanguage",
    default_language: "none",
    weights: { label: 10, gloss: 6, synonyms: 6, meaningEn: 4, meaningFil: 4, description: 1 },
  }
);

module.exports = mongoose.models.DictionaryEntry || mongoose.model("DictionaryEntry", dictionaryEntrySchema);
