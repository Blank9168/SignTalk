const rateLimit = require("express-rate-limit");
const env = require("../config/env");

// Factories (not singletons) so every createApp() gets fresh in-memory
// counters -- keeps tests isolated. Counters are per process and reset on
// restart/redeploy, which is fine for a single Render instance.

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

// Anonymous best-effort telemetry the Android app fires once per recognized
// sign. It is excluded from the write limiter so a busy demo (many phones
// behind one Wi-Fi NAT) can't use up the write budget that real writes such
// as submissions need; it still counts toward the global limit.
const WRITE_LIMIT_EXEMPT = [/^\/api\/logs\/recognition(\/|$)/];

const common = { standardHeaders: true, legacyHeaders: false };

// Generic rate limit -- protects the free-tier Mongo/hosting from accidental
// abuse; not a substitute for real auth.
function createGlobalLimiter() {
  return rateLimit({
    ...common,
    windowMs: 15 * 60 * 1000,
    limit: env.rateLimits.globalMax,
    message: { error: "Too many requests -- please slow down and try again in a few minutes." },
  });
}

// Stricter limit on every non-GET request.
function createWriteLimiter() {
  return rateLimit({
    ...common,
    windowMs: 15 * 60 * 1000,
    limit: env.rateLimits.writeMax,
    skip: (req) => SAFE_METHODS.has(req.method) || WRITE_LIMIT_EXEMPT.some((rx) => rx.test(req.path)),
    message: { error: "Too many write requests -- please wait a few minutes and try again." },
  });
}

// Strictest: public sign submissions.
function createSubmissionLimiter() {
  return rateLimit({
    ...common,
    windowMs: 60 * 60 * 1000,
    limit: env.rateLimits.submissionMax,
    message: { error: "Too many sign submissions from this network -- please try again in an hour." },
  });
}

module.exports = { createGlobalLimiter, createWriteLimiter, createSubmissionLimiter };
