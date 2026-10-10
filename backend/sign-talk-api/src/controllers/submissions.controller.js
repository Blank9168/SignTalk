// Community sign submissions and their expert-review workflow.
//
// NOTE: Adding, reviewing, or publishing a submission never affects the
// recognition model. Submissions only create dictionary content (with
// hasTrainingData=false); the on-device TFLite model changes only when the
// team retrains from ai/dataset and ships a new app build.

const asyncHandler = require("express-async-handler");
const Submission = require("../models/Submission");
const DictionaryEntry = require("../models/DictionaryEntry");
const { SUBMISSION_FIELDS } = require("../middleware/validators");
const { OPEN_SUBMISSION_STATUSES, SUBMISSION_TRANSITIONS } = require("../config/constants");
const { recordAudit, actorUid } = require("../utils/audit");
const { slugify, escapeRegex } = require("../utils/text");

/**
 * Finds a non-archived dictionary entry or an open submission that the
 * proposed sign would duplicate. Returns null, or a description of the conflict.
 */
async function findDuplicate(name, slug) {
  const lowered = name.trim().toLowerCase();
  const entry = await DictionaryEntry.findOne({
    archived: { $ne: true },
    $or: [{ slug }, { label: new RegExp(`^${escapeRegex(name.trim())}$`, "i") }, { synonyms: lowered }],
  });
  if (entry) {
    let matchedOn = "synonym";
    if (entry.slug === slug) matchedOn = "slug";
    else if (typeof entry.label === "string" && entry.label.toLowerCase() === lowered) matchedOn = "label";
    return {
      type: "dictionary",
      id: entry._id ?? null,
      slug: entry.slug,
      label: entry.label,
      validationStatus: entry.validationStatus ?? null,
      matchedOn,
    };
  }

  const open = await Submission.findOne({ slug, status: { $in: OPEN_SUBMISSION_STATUSES } });
  if (open) {
    return { type: "submission", id: open._id ?? null, slug: open.slug, label: open.name, status: open.status, matchedOn: "slug" };
  }
  return null;
}

// POST /api/submissions -- public (optional auth). Body validated by
// validators.createSubmissionRules; stricter per-IP rate limit in app.js.
const createSubmission = asyncHandler(async (req, res) => {
  const data = {};
  for (const key of SUBMISSION_FIELDS) {
    if (req.body[key] !== undefined && req.body[key] !== null) data[key] = req.body[key];
  }
  data.slug = slugify(data.name);

  const conflict = await findDuplicate(data.name, data.slug);
  if (conflict) {
    const what =
      conflict.type === "dictionary"
        ? `the dictionary already has "${conflict.label}" (${conflict.slug})`
        : `a submission for "${conflict.label}" is already ${conflict.status.replace("_", " ")}`;
    res.status(409).json({ error: `Duplicate sign: ${what}.`, conflict });
    return;
  }

  const uid = actorUid(req);
  const submission = await Submission.create({
    ...data,
    submittedBy: uid,
    status: "submitted",
    statusHistory: [{ status: "submitted", by: uid, at: new Date(), note: "" }],
  });

  await recordAudit(req, {
    action: "submission.create",
    entityType: "submission",
    entityId: submission._id,
    after: submission,
  });
  res.status(201).json(submission);
});

// GET /api/submissions?status=submitted&limit=50&page=1 (validator/admin)
const listSubmissions = asyncHandler(async (req, res) => {
  const limit = req.query.limit || 50;
  const page = req.query.page || 1;
  const filter = {};
  if (req.query.status) filter.status = req.query.status;

  const [submissions, total] = await Promise.all([
    Submission.find(filter)
      .sort({ createdAt: -1 })
      .skip((page - 1) * limit)
      .limit(limit),
    Submission.countDocuments(filter),
  ]);
  res.json({ count: submissions.length, total, page, limit, submissions });
});

// GET /api/submissions/:id (validator/admin)
const getSubmission = asyncHandler(async (req, res) => {
  const submission = await Submission.findById(req.params.id);
  if (!submission) {
    res.status(404);
    throw new Error("Submission not found.");
  }
  res.json(submission);
});

