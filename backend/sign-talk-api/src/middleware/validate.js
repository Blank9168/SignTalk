const { validationResult } = require("express-validator");

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

module.exports = validate;
