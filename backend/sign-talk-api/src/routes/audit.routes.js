const express = require("express");
const { requireAuth, requireAdmin } = require("../middleware/auth");
const validate = require("../middleware/validate");
const { listAuditRules } = require("../middleware/validators");
const ctrl = require("../controllers/audit.controller");

const router = express.Router();

router.get("/", requireAuth, requireAdmin, listAuditRules, validate, ctrl.listAudit);

module.exports = router;
