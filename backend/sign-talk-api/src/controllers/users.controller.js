const asyncHandler = require("express-async-handler");
const User = require("../models/User");
const env = require("../config/env");

// POST /api/users/sync -- call right after a successful Firebase sign-in.
// Creates the MongoDB profile on first login, updates lastLoginAt/email/
// displayName on every subsequent call. Emails in ADMIN_EMAILS get
// role="admin" the first time they sync.
const syncUser = asyncHandler(async (req, res) => {
  const { uid, email } = req.firebaseUser;
  const displayName = req.body.displayName || req.firebaseUser.name || "";

  const isBootstrapAdmin = env.adminEmails.includes((email || "").toLowerCase());

  const user = await User.findOneAndUpdate(
    { firebaseUid: uid },
    {
      $set: { email, displayName, lastLoginAt: new Date() },
      $setOnInsert: { role: isBootstrapAdmin ? "admin" : "user" },
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  res.json(user);
});

// GET /api/users/me
const getMe = asyncHandler(async (req, res) => {
  if (!req.user) {
    res.status(404);
    throw new Error("No profile yet -- call POST /api/users/sync first.");
  }
  res.json(req.user);
});

// PATCH /api/users/me
const updateMe = asyncHandler(async (req, res) => {
  if (!req.user) {
    res.status(404);
    throw new Error("No profile yet -- call POST /api/users/sync first.");
  }
  const allowed = ["displayName"];
  const updates = {};
  for (const key of allowed) {
    if (key in req.body) updates[key] = req.body[key];
  }
  const user = await User.findByIdAndUpdate(req.user._id, updates, { new: true, runValidators: true });
  res.json(user);
});

module.exports = { syncUser, getMe, updateMe };
