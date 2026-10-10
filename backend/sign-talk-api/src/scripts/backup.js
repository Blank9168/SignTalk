// Full MongoDB backup with mongodump -- run with `npm run backup`.
//
//   mongodump --uri "$MONGODB_URI" --gzip --archive=backups/signtalk-<timestamp>.gz
//
// Requires the MongoDB Database Tools (mongodump/mongorestore) on PATH:
// https://www.mongodb.com/docs/database-tools/installation/
// Reads MONGODB_URI from .env (or the environment). The URI is never printed.

const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");

const BACKUP_DIR = path.resolve(__dirname, "../../backups");

function timestamp(date = new Date()) {
  // 2026-10-10T07:48:52.585Z -> 2026-10-10T07-48-52Z (filesystem-safe)
  return date.toISOString().replace(/\.\d{3}Z$/, "Z").replace(/:/g, "-");
}

function buildDumpArgs(uri, archivePath) {
  return ["--uri", uri, "--gzip", `--archive=${archivePath}`];
}

function run(cmd, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(cmd, args, { stdio: "inherit" });
    child.on("error", (err) => {
      if (err.code === "ENOENT") {
        reject(new Error(`${cmd} not found -- install the MongoDB Database Tools and make sure ${cmd} is on PATH.`));
      } else reject(err);
    });
    child.on("exit", (code) => (code === 0 ? resolve() : reject(new Error(`${cmd} exited with code ${code}`))));
  });
}

async function main() {
  require("dotenv").config({ path: path.resolve(__dirname, "../../.env") });
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set (put it in .env or the environment).");

  fs.mkdirSync(BACKUP_DIR, { recursive: true });
  const archivePath = path.join(BACKUP_DIR, `signtalk-${timestamp()}.gz`);

  console.log(`[backup] Dumping database to ${path.relative(process.cwd(), archivePath)} ...`);
  await run("mongodump", buildDumpArgs(uri, archivePath));
  const sizeKb = (fs.statSync(archivePath).size / 1024).toFixed(1);
  console.log(`[backup] Done: ${archivePath} (${sizeKb} KB)`);
}

if (require.main === module) {
  main().catch((err) => {
    console.error("[backup] Failed:", err.message);
    process.exit(1);
  });
}

module.exports = { timestamp, buildDumpArgs, run, BACKUP_DIR };
