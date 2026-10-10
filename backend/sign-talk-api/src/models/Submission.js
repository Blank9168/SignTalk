const { mongoose } = require("../config/db");
const { LANGUAGES, SIGN_TYPES, HANDS, SUBMISSION_STATUSES } = require("../config/constants");

// A community-proposed sign waiting for expert review.
//
// IMPORTANT: submissions (and entries published from them) are dictionary
// content only. They never touch the recognition model -- the TFLite model
// on the device is trained offline from ai/dataset and only changes when the
// team retrains and ships a new app build. Nothing here feeds training data.
//
// Lifecycle: submitted -> for_review -> verified -> published
//            (rejected is reachable from submitted / for_review / verified)
// See SUBMISSION_TRANSITIONS in config/constants.js.

const statusHistorySchema = new mongoose.Schema(
  {
    status: { type: String, enum: SUBMISSION_STATUSES, required: true },
    by: { type: String, default: null }, // Firebase uid, or null for an anonymous submitter
    at: { type: Date, default: Date.now },
    note: { type: String, default: "" },
  },
  { _id: false }
);

const submissionSchema = new mongoose.Schema(
  {
    name: { type: String, required: true, trim: true }, // becomes DictionaryEntry.label on publish
    slug: { type: String, required: true, trim: true, index: true }, // derived from name server-side
    language: { type: String, enum: LANGUAGES, default: "FSL" },
    gloss: { type: String, trim: true, default: "" },
    meaningEn: { type: String, required: true, trim: true },
    meaningFil: { type: String, required: true, trim: true },
    category: { type: String, required: true, trim: true },
    description: { type: String, trim: true, default: "" },
    signType: { type: String, enum: SIGN_TYPES, required: true },
    hands: { type: String, enum: HANDS, required: true },
    source: { type: String, required: true, trim: true },
    mediaUrl: { type: String, trim: true, default: "" },
    notes: { type: String, trim: true, default: "" },

    submittedBy: { type: String, default: null }, // Firebase uid when the submitter was logged in
    submitterName: { type: String, trim: true, default: "" },

    status: { type: String, enum: SUBMISSION_STATUSES, default: "submitted", index: true },
    reviewedBy: { type: String, default: null },
    reviewNote: { type: String, default: "" },
    publishedEntry: { type: mongoose.Schema.Types.ObjectId, ref: "DictionaryEntry", default: null },
    statusHistory: { type: [statusHistorySchema], default: [] },
  },
  { timestamps: true }
);

submissionSchema.index({ slug: 1, status: 1 });

module.exports = mongoose.models.Submission || mongoose.model("Submission", submissionSchema);
