const asyncHandler = require("express-async-handler");
const DictionaryEntry = require("../models/DictionaryEntry");
const { DICTIONARY_EDITABLE_FIELDS } = require("../middleware/validators");
const { recordAudit, actorUid } = require("../utils/audit");
const { escapeRegex } = require("../utils/text");

const NOT_ARCHIVED = { $ne: true }; // also matches legacy docs that predate the `archived` field
const SORT = { language: 1, category: 1, label: 1 };

// Only accept plain string query values -- `?language[$ne]=x` would otherwise
// arrive as an object and be passed straight into the Mongo filter.
const str = (v) => (typeof v === "string" && v.trim() !== "" ? v.trim() : undefined);

// includeArchived=true is gated to validators/admins by
// dictionary.routes.js#gateIncludeArchived before this runs.
const wantsArchived = (req) => req.query.includeArchived === "true";

function notFound(res, slug) {
  res.status(404);
  return new Error(`No dictionary entry with slug "${slug}"`);
}

// GET /api/dictionary?language=FSL&category=Numbers&q=hello[&includeArchived=true][&validationStatus=verified]
const listEntries = asyncHandler(async (req, res) => {
  const language = str(req.query.language);
  const category = str(req.query.category);
  const validationStatus = str(req.query.validationStatus);
  const q = str(req.query.q)?.slice(0, 100);

  const filter = {};
  if (language) filter.language = language;
  if (category) filter.category = category;
  if (validationStatus) filter.validationStatus = validationStatus;
  if (!wantsArchived(req)) filter.archived = NOT_ARCHIVED;

  let entries;
  if (q) {
    // Whole-word search via the text index (label, gloss, synonyms, meanings,
    // description); if that finds nothing, fall back to a case-insensitive
    // substring match so partial input like "maganda" still finds entries.
    entries = await DictionaryEntry.find({ ...filter, $text: { $search: q } }).sort(SORT);
    if (!entries || entries.length === 0) {
      const rx = new RegExp(escapeRegex(q), "i");
      entries = await DictionaryEntry.find({
        ...filter,
        $or: [{ slug: rx }, { label: rx }, { gloss: rx }, { synonyms: rx }, { meaningEn: rx }, { meaningFil: rx }],
      }).sort(SORT);
    }
  } else {
    entries = await DictionaryEntry.find(filter).sort(SORT);
  }

  res.json({ count: entries.length, entries });
});

// GET /api/dictionary/:slug
const getEntry = asyncHandler(async (req, res) => {
  const filter = { slug: req.params.slug };
  if (!wantsArchived(req)) filter.archived = NOT_ARCHIVED;
  const entry = await DictionaryEntry.findOne(filter);
  if (!entry) throw notFound(res, req.params.slug);
  res.json(entry);
});

// POST /api/dictionary (admin only)
const createEntry = asyncHandler(async (req, res) => {
  const { slug } = req.body;
  const existing = await DictionaryEntry.findOne({ slug });
  if (existing) {
    res.status(409);
    throw new Error(
      existing.archived
        ? `A dictionary entry with slug "${slug}" already exists but is archived -- restore it with POST /api/dictionary/${slug}/restore.`
        : `A dictionary entry with slug "${slug}" already exists.`
    );
  }

  const uid = actorUid(req);
  const data = { slug };
  for (const key of DICTIONARY_EDITABLE_FIELDS) {
    if (key in req.body) data[key] = req.body[key];
  }
  data.hasVideo = !!data.hasVideo;
  data.hasTrainingData = !!data.hasTrainingData;
  if (data.validationStatus === "verified") {
    data.validatedBy = uid;
    data.validatedAt = new Date();
  }
  data.createdBy = uid;
  data.updatedBy = uid;

  const entry = await DictionaryEntry.create(data);
  await recordAudit(req, { action: "dictionary.create", entityType: "dictionary", entityId: slug, after: entry });
  res.status(201).json(entry);
});

// PATCH /api/dictionary/:slug (validator/admin). Body is whitelisted +
// type-checked by validators.updateDictionaryEntryRules before this runs.
const updateEntry = asyncHandler(async (req, res) => {
  const { slug } = req.params;
  const before = await DictionaryEntry.findOne({ slug });
  if (!before) throw notFound(res, slug);

  const $set = { updatedBy: actorUid(req) };
  for (const key of DICTIONARY_EDITABLE_FIELDS) {
    if (key in req.body) $set[key] = req.body[key];
  }
  // Moving an entry back to unverified/for_review clears the old verification stamp.
  if ($set.validationStatus) {
    $set.validatedBy = null;
    $set.validatedAt = null;
  }

  const entry = await DictionaryEntry.findOneAndUpdate({ slug }, { $set }, { new: true, runValidators: true });
  if (!entry) throw notFound(res, slug);

  await recordAudit(req, { action: "dictionary.update", entityType: "dictionary", entityId: slug, before, after: entry });
  res.json(entry);
});

// POST /api/dictionary/:slug/verify (validator/admin)
const verifyEntry = asyncHandler(async (req, res) => {
  const { slug } = req.params;
  const before = await DictionaryEntry.findOne({ slug });
  if (!before) throw notFound(res, slug);
  if (before.archived) {
    res.status(409);
    throw new Error(`Entry "${slug}" is archived -- restore it before verifying.`);
  }

  const uid = actorUid(req);
  const entry = await DictionaryEntry.findOneAndUpdate(
    { slug },
    { $set: { validationStatus: "verified", validatedBy: uid, validatedAt: new Date(), updatedBy: uid } },
    { new: true, runValidators: true }
  );
  await recordAudit(req, { action: "dictionary.verify", entityType: "dictionary", entityId: slug, before, after: entry });
  res.json(entry);
});

async function setArchived(req, res, archived) {
  const { slug } = req.params;
  const before = await DictionaryEntry.findOne({ slug });
  if (!before) throw notFound(res, slug);
  if (!!before.archived === archived) {
    // Already in the requested state -- idempotent, nothing to write or audit.
    return res.json(before);
  }

  const uid = actorUid(req);
  const $set = archived
    ? { archived: true, archivedAt: new Date(), archivedBy: uid, updatedBy: uid }
    : { archived: false, archivedAt: null, archivedBy: null, updatedBy: uid };
  const entry = await DictionaryEntry.findOneAndUpdate({ slug }, { $set }, { new: true });

  await recordAudit(req, {
    action: archived ? "dictionary.archive" : "dictionary.restore",
    entityType: "dictionary",
    entityId: slug,
    before,
    after: entry,
  });
  return res.json(entry);
}

// POST /api/dictionary/:slug/archive and DELETE /api/dictionary/:slug (admin only).
// Entries are never hard-deleted: archiving hides them from public reads but
// keeps the row (and its history) so it can be restored.
const archiveEntry = asyncHandler(async (req, res) => setArchived(req, res, true));

// POST /api/dictionary/:slug/restore (admin only)
const restoreEntry = asyncHandler(async (req, res) => setArchived(req, res, false));

module.exports = { listEntries, getEntry, createEntry, updateEntry, verifyEntry, archiveEntry, restoreEntry };
