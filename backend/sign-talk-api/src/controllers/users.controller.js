const asyncHandler = require("express-async-handler");
const User = require("../models/User");
const env = require("../config/env");
const { recordAudit } = require("../utils/audit");

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

// PATCH /api/users/me (body validated by validators.updateMeRules)
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

// PATCH /api/users/:id/role { role } (admin only). :id may be the Mongo _id,
// the Firebase uid, or the user's email.
const updateRole = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const { role } = req.body;

  let lookup;
  if (/^[a-f0-9]{24}$/i.test(id)) lookup = { _id: id };
  else if (id.includes("@")) lookup = { email: id.toLowerCase() };
  else lookup = { firebaseUid: id };

  const before = await User.findOne(lookup);
  if (!before) {
    res.status(404);
    throw new Error(`No user found for "${id}" (use the user's _id, Firebase uid, or email; they must have called POST /api/users/sync once).`);
  }

  const isSelf =
    (req.user && req.user._id && String(req.user._id) === String(before._id)) ||
    (req.firebaseUser && req.firebaseUser.uid === before.firebaseUid);
  if (isSelf && role !== "admin") {
    res.status(400);
    throw new Error("You can't remove your own admin role -- ask another admin to do it.");
  }

  if (before.role === role) return res.json(before);

  const user = await User.findByIdAndUpdate(before._id, { role }, { new: true, runValidators: true });
  await recordAudit(req, { action: "user.role", entityType: "user", entityId: before.firebaseUid || before._id, before, after: user });
  return res.json(user);
});

module.exports = { syncUser, getMe, updateMe, updateRole };
