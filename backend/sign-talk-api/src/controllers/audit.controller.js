const asyncHandler = require("express-async-handler");
const AuditLog = require("../models/AuditLog");

// GET /api/audit?entityType=dictionary&entityId=isa&actor=<uid>&action=dictionary.update&limit=50&page=1 (admin only)
const listAudit = asyncHandler(async (req, res) => {
  const limit = req.query.limit || 50;
  const page = req.query.page || 1;
  const filter = {};
  if (req.query.entityType) filter.entityType = req.query.entityType;
  if (req.query.entityId) filter.entityId = req.query.entityId;
  if (req.query.actor) filter.actorUid = req.query.actor;
  if (req.query.action) filter.action = req.query.action;

  const [logs, total] = await Promise.all([
    AuditLog.find(filter)
      .sort({ at: -1 })
      .skip((page - 1) * limit)
      .limit(limit),
    AuditLog.countDocuments(filter),
  ]);
  res.json({ count: logs.length, total, page, limit, logs });
});

module.exports = { listAudit };
