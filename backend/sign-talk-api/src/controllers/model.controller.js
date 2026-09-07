const asyncHandler = require("express-async-handler");
const ModelVersion = require("../models/ModelVersion");

// GET /api/model/version -- the currently-active trained model's metadata.
// The app can call this at startup to know the label order/count it should
// expect from on-device inference, and to show model info in "About".
const getActiveVersion = asyncHandler(async (req, res) => {
  const active = await ModelVersion.findOne({ isActive: true }).sort({ trainedAt: -1 });
  if (!active) {
    res.status(404);
    throw new Error("No active model version registered yet -- run npm run seed:model-version.");
  }
  res.json(active);
});

// GET /api/model/versions -- full history, newest first.
const listVersions = asyncHandler(async (req, res) => {
  const versions = await ModelVersion.find().sort({ trainedAt: -1 });
  res.json({ count: versions.length, versions });
});

// POST /api/model/versions (admin only) -- register a new training run
// without needing shell access to the API's machine (registerModelVersion.js
// is the CLI equivalent, used by the seed script / CI).
const createVersion = asyncHandler(async (req, res) => {
  const { version, labels, numClasses, architecture, dataSource, overallValAccuracy, macroPrecision, macroRecall, macroF1, epochsTrained, notes, makeActive } = req.body;

  if (!version || !Array.isArray(labels) || !numClasses) {
    res.status(400);
    throw new Error("version (string), labels (array), and numClasses (number) are required.");
  }

  if (makeActive) {
    await ModelVersion.updateMany({ isActive: true }, { isActive: false });
  }

  const created = await ModelVersion.create({
    version,
    labels,
    numClasses,
    architecture,
    dataSource,
    overallValAccuracy,
    macroPrecision,
    macroRecall,
    macroF1,
    epochsTrained,
    notes,
    isActive: !!makeActive,
  });

  res.status(201).json(created);
});

module.exports = { getActiveVersion, listVersions, createVersion };
