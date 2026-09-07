const env = require("./config/env");
const { connectDB } = require("./config/db");
const createApp = require("./app");

async function main() {
  await connectDB();
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
