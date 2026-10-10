// Central place that loads and validates environment variables so nothing
// downstream has to guess whether `process.env.X` is set.

require("dotenv").config();

const required = (name, fallback = undefined) => {
  const value = process.env[name] ?? fallback;
  return value;
};

const intFromEnv = (name, fallback) => {
  const parsed = parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
};

// Express "trust proxy" setting. Render (and most PaaS hosts) put a reverse
// proxy in front of the app, so without this req.ip is the proxy's address
// and every client shares one rate-limit bucket. Defaults to 1 hop in
// production; override with TRUST_PROXY=<hops>|true|false|<express value>.
const parseTrustProxy = (raw, nodeEnv) => {
  if (raw === undefined || raw === "") return nodeEnv === "production" ? 1 : false;
  if (raw === "true") return true;
  if (raw === "false") return false;
  if (/^\d+$/.test(raw)) return parseInt(raw, 10);
  return raw; // e.g. "loopback" or a comma-separated list of subnets
};

const nodeEnv = process.env.NODE_ENV || "development";

const env = {
  nodeEnv,
  port: parseInt(process.env.PORT || "4000", 10),
  mongodbUri: required("MONGODB_URI", "mongodb://localhost:27017/signtalk"),

  firebaseProjectId: process.env.FIREBASE_PROJECT_ID || "",
  firebaseClientEmail: process.env.FIREBASE_CLIENT_EMAIL || "",
  // Service-account private keys are stored in .env with literal "\n"
  // sequences (real newlines break most .env parsers); un-escape them here.
  firebasePrivateKey: (process.env.FIREBASE_PRIVATE_KEY || "").replace(/\\n/g, "\n"),

  authDevBypass: process.env.AUTH_DEV_BYPASS === "true",
  adminEmails: (process.env.ADMIN_EMAILS || "")
    .split(",")
    .map((e) => e.trim().toLowerCase())
    .filter(Boolean),

  trustProxy: parseTrustProxy(process.env.TRUST_PROXY, nodeEnv),
  rateLimits: {
    globalMax: intFromEnv("RATE_LIMIT_GLOBAL_MAX", 300), // all requests, per IP per 15 min
    writeMax: intFromEnv("RATE_LIMIT_WRITE_MAX", 60), // non-GET requests, per IP per 15 min
    submissionMax: intFromEnv("RATE_LIMIT_SUBMISSION_MAX", 10), // POST /api/submissions, per IP per hour
  },

  aiModelsMetadataPath: process.env.AI_MODELS_METADATA_PATH || "../../ai/models/metadata.json",
};

if (env.authDevBypass && env.nodeEnv === "production") {
  // Fail loudly rather than silently accepting unauthenticated requests in prod.
  throw new Error(
    "AUTH_DEV_BYPASS=true is not allowed when NODE_ENV=production. " +
      "Remove it from your production .env before deploying."
  );
}

if (!env.authDevBypass && (!env.firebaseProjectId || !env.firebaseClientEmail || !env.firebasePrivateKey)) {
  // Not fatal at import time -- firebase.js logs a clearer warning when it
  // actually tries to initialize -- but we surface it early too.
  // eslint-disable-next-line no-console
  console.warn(
    "[env] Firebase Admin credentials are incomplete and AUTH_DEV_BYPASS is not enabled. " +
      "Requests requiring auth will fail until FIREBASE_PROJECT_ID/CLIENT_EMAIL/PRIVATE_KEY are set, " +
      "or AUTH_DEV_BYPASS=true is set for local development."
  );
}

module.exports = env;
