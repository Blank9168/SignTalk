const express = require("express");
const { requireAuth, requireAdmin } = require("../middleware/auth");
const validate = require("../middleware/validate");
const { updateUserRoleRules, updateMeRules } = require("../middleware/validators");
const ctrl = require("../controllers/users.controller");

const router = express.Router();

router.post("/sync", requireAuth, ctrl.syncUser);
router.get("/me", requireAuth, ctrl.getMe);
router.patch("/me", requireAuth, updateMeRules, validate, ctrl.updateMe);

// Admin only: promote/demote a user (user | validator | admin).
router.patch("/:id/role", requireAuth, requireAdmin, updateUserRoleRules, validate, ctrl.updateRole);

module.exports = router;
