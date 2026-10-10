const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");

const env = require("./config/env");
const routes = require("./routes");
const { notFound, errorHandler } = require("./middleware/errorHandler");
const { createGlobalLimiter, createWriteLimiter, createSubmissionLimiter } = require("./middleware/rateLimits");

function createApp() {
  const app = express();

  // Behind Render's proxy: lets req.ip (used by rate limits + audit log) be
  // the real client IP instead of the proxy's. See TRUST_PROXY in config/env.js.
  app.set("trust proxy", env.trustProxy);

  app.use(helmet());
  app.use(cors()); // the Android app calls this from outside a browser, so a permissive CORS policy is fine here
  app.use(express.json({ limit: "1mb" }));
  if (env.nodeEnv !== "test") {
    app.use(morgan(env.nodeEnv === "production" ? "combined" : "dev"));
  }

  // Rate limits (per client IP):
  //   global      -- every request, 300 / 15 min
  //   writes      -- every non-GET request, 60 / 15 min
  //   submissions -- POST /api/submissions, 10 / hour
  app.use(createGlobalLimiter());
  app.use(createWriteLimiter());
  app.post("/api/submissions", createSubmissionLimiter());

  app.use("/api", routes);

  app.use(notFound);
  app.use(errorHandler);

  return app;
}

module.exports = createApp;
