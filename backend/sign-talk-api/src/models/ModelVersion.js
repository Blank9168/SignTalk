const { mongoose } = require("../config/db");

// One row per trained sign_lstm.pt, populated from ai/models/metadata.json
// by scripts/registerModelVersion.js after each training run. Backs
// "Model/version information" from the tech stack, and lets the app query
// which label order / accuracy the currently-deployed model has without
// bundling metadata.json into the app itself.
const modelVersionSchema = new mongoose.Schema(
  {
    version: { type: String, required: true, unique: true }, // e.g. an ISO timestamp or short hash
    labels: { type: [String], required: true },
    numClasses: { type: Number, required: true },
    architecture: { type: String, default: "" },
    dataSource: { type: mongoose.Schema.Types.Mixed, default: {} },
    overallValAccuracy: { type: Number, default: null },
    macroPrecision: { type: Number, default: null },
    macroRecall: { type: Number, default: null },
    macroF1: { type: Number, default: null },
    epochsTrained: { type: Number, default: null },
    trainedAt: { type: Date, default: Date.now },
    notes: { type: String, default: "" },
    // Exactly one ModelVersion should be active at a time -- the one the
    // app/API should treat as current. registerModelVersion.js flips this.
    isActive: { type: Boolean, default: false },
  },
  { timestamps: true }
);

module.exports = mongoose.models.ModelVersion || mongoose.model("ModelVersion", modelVersionSchema);
