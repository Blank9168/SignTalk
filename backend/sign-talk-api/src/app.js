const express = require("express");
const cors = require("cors");
const helmet = require("helmet");
const morgan = require("morgan");
const rateLimit = require("express-rate-limit");

const env = require("./config/env");
const routes = require("./routes");
const { notFound, errorHandler } = require("./middleware/errorHandler");

function createApp() {
  const app = express();

  app.use(helmet());
  app.use(cors()); // the Android app calls this from outside a browser, so a permissive CORS policy is fine here
  app.use(express.json({ limit: "1mb" }));
  if (env.nodeEnv !== "test") {
    app.use(morgan(env.nodeEnv === "production" ? "combined" : "dev"));
  }

  // Generic rate limit -- protects the free-tier Mongo/hosting this is likely
  // to run on from accidental abuse; not a substitute for real auth.
  app.use(
    rateLimit({
      windowMs: 15 * 60 * 1000,
      limit: 300,
      standardHeaders: true,
      legacyHeaders: false,
    })
  );

  app.use("/api", routes);

  app.use(notFound);
  app.use(errorHandler);

  return app;
}

module.exports = createApp;
