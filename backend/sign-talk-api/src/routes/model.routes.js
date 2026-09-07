const express = require("express");
const { requireAuth, requireAdmin } = require("../middleware/auth");
const ctrl = require("../controllers/model.controller");

const router = express.Router();

// Public: the app needs this at startup regardless of login state.
router.get("/version", ctrl.getActiveVersion);
router.get("/versions", ctrl.listVersions);

// Admin-only: registering a new training run.
router.post("/versions", requireAuth, requireAdmin, ctrl.createVersion);

module.exports = router;
