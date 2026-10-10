const asyncHandler = require("express-async-handler");
const { getFirebaseApp, admin } = require("../config/firebase");
const env = require("../config/env");
const User = require("../models/User");

/**
 * requireAuth: verifies the Firebase ID token in `Authorization: Bearer <token>`,
 * loads (or lazily creates) the matching MongoDB User document, and attaches
 * it to req.user. In AUTH_DEV_BYPASS mode (local dev only, refused in
 * production by config/env.js), it skips verification and attaches a fixed
 * dev user instead -- clearly logged so it's never mistaken for real auth.
 */
const requireAuth = asyncHandler(async (req, res, next) => {
  if (env.authDevBypass) {
    req.firebaseUser = { uid: "dev-user", email: "dev@example.com" };
    req.user = await User.findOneAndUpdate(
      { firebaseUid: "dev-user" },
      { $setOnInsert: { email: "dev@example.com", displayName: "Dev User", role: "admin" } },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );
    return next();
  }

  const header = req.headers.authorization || "";
  const [scheme, token] = header.split(" ");

  if (scheme !== "Bearer" || !token) {
    res.status(401);
    throw new Error("Missing or malformed Authorization header (expected: Bearer <Firebase ID token>)");
  }

  getFirebaseApp(); // throws a clear error if credentials are missing and bypass is off

  let decoded;
  try {
    decoded = await admin.auth().verifyIdToken(token);
  } catch (err) {
    res.status(401);
    throw new Error(`Invalid or expired Firebase ID token: ${err.message}`);
  }

  req.firebaseUser = decoded;
  req.user = await User.findOne({ firebaseUid: decoded.uid });
  // Not auto-creating the User here on purpose -- POST /api/users/sync is the
  // explicit "first login" step that creates the profile with admin-email logic.
  next();
});

/** Like requireAuth, but does not fail the request if no/invalid token is present. */
const optionalAuth = asyncHandler(async (req, res, next) => {
  const header = req.headers.authorization || "";
  const [scheme, token] = header.split(" ");

  if (env.authDevBypass) {
    req.firebaseUser = { uid: "dev-user", email: "dev@example.com" };
    req.user = await User.findOne({ firebaseUid: "dev-user" });
    return next();
  }

  if (scheme !== "Bearer" || !token) return next();

  try {
    getFirebaseApp();
    const decoded = await admin.auth().verifyIdToken(token);
    req.firebaseUser = decoded;
    req.user = await User.findOne({ firebaseUid: decoded.uid });
  } catch {
    // Invalid token on an optional-auth route just means "treat as anonymous".
  }
  next();
});

/**
 * Must follow requireAuth. 403s unless req.user.role is one of `roles`.
 *   requireRole("admin")
 *   requireRole("validator", "admin")
 */
const requireRole = (...roles) => {
  const allowed = roles.flat();
  const message =
    allowed.length === 1 && allowed[0] === "admin"
      ? "This action requires an admin account."
      : `This action requires one of these roles: ${allowed.join(", ")}.`;
  return (req, res, next) => {
    if (!req.user || !allowed.includes(req.user.role)) {
      res.status(403);
      return next(new Error(message));
    }
    return next();
  };
};

/** Must follow requireAuth. 403s unless req.user.role === "admin". */
const requireAdmin = requireRole("admin");
/** Must follow requireAuth. Validators and admins. */
const requireValidator = requireRole("validator", "admin");

module.exports = { requireAuth, optionalAuth, requireRole, requireAdmin, requireValidator };
