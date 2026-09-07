const asyncHandler = require("express-async-handler");
const RecognitionLog = require("../models/RecognitionLog");

// POST /api/logs/recognition -- the app calls this after each on-device
// prediction it wants recorded (optional/best-effort from the app's side;
// this endpoint never blocks recognition itself).
const createLog = asyncHandler(async (req, res) => {
  const { predictedSlug, confidence, modelVersion, deviceInfo } = req.body;

  if (!predictedSlug || typeof confidence !== "number") {
    res.status(400);
    throw new Error("predictedSlug (string) and confidence (number 0-1) are required.");
  }

  const log = await RecognitionLog.create({
    userId: req.user ? req.user._id : null,
    predictedSlug,
    confidence,
    modelVersion: modelVersion || null,
    deviceInfo: deviceInfo || "",
  });
  res.status(201).json(log);
});

// PATCH /api/logs/recognition/:id -- let the app report whether a prediction
// was actually correct (e.g. user tapped "that's wrong"), for later accuracy analysis.
const markLogFeedback = asyncHandler(async (req, res) => {
  const { wasCorrect } = req.body;
  if (typeof wasCorrect !== "boolean") {
    res.status(400);
    throw new Error("wasCorrect (boolean) is required.");
  }
  const log = await RecognitionLog.findByIdAndUpdate(req.params.id, { wasCorrect }, { new: true });
  if (!log) {
    res.status(404);
    throw new Error("Recognition log not found.");
  }
  res.json(log);
});

// GET /api/logs/recognition?limit=50&before=<ISO date> -- admin-facing, paginated.
const listLogs = asyncHandler(async (req, res) => {
  const limit = Math.min(parseInt(req.query.limit, 10) || 50, 200);
  const filter = {};
  if (req.query.before) filter.createdAt = { $lt: new Date(req.query.before) };
  if (req.query.slug) filter.predictedSlug = req.query.slug;

  const logs = await RecognitionLog.find(filter).sort({ createdAt: -1 }).limit(limit);
  res.json({ count: logs.length, logs });
});

// GET /api/logs/stats -- aggregate usage stats: most-recognized signs,
// overall volume, and self-reported accuracy where feedback exists.
const getStats = asyncHandler(async (req, res) => {
  const [totalCount, byLabel, feedback] = await Promise.all([
    RecognitionLog.countDocuments(),
    RecognitionLog.aggregate([
      { $group: { _id: "$predictedSlug", count: { $sum: 1 }, avgConfidence: { $avg: "$confidence" } } },
      { $sort: { count: -1 } },
      { $limit: 20 },
    ]),
    RecognitionLog.aggregate([
      { $match: { wasCorrect: { $ne: null } } },
      { $group: { _id: "$wasCorrect", count: { $sum: 1 } } },
    ]),
  ]);

  const correct = feedback.find((f) => f._id === true)?.count || 0;
  const incorrect = feedback.find((f) => f._id === false)?.count || 0;
  const feedbackTotal = correct + incorrect;

  res.json({
    totalRecognitions: totalCount,
    topPredictedSigns: byLabel.map((row) => ({
      slug: row._id,
      count: row.count,
      avgConfidence: row.avgConfidence,
    })),
    userReportedAccuracy: feedbackTotal > 0 ? correct / feedbackTotal : null,
    feedbackSampleSize: feedbackTotal,
  });
});

module.exports = { createLog, markLogFeedback, listLogs, getStats };
