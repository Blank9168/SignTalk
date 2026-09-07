const express = require("express");
const { requireAuth, optionalAuth, requireAdmin } = require("../middleware/auth");
const ctrl = require("../controllers/logs.controller");

const router = express.Router();

// Logging a recognition works whether or not the user is logged in
// (optionalAuth attaches req.user when a valid token is present).
router.post("/recognition", optionalAuth, ctrl.createLog);
router.patch("/recognition/:id", optionalAuth, ctrl.markLogFeedback);

// Browsing logs/stats is an admin/analytics action.
router.get("/recognition", requireAuth, requireAdmin, ctrl.listLogs);
router.get("/stats", requireAuth, requireAdmin, ctrl.getStats);

module.exports = router;
