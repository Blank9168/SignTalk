// Reads ai/models/metadata.json (written by ai/training/train.py after each
// run) and registers it as a ModelVersion, marking it the active one.
// Run with `npm run seed:model-version` after every retraining.

const fs = require("fs");
const path = require("path");
const { connectDB, disconnectDB } = require("../config/db");
const ModelVersion = require("../models/ModelVersion");
const env = require("../config/env");

async function main() {
  const metadataPath = path.resolve(__dirname, "../../", env.aiModelsMetadataPath);

  if (!fs.existsSync(metadataPath)) {
    console.error(
      `[registerModelVersion] metadata.json not found at ${metadataPath}.\n` +
        "Set AI_MODELS_METADATA_PATH in .env to point at ai/models/metadata.json, " +
        "or run ai/training/train.py first."
    );
    process.exit(1);
  }

  const metadata = JSON.parse(fs.readFileSync(metadataPath, "utf-8"));
  const trainedAt = fs.statSync(metadataPath).mtime;
  // A version string unique per training run: timestamp + accuracy, so two
  // runs on the same day with different results don't collide.
  const version = `${trainedAt.toISOString()}_acc${(metadata.overall_val_accuracy ?? 0).toFixed(4)}`;

  await connectDB();

  const existing = await ModelVersion.findOne({ version });
  if (existing) {
    console.log(`[registerModelVersion] Version ${version} already registered -- marking it active.`);
    await ModelVersion.updateMany({ isActive: true }, { isActive: false });
    existing.isActive = true;
    await existing.save();
  } else {
    await ModelVersion.updateMany({ isActive: true }, { isActive: false });
    await ModelVersion.create({
      version,
      labels: metadata.labels,
      numClasses: metadata.num_classes,
      architecture: metadata.architecture,
      dataSource: metadata.data_source,
      overallValAccuracy: metadata.overall_val_accuracy,
      macroPrecision: metadata.macro_precision,
      macroRecall: metadata.macro_recall,
      macroF1: metadata.macro_f1,
      epochsTrained: metadata.epochs_trained,
      trainedAt,
      notes: "Registered from ai/models/metadata.json",
      isActive: true,
    });
    console.log(`[registerModelVersion] Registered new active version ${version}.`);
  }

  await disconnectDB();
}

main().catch((err) => {
  console.error("[registerModelVersion] Failed:", err);
  process.exit(1);
});
