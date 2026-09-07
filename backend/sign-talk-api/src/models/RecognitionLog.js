const { mongoose } = require("../config/db");

// One row per on-device recognition event the app chooses to report --
// "Logs/statistics" from the tech stack. userId is optional so the app can
// log anonymously (e.g. before login, or if a user opts out of attribution).
const recognitionLogSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: "User", default: null },
    predictedSlug: { type: String, required: true, trim: true },
    confidence: { type: Number, min: 0, max: 1, required: true },
    wasCorrect: { type: Boolean, default: null }, // set later if the user gives feedback
    modelVersion: { type: String, default: null }, // ties a log row to the ModelVersion active at prediction time
    deviceInfo: { type: String, default: "" }, // e.g. "Pixel 7 / Android 14" -- free text from the app
    createdAt: { type: Date, default: Date.now },
  },
  { timestamps: false }
);

recognitionLogSchema.index({ createdAt: -1 });
recognitionLogSchema.index({ predictedSlug: 1 });

module.exports = mongoose.models.RecognitionLog || mongoose.model("RecognitionLog", recognitionLogSchema);
