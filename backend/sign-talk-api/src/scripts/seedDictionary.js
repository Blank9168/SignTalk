// Populates (or refreshes) the DictionaryEntry collection from the real
// 50-word vocabulary in dictionaryData.js -- run with `npm run seed:dictionary`.
// Safe to re-run: upserts by slug rather than blindly inserting duplicates.
//
// Every field present on a dictionaryData.js entry is passed through as-is
// (gloss, meaningEn, meaningFil, synonyms, signType, hands, source,
// validationStatus, ...). Fields not in the schema are reported and ignored.
// One bad entry is logged and skipped; the rest still seed.

const { connectDB, disconnectDB } = require("../config/db");
const DictionaryEntry = require("../models/DictionaryEntry");
const dictionaryData = require("./dictionaryData");

const SEED_ACTOR = "seed-script";

function unknownKeys(entry) {
  return Object.keys(entry).filter((key) => !DictionaryEntry.schema.path(key) && !key.startsWith("_"));
}

async function seedEntry(entry) {
  if (!entry || typeof entry.slug !== "string" || entry.slug.trim() === "") {
    throw new Error("entry has no slug");
  }

  const ignored = unknownKeys(entry);
  if (ignored.length > 0) {
    console.warn(`[seedDictionary] ${entry.slug}: ignoring unknown field(s) ${ignored.join(", ")}`);
  }

  const $set = { ...entry, updatedBy: entry.updatedBy || SEED_ACTOR };
  const update = { $set };
  if (!("createdBy" in entry)) update.$setOnInsert = { createdBy: SEED_ACTOR };
  // A seeded entry marked verified without a stamp gets one on first insert.
  if (entry.validationStatus === "verified" && !("validatedBy" in entry) && !("validatedAt" in entry)) {
    update.$setOnInsert = { ...(update.$setOnInsert || {}), validatedBy: SEED_ACTOR, validatedAt: new Date() };
  }

  const result = await DictionaryEntry.findOneAndUpdate({ slug: entry.slug }, update, {
    upsert: true,
    new: true,
    runValidators: true,
    setDefaultsOnInsert: true,
    // Mongoose 8 replaced `rawResult` with this; needed to tell inserts from updates.
    includeResultMetadata: true,
  });
  return result?.lastErrorObject?.upserted ? "created" : "updated";
}

async function seed() {
  await connectDB();

  // Drops the pre-synonyms text index if present so the new one can be built
  // (MongoDB allows one text index per collection).
  try {
    await DictionaryEntry.syncIndexes();
  } catch (err) {
    console.error("[seedDictionary] Index sync failed (continuing):", err.message);
  }

  let created = 0;
  let updated = 0;
  const failures = [];

  for (const [i, entry] of dictionaryData.entries()) {
    try {
      const outcome = await seedEntry(entry);
      if (outcome === "created") created += 1;
      else updated += 1;
    } catch (err) {
      const id = entry?.slug || `#${i}`;
      failures.push({ id, message: err.message });
      console.error(`[seedDictionary] FAILED ${id}: ${err.message}`);
    }
  }

  console.log(
    `[seedDictionary] Done. ${created} created, ${updated} updated, ${failures.length} failed, ${dictionaryData.length} total.`
  );
  if (failures.length > 0) {
    console.log(`[seedDictionary] Failed entries: ${failures.map((f) => f.id).join(", ")}`);
    process.exitCode = 1;
  }
  await disconnectDB();
}

if (require.main === module) {
  seed().catch((err) => {
    console.error("[seedDictionary] Failed:", err);
    process.exit(1);
  });
}

module.exports = { seed, seedEntry, unknownKeys };
