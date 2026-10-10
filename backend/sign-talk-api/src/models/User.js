const { mongoose } = require("../config/db");
const { ROLES } = require("../config/constants");

// Firebase owns credentials/login; this collection only holds the app-side
// profile + role for a Firebase-authenticated user (per the tech stack:
// "Firebase -- authentication only, not the full backend").
//
// Roles:
//   user      -- default; can submit signs and manage their own profile
//   validator -- FSL reviewer; can review submissions and edit/verify dictionary entries
//   admin     -- everything a validator can do, plus archive/restore entries,
//                change user roles, read the audit log and recognition stats
const userSchema = new mongoose.Schema(
  {
    firebaseUid: { type: String, required: true, unique: true, index: true },
    email: { type: String, required: true, lowercase: true, trim: true },
    displayName: { type: String, trim: true, default: "" },
    role: { type: String, enum: ROLES, default: "user" },
    lastLoginAt: { type: Date, default: Date.now },
  },
  { timestamps: true }
);

module.exports = mongoose.models.User || mongoose.model("User", userSchema);
