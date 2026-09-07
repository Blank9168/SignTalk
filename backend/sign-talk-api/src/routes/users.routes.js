const express = require("express");
const { requireAuth } = require("../middleware/auth");
const ctrl = require("../controllers/users.controller");

const router = express.Router();

router.post("/sync", requireAuth, ctrl.syncUser);
router.get("/me", requireAuth, ctrl.getMe);
router.patch("/me", requireAuth, ctrl.updateMe);

module.exports = router;
