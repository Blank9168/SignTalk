const express = require("express");
const { body } = require("express-validator");
const { requireAuth, requireAdmin } = require("../middleware/auth");
const validate = require("../middleware/validate");
const ctrl = require("../controllers/dictionary.controller");

const router = express.Router();

const entryValidation = [
  body("slug").isString().trim().notEmpty(),
  body("label").isString().trim().notEmpty(),
  body("language").isIn(["FSL", "ASL"]),
  body("category").isString().trim().notEmpty(),
  body("description").isString().trim().notEmpty(),
];

// Public: anyone can browse the dictionary (matches the app's own dictionary screen).
router.get("/", ctrl.listEntries);
router.get("/:slug", ctrl.getEntry);

// Admin-only writes.
router.post("/", requireAuth, requireAdmin, entryValidation, validate, ctrl.createEntry);
router.patch("/:slug", requireAuth, requireAdmin, ctrl.updateEntry);
router.delete("/:slug", requireAuth, requireAdmin, ctrl.deleteEntry);

module.exports = router;
