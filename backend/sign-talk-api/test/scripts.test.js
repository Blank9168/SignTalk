jest.mock("../src/models/DictionaryEntry");

const DictionaryEntry = require("../src/models/DictionaryEntry");
const { slugify } = require("../src/utils/text");
const { timestamp, buildDumpArgs } = require("../src/scripts/backup");
const { buildRestoreArgs } = require("../src/scripts/restore");
const { seedEntry } = require("../src/scripts/seedDictionary");

beforeEach(() => jest.resetAllMocks());

describe("slugify", () => {
  it("matches the existing slug style", () => {
    expect(slugify("Magandang Umaga")).toBe("magandang_umaga");
    expect(slugify("  Niño! ")).toBe("nino");
    expect(slugify("!!!")).toBe("");
  });
});

describe("backup/restore scripts", () => {
  it("builds the mongodump command", () => {
    expect(buildDumpArgs("mongodb://x/signtalk", "backups/a.gz")).toEqual([
      "--uri",
      "mongodb://x/signtalk",
      "--gzip",
      "--archive=backups/a.gz",
    ]);
    expect(timestamp(new Date("2026-10-10T07:48:52.585Z"))).toBe("2026-10-10T07-48-52Z");
  });

  it("builds the mongorestore command, with --drop only when asked", () => {
    expect(buildRestoreArgs("u", "f.gz")).toEqual(["--uri", "u", "--gzip", "--archive=f.gz"]);
    expect(buildRestoreArgs("u", "f.gz", { drop: true })).toContain("--drop");
  });
});

describe("seedDictionary.seedEntry", () => {
  it("passes every data field through and reports inserts", async () => {
    DictionaryEntry.schema = { path: (k) => (k === "notAField" ? undefined : {}) };
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ lastErrorObject: { upserted: "id" } });
    const data = {
      slug: "isa",
      label: "Isa",
      gloss: "ONE",
      meaningEn: "One",
      meaningFil: "Isa",
      synonyms: ["one"],
      signType: "static",
      hands: "one",
      source: "FSL-105 (DLSU/DOST)",
      validationStatus: "verified",
    };

    await expect(seedEntry(data)).resolves.toBe("created");
    const [filter, update, opts] = DictionaryEntry.findOneAndUpdate.mock.calls[0];
    expect(filter).toEqual({ slug: "isa" });
    expect(update.$set).toMatchObject(data);
    expect(update.$setOnInsert).toMatchObject({ createdBy: "seed-script", validatedBy: "seed-script" });
    expect(opts).toMatchObject({ upsert: true, runValidators: true, includeResultMetadata: true });
  });

  it("throws for an entry without a slug (caller logs and continues)", async () => {
    DictionaryEntry.schema = { path: () => ({}) };
    await expect(seedEntry({ label: "x" })).rejects.toThrow(/slug/);
  });
});
