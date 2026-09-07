const { mongoose } = require("../config/db");

// Mirrors android/.../data/dictionary/DictionaryEntity + ai/dataset/labels_50.json,
// so this collection can serve as (or eventually replace) DictionarySeed.kt as
// the source of truth the app reads its dictionary from.
const dictionaryEntrySchema = new mongoose.Schema(
  {
    slug: { type: String, required: true, unique: true, index: true, trim: true },
    label: { type: String, required: true, trim: true },
    language: { type: String, enum: ["FSL", "ASL"], required: true },
    category: { type: String, required: true, trim: true },
    description: { type: String, required: true },
    emoji: { type: String, default: "" },
    // Whether a real bundled video exists for this entry (sign_videos/<slug>.mp4)
    // and whether real landmark training data exists (ai/dataset/raw/<slug>/*.npy).
    // Both are true for all 50 current entries -- see proposal-notes.md.
    hasVideo: { type: Boolean, default: false },
    hasTrainingData: { type: Boolean, default: false },
  },
  { timestamps: true }
);

dictionaryEntrySchema.index({ language: 1, category: 1 });
// Basic text search across label/description for GET /api/dictionary?q=...
// language_override is required here: MongoDB text indexes reserve a field
// named "language" by default to pick per-document stemming rules, which
// collides with our real `language` field ("FSL"/"ASL" aren't valid
// stemming languages and every write fails with "language override
// unsupported: FSL" otherwise). Pointing the override at an unused field
// name sidesteps the collision entirely.
dictionaryEntrySchema.index({ label: "text", description: "text" }, { language_override: "textSearchLanguage" });

module.exports = mongoose.models.DictionaryEntry || mongoose.model("DictionaryEntry", dictionaryEntrySchema);
