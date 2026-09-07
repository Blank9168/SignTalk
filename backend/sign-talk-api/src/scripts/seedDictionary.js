// Populates (or refreshes) the DictionaryEntry collection from the real
// 50-word vocabulary in dictionaryData.js -- run with `npm run seed:dictionary`.
// Safe to re-run: upserts by slug rather than blindly inserting duplicates.

const { connectDB, disconnectDB } = require("../config/db");
const DictionaryEntry = require("../models/DictionaryEntry");
const dictionaryData = require("./dictionaryData");

async function seed() {
  await connectDB();

  let created = 0;
  let updated = 0;

  for (const entry of dictionaryData) {
    const result = await DictionaryEntry.findOneAndUpdate(
      { slug: entry.slug },
      { $set: entry },
      { upsert: true, new: true, rawResult: true, setDefaultsOnInsert: true }
    );
    if (result.lastErrorObject?.upserted) created += 1;
    else updated += 1;
  }

  console.log(`[seedDictionary] Done. ${created} created, ${updated} updated, ${dictionaryData.length} total.`);
  await disconnectDB();
}

seed().catch((err) => {
  console.error("[seedDictionary] Failed:", err);
  process.exit(1);
});
