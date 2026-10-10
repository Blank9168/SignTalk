const { mongoose } = require("../config/db");

// Append-only record of who changed what. Written by utils/audit.js from
// every admin/validator write route (plus public submission creation).
// `before`/`after` are plain snapshots of the affected document.
const auditLogSchema = new mongoose.Schema(
  {
    actorUid: { type: String, default: null },
    actorRole: { type: String, default: "anonymous" },
    // e.g. dictionary.create, dictionary.update, dictionary.archive,
    // dictionary.restore, dictionary.verify, submission.create,
    // submission.review, user.role, model.version.create
    action: { type: String, required: true },
    entityType: { type: String, required: true },
    entityId: { type: String, default: null },
    before: { type: mongoose.Schema.Types.Mixed, default: null },
    after: { type: mongoose.Schema.Types.Mixed, default: null },
    ip: { type: String, default: null },
    at: { type: Date, default: Date.now },
  },
  { timestamps: false, minimize: false }
);

auditLogSchema.index({ at: -1 });
auditLogSchema.index({ entityType: 1, entityId: 1, at: -1 });
auditLogSchema.index({ actorUid: 1, at: -1 });

module.exports = mongoose.models.AuditLog || mongoose.model("AuditLog", auditLogSchema);
