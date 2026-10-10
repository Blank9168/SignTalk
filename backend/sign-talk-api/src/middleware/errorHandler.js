const env = require("../config/env");

function notFound(req, res) {
  res.status(404).json({ error: `Route not found: ${req.method} ${req.originalUrl}` });
}

// Express recognizes error middleware by arity (4 args) -- keep all 4 even
// though `next` is unused, or Express will treat this as a normal handler.
// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, next) {
  // Controllers set res.status() before throwing; errors from Express
  // middleware (e.g. malformed JSON from express.json) carry err.status.
  let status = res.statusCode && res.statusCode !== 200 ? res.statusCode : err.status || err.statusCode || 500;
  if (status < 400) status = 500;
  res.status(status).json({
    error: err.message || "Internal server error",
    ...(env.nodeEnv !== "production" ? { stack: err.stack } : {}),
  });
}

module.exports = { notFound, errorHandler };
