const admin = require("firebase-admin");
const env = require("./env");

let app = null;

/**
 * Lazily initializes the Firebase Admin SDK. Returns null (rather than
 * throwing) when credentials are missing and dev-bypass is on, so the
 * server can still boot for local testing without a real Firebase project.
 */
function getFirebaseApp() {
  if (app) return app;

  if (!env.firebaseProjectId || !env.firebaseClientEmail || !env.firebasePrivateKey) {
    if (env.authDevBypass) {
      // eslint-disable-next-line no-console
      console.warn("[firebase] No credentials configured -- running with AUTH_DEV_BYPASS, Firebase Admin not initialized.");
      return null;
    }
    throw new Error(
      "Firebase Admin credentials are missing (FIREBASE_PROJECT_ID / FIREBASE_CLIENT_EMAIL / FIREBASE_PRIVATE_KEY). " +
        "Set them in .env, or set AUTH_DEV_BYPASS=true for local development without Firebase."
    );
  }

  app = admin.initializeApp({
    credential: admin.credential.cert({
      projectId: env.firebaseProjectId,
      clientEmail: env.firebaseClientEmail,
      privateKey: env.firebasePrivateKey,
    }),
  });

  // eslint-disable-next-line no-console
  console.log("[firebase] Admin SDK initialized for project", env.firebaseProjectId);
  return app;
}

module.exports = { getFirebaseApp, admin };
