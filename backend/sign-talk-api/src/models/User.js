const { mongoose } = require("../config/db");

// Firebase owns credentials/login; this collection only holds the app-side
// profile + role for a Firebase-authenticated user (per the tech stack:
// "Firebase -- authentication only, not the full backend").
const userSchema = new mongoose.Schema(
  {
    firebaseUid: { type: String, required: true, unique: true, index: true },
    email: { type: String, required: true, lowercase: true, trim: true },
    displayName: { type: String, trim: true, default: "" },
    role: { type: String, enum: ["user", "admin"], default: "user" },
    lastLoginAt: { type: Date, default: Date.now },
  },
  { timestamps: true }
);

module.exports = mongoose.models.User || mongoose.model("User", userSchema);