/** Creates the dictionary entry for a verified submission. Returns { entry } or { conflict }. */
async function publishToDictionary(submission, uid) {
  const existing = await DictionaryEntry.findOne({ slug: submission.slug });
  if (existing) {
    return {
      conflict: {
        type: "dictionary",
        id: existing._id ?? null,
        slug: existing.slug,
        label: existing.label,
        archived: !!existing.archived,
        matchedOn: "slug",
      },
    };
  }

  const now = new Date();
  try {
    const entry = await DictionaryEntry.create({
      slug: submission.slug,
      label: submission.name,
      language: submission.language || "FSL",
      category: submission.category,
      description: submission.description || `${submission.meaningEn} (${submission.meaningFil})`,
      gloss: submission.gloss || "",
      meaningEn: submission.meaningEn,
      meaningFil: submission.meaningFil,
      synonyms: [],
      signType: submission.signType,
      hands: submission.hands,
      source: submission.source,
      // Dictionary-only: no bundled video and no training data, so the
      // recognition model is unaffected.
      hasVideo: false,
      hasTrainingData: false,
      validationStatus: "verified",
      validatedBy: uid,
      validatedAt: now,
      createdBy: uid,
      updatedBy: uid,
    });
    return { entry };
  } catch (err) {
    if (err && err.code === 11000) {
      return { conflict: { type: "dictionary", slug: submission.slug, matchedOn: "slug" } };
    }
    throw err;
  }
}

// POST /api/submissions/:id/review  { action, note } (validator/admin)
const reviewSubmission = asyncHandler(async (req, res) => {
  const { action } = req.body;
  const note = req.body.note || "";
  const transition = SUBMISSION_TRANSITIONS[action];

  const before = await Submission.findById(req.params.id);
  if (!before) {
    res.status(404);
    throw new Error("Submission not found.");
  }

  if (!transition.from.includes(before.status)) {
    const allowedActions = Object.entries(SUBMISSION_TRANSITIONS)
      .filter(([, t]) => t.from.includes(before.status))
      .map(([name]) => name);
    res.status(409).json({
      error: `Cannot "${action}" a submission whose status is "${before.status}".`,
      currentStatus: before.status,
      allowedActions,
    });
    return;
  }

  const uid = actorUid(req);
  const $set = { status: transition.to, reviewedBy: uid, reviewNote: note };

  let entry = null;
  if (action === "publish") {
    const result = await publishToDictionary(before, uid);
    if (result.conflict) {
      res.status(409).json({
        error: `Cannot publish: a dictionary entry with slug "${before.slug}" already exists.`,
        conflict: result.conflict,
      });
      return;
    }
    entry = result.entry;
    $set.publishedEntry = entry._id;
  }

  // Conditional on the status we validated against, so two reviewers acting
  // at once can't both apply a transition.
  const submission = await Submission.findOneAndUpdate(
    { _id: before._id, status: before.status },
    { $set, $push: { statusHistory: { status: transition.to, by: uid, at: new Date(), note } } },
    { new: true, runValidators: true }
  );
  if (!submission) {
    if (entry) {
      // Undo the entry this request just created so the dictionary and the
      // submission don't disagree.
      await DictionaryEntry.deleteOne({ _id: entry._id }).catch((err) =>
        // eslint-disable-next-line no-console
        console.error(`[submissions] Failed to roll back entry ${entry.slug}: ${err.message}`)
      );
    }
    res.status(409);
    throw new Error("Submission was changed by someone else while you were reviewing it -- reload and try again.");
  }

  if (entry) {
    await recordAudit(req, {
      action: "dictionary.create",
      entityType: "dictionary",
      entityId: entry.slug,
      after: entry,
    });
  }
  await recordAudit(req, {
    action: "submission.review",
    entityType: "submission",
    entityId: before._id,
    before,
    after: submission,
  });

  res.json(entry ? { submission, entry } : { submission });
});

module.exports = { createSubmission, listSubmissions, getSubmission, reviewSubmission };
