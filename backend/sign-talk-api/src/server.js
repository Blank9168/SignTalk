const env = require("./config/env");
const { connectDB } = require("./config/db");
const createApp = require("./app");
const DictionaryEntry = require("./models/DictionaryEntry");

// MongoDB allows only one text index per collection, so when the dictionary
// text index changes (e.g. synonyms/meanings were added to it) the old one
// has to be dropped before the new one can be built. syncIndexes() does
// that. Best-effort: a failure is logged and the server still starts.
async function syncDictionaryIndexes() {
  try {
    const dropped = await DictionaryEntry.syncIndexes();
    if (dropped && dropped.length > 0) {
      // eslint-disable-next-line no-console
      console.log(`[server] Dictionary indexes synced (dropped stale: ${dropped.join(", ")})`);
    }
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error("[server] Could not sync dictionary indexes (search may use the old index):", err.message);
  }
}

async function main() {
  await connectDB();
  await syncDictionaryIndexes();
  const app = createApp();
  app.listen(env.port, () => {
    // eslint-disable-next-line no-console
    console.log(`[server] SignTalk API listening on port ${env.port} (${env.nodeEnv})`);
  });
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error("[server] Fatal startup error:", err);
  process.exit(1);
});
