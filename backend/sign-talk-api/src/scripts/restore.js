// Restores a backup made by `npm run backup` -- run with:
//
//   npm run restore -- backups/signtalk-<timestamp>.gz --yes          # merge: insert missing docs, keep existing
//   npm run restore -- backups/signtalk-<timestamp>.gz --drop --yes   # replace: drop each collection first
//
// Equivalent to: mongorestore --uri "$MONGODB_URI" --gzip --archive=<file> [--drop]
// Without --yes it only prints what it would do. Restore into a scratch
// database first if you're unsure (point MONGODB_URI at it for that run).

const fs = require("fs");
const path = require("path");
const { run } = require("./backup");

function buildRestoreArgs(uri, archivePath, { drop = false } = {}) {
  const args = ["--uri", uri, "--gzip", `--archive=${archivePath}`];
  if (drop) args.push("--drop");
  return args;
}

async function main(argv = process.argv.slice(2)) {
  require("dotenv").config({ path: path.resolve(__dirname, "../../.env") });

  const file = argv.find((a) => !a.startsWith("--"));
  const drop = argv.includes("--drop");
  const confirmed = argv.includes("--yes");

  if (!file) throw new Error("Usage: npm run restore -- <backups/signtalk-<timestamp>.gz> [--drop] --yes");
  const archivePath = path.resolve(process.cwd(), file);
  if (!fs.existsSync(archivePath)) throw new Error(`Backup file not found: ${archivePath}`);

  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set (put it in .env or the environment).");

  const mode = drop ? "REPLACE (drops each collection in the archive first)" : "MERGE (existing documents are kept)";
  console.log(`[restore] Archive: ${archivePath}`);
  console.log(`[restore] Mode:    ${mode}`);
  if (!confirmed) {
    console.log("[restore] Dry run -- re-run with --yes to actually restore.");
    return;
  }

  await run("mongorestore", buildRestoreArgs(uri, archivePath, { drop }));
  console.log("[restore] Done.");
}

if (require.main === module) {
  main().catch((err) => {
    console.error("[restore] Failed:", err.message);
    process.exit(1);
  });
}

module.exports = { buildRestoreArgs, main };
