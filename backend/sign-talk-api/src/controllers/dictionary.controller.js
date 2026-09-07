const asyncHandler = require("express-async-handler");
const DictionaryEntry = require("../models/DictionaryEntry");

// GET /api/dictionary?language=FSL&category=Numbers&q=hello
const listEntries = asyncHandler(async (req, res) => {
  const { language, category, q } = req.query;
  const filter = {};
  if (language) filter.language = language;
  if (category) filter.category = category;
  if (q) filter.$text = { $search: q };

  const entries = await DictionaryEntry.find(filter).sort({ language: 1, category: 1, label: 1 });
  res.json({ count: entries.length, entries });
});

// GET /api/dictionary/:slug
const getEntry = asyncHandler(async (req, res) => {
  const entry = await DictionaryEntry.findOne({ slug: req.params.slug });
  if (!entry) {
    res.status(404);
    throw new Error(`No dictionary entry with slug "${req.params.slug}"`);
  }
  res.json(entry);
});

// POST /api/dictionary (admin only)
const createEntry = asyncHandler(async (req, res) => {
  const { slug, label, language, category, description, emoji, hasVideo, hasTrainingData } = req.body;
  const existing = await DictionaryEntry.findOne({ slug });
  if (existing) {
    res.status(409);
    throw new Error(`A dictionary entry with slug "${slug}" already exists.`);
  }
  const entry = await DictionaryEntry.create({
    slug,
    label,
    language,
    category,
    description,
    emoji,
    hasVideo: !!hasVideo,
    hasTrainingData: !!hasTrainingData,
  });
  res.status(201).json(entry);
});

// PATCH /api/dictionary/:slug (admin only)
const updateEntry = asyncHandler(async (req, res) => {
  const allowed = ["label", "language", "category", "description", "emoji", "hasVideo", "hasTrainingData"];
  const updates = {};
  for (const key of allowed) {
    if (key in req.body) updates[key] = req.body[key];
  }

  const entry = await DictionaryEntry.findOneAndUpdate({ slug: req.params.slug }, updates, {
    new: true,
    runValidators: true,
  });
  if (!entry) {
    res.status(404);
    throw new Error(`No dictionary entry with slug "${req.params.slug}"`);
  }
  res.json(entry);
});

// DELETE /api/dictionary/:slug (admin only)
const deleteEntry = asyncHandler(async (req, res) => {
  const entry = await DictionaryEntry.findOneAndDelete({ slug: req.params.slug });
  if (!entry) {
    res.status(404);
    throw new Error(`No dictionary entry with slug "${req.params.slug}"`);
  }
  res.status(204).send();
});

module.exports = { listEntries, getEntry, createEntry, updateEntry, deleteEntry };
