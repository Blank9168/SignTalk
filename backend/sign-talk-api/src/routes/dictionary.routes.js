const express = require("express");
const { requireAuth, requireAdmin, requireValidator } = require("../middleware/auth");
const validate = require("../middleware/validate");
const {
  createDictionaryEntryRules,
  updateDictionaryEntryRules,
  dictionaryActionRules,
} = require("../middleware/validators");
const ctrl = require("../controllers/dictionary.controller");

const router = express.Router();

// Public reads stay anonymous. Only `?includeArchived=true` needs a
// validator/admin token; everything else skips auth entirely.
function gateIncludeArchived(req, res, next) {
  if (req.query.includeArchived !== "true") return next();
  return requireAuth(req, res, (err) => (err ? next(err) : requireValidator(req, res, next)));
}

// Public: anyone can browse the dictionary (matches the app's own dictionary screen).
// Archived entries are excluded unless ?includeArchived=true (validator/admin).
router.get("/", gateIncludeArchived, ctrl.listEntries);
router.get("/:slug", gateIncludeArchived, ctrl.getEntry);

// Admin: create.
router.post("/", requireAuth, requireAdmin, createDictionaryEntryRules, validate, ctrl.createEntry);

// Validator/admin: edit and verify.
router.patch("/:slug", requireAuth, requireValidator, updateDictionaryEntryRules, validate, ctrl.updateEntry);
router.post("/:slug/verify", requireAuth, requireValidator, dictionaryActionRules, validate, ctrl.verifyEntry);

// Admin: archive / restore. DELETE is kept as an alias for archive so no
// client (including the Android app) can ever hard-delete an entry.
router.post("/:slug/archive", requireAuth, requireAdmin, dictionaryActionRules, validate, ctrl.archiveEntry);
router.post("/:slug/restore", requireAuth, requireAdmin, dictionaryActionRules, validate, ctrl.restoreEntry);
router.delete("/:slug", requireAuth, requireAdmin, dictionaryActionRules, validate, ctrl.archiveEntry);

module.exports = router;
