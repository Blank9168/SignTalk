const { body, validationResult } = require("express-validator");

// Runs after an express-validator chain; turns validation failures into a
// clean 400 instead of letting a bad payload reach the controller/DB.
function validate(req, res, next) {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    res.status(400).json({ error: "Validation failed", details: errors.array() });
    return;
  }
  next();
}

/**
 * Whole-body check for whitelisted payloads: the body must be a JSON object,
 * may only contain `allowed` keys, and (with requireNonEmpty) must contain
 * at least one of them. Use at the start of a chain, before `validate`.
 */
function rejectUnknownFields(allowed, { requireNonEmpty = false } = {}) {
  return body().custom((value) => {
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
      throw new Error("Request body must be a JSON object.");
    }
    const keys = Object.keys(value);
    if (requireNonEmpty && keys.length === 0) {
      throw new Error(`Request body is empty -- send at least one of: ${allowed.join(", ")}.`);
    }
    const unknown = keys.filter((k) => !allowed.includes(k));
    if (unknown.length > 0) {
      throw new Error(`Unknown or non-editable field(s): ${unknown.join(", ")}. Allowed: ${allowed.join(", ")}.`);
    }
    return true;
  });
}

module.exports = validate;
module.exports.validate = validate;
module.exports.rejectUnknownFields = rejectUnknownFields;
