const express = require("express");
const { requireAuth, optionalAuth, requireValidator } = require("../middleware/auth");
const validate = require("../middleware/validate");
const {
  createSubmissionRules,
  submissionIdRule,
  listSubmissionsRules,
  reviewSubmissionRules,
} = require("../middleware/validators");
const ctrl = require("../controllers/submissions.controller");

const router = express.Router();

// Public: the Android app has no login yet, so a token is optional (its uid
// is recorded when present). The per-IP submission rate limit is applied in
// app.js so each app instance gets its own counter.
router.post("/", optionalAuth, createSubmissionRules, validate, ctrl.createSubmission);

// Validator/admin: review queue.
router.get("/", requireAuth, requireValidator, listSubmissionsRules, validate, ctrl.listSubmissions);
router.get("/:id", requireAuth, requireValidator, submissionIdRule, validate, ctrl.getSubmission);
router.post("/:id/review", requireAuth, requireValidator, reviewSubmissionRules, validate, ctrl.reviewSubmission);

module.exports = router;
