const AuditLog = require("../models/AuditLog");

function toPlain(doc) {
  if (doc === undefined || doc === null) return null;
  if (typeof doc.toObject === "function") return doc.toObject({ depopulate: true });
  try {
    return JSON.parse(JSON.stringify(doc));
  } catch {
    return null;
  }
}

/**
 * Records one audit entry. Never throws: an audit write failing must not
 * fail (or roll back) the user-facing change, so errors are logged instead.
 */
async function recordAudit(req, { action, entityType, entityId = null, before = null, after = null }) {
  try {
    await AuditLog.create({
      actorUid: req.firebaseUser?.uid || req.user?.firebaseUid || null,
      actorRole: req.user?.role || "anonymous",
      action,
      entityType,
      entityId: entityId === null || entityId === undefined ? null : String(entityId),
      before: toPlain(before),
      after: toPlain(after),
      ip: req.ip || null,
      at: new Date(),
    });
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error(`[audit] Failed to record ${action} for ${entityType}/${entityId}: ${err.message}`);
  }
}

/** Firebase uid of the caller, or null when anonymous. */
function actorUid(req) {
  return req.firebaseUser?.uid || req.user?.firebaseUid || null;
}

module.exports = { recordAudit, actorUid, toPlain };
