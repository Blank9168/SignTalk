// Archive-not-delete, restore, verify, PATCH validation, search, and the
// audit trail for dictionary writes.
const request = require("supertest");

jest.mock("../src/models/DictionaryEntry");
jest.mock("../src/models/User");
jest.mock("../src/models/AuditLog");

const DictionaryEntry = require("../src/models/DictionaryEntry");
const User = require("../src/models/User");
const AuditLog = require("../src/models/AuditLog");
const createApp = require("../src/app");

const entry = { _id: "e1", slug: "isa", label: "Isa", language: "FSL", archived: false, validationStatus: "unverified" };

beforeEach(() => {
  jest.resetAllMocks();
  User.findOneAndUpdate.mockResolvedValue({ _id: "u1", firebaseUid: "dev-user", role: "admin" });
  AuditLog.create.mockResolvedValue({});
});

describe("archive instead of delete", () => {
  it("DELETE archives the entry and never hard-deletes", async () => {
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, archived: true });

    const res = await request(createApp()).delete("/api/dictionary/isa");

    expect(res.status).toBe(200);
    expect(res.body.archived).toBe(true);
    expect(DictionaryEntry.findOneAndUpdate).toHaveBeenCalledWith(
      { slug: "isa" },
      { $set: expect.objectContaining({ archived: true, archivedBy: "dev-user", archivedAt: expect.any(Date) }) },
      expect.any(Object)
    );
    expect(DictionaryEntry.findOneAndDelete).not.toHaveBeenCalled();
    expect(DictionaryEntry.deleteOne).not.toHaveBeenCalled();
    expect(DictionaryEntry.deleteMany).not.toHaveBeenCalled();
    expect(AuditLog.create).toHaveBeenCalledWith(
      expect.objectContaining({ action: "dictionary.archive", entityType: "dictionary", entityId: "isa", actorUid: "dev-user" })
    );
  });

  it("POST /:slug/archive does the same", async () => {
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, archived: true });
    const res = await request(createApp()).post("/api/dictionary/isa/archive");
    expect(res.status).toBe(200);
    expect(DictionaryEntry.findOneAndDelete).not.toHaveBeenCalled();
  });

  it("archiving an already-archived entry is a no-op", async () => {
    DictionaryEntry.findOne.mockResolvedValue({ ...entry, archived: true });
    const res = await request(createApp()).post("/api/dictionary/isa/archive");
    expect(res.status).toBe(200);
    expect(DictionaryEntry.findOneAndUpdate).not.toHaveBeenCalled();
    expect(AuditLog.create).not.toHaveBeenCalled();
  });

  it("restore un-archives and is audited", async () => {
    DictionaryEntry.findOne.mockResolvedValue({ ...entry, archived: true });
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, archived: false });
    const res = await request(createApp()).post("/api/dictionary/isa/restore");
    expect(res.status).toBe(200);
    expect(DictionaryEntry.findOneAndUpdate).toHaveBeenCalledWith(
      { slug: "isa" },
      { $set: expect.objectContaining({ archived: false, archivedAt: null, archivedBy: null }) },
      expect.any(Object)
    );
    expect(AuditLog.create).toHaveBeenCalledWith(expect.objectContaining({ action: "dictionary.restore" }));
  });

  it("404s when archiving an unknown slug", async () => {
    DictionaryEntry.findOne.mockResolvedValue(null);
    const res = await request(createApp()).delete("/api/dictionary/nope");
    expect(res.status).toBe(404);
  });

  it("public GET /:slug excludes archived entries", async () => {
    DictionaryEntry.findOne.mockResolvedValue(null);
    const res = await request(createApp()).get("/api/dictionary/isa");
    expect(res.status).toBe(404);
    expect(DictionaryEntry.findOne).toHaveBeenCalledWith({ slug: "isa", archived: { $ne: true } });
  });
});

describe("POST /api/dictionary/:slug/verify", () => {
  it("stamps validationStatus, validatedBy and validatedAt, and audits", async () => {
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, validationStatus: "verified", validatedBy: "dev-user" });

    const res = await request(createApp()).post("/api/dictionary/isa/verify");

    expect(res.status).toBe(200);
    expect(DictionaryEntry.findOneAndUpdate).toHaveBeenCalledWith(
      { slug: "isa" },
      {
        $set: expect.objectContaining({ validationStatus: "verified", validatedBy: "dev-user", validatedAt: expect.any(Date) }),
      },
      expect.any(Object)
    );
    expect(AuditLog.create).toHaveBeenCalledWith(expect.objectContaining({ action: "dictionary.verify" }));
  });

  it("refuses to verify an archived entry", async () => {
    DictionaryEntry.findOne.mockResolvedValue({ ...entry, archived: true });
    const res = await request(createApp()).post("/api/dictionary/isa/verify");
    expect(res.status).toBe(409);
  });
});

describe("PATCH /api/dictionary/:slug validation", () => {
  const patch = (body) => request(createApp()).patch("/api/dictionary/isa").send(body);

  it.each([
    ["an empty body", {}],
    ["an unknown field", { foo: "bar" }],
    ["a non-editable field (slug)", { slug: "other" }],
    ["a non-editable field (archived)", { archived: true }],
    ["a bad signType enum", { signType: "wiggly" }],
    ["a bad hands enum", { hands: "three" }],
    ["a bad language enum", { language: "JSL" }],
    ["a non-array synonyms", { synonyms: "one" }],
    ["a non-string synonym", { synonyms: ["one", 2] }],
    ["an empty label", { label: "   " }],
    ["a non-boolean hasVideo", { hasVideo: "yes" }],
    ["validationStatus=verified (must use /verify)", { validationStatus: "verified" }],
  ])("rejects %s with 400", async (_name, body) => {
    const res = await patch(body);
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("Validation failed");
    expect(DictionaryEntry.findOneAndUpdate).not.toHaveBeenCalled();
  });

  it("applies a valid patch, normalizes synonyms/gloss, and audits before/after", async () => {
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, gloss: "ONE", synonyms: ["one", "uno"] });

    const res = await patch({ gloss: "one", synonyms: ["  One ", "UNO"], signType: "static", hands: "one", source: "FSL-105 (DLSU/DOST)" });

    expect(res.status).toBe(200);
    expect(DictionaryEntry.findOneAndUpdate).toHaveBeenCalledWith(
      { slug: "isa" },
      {
        $set: expect.objectContaining({
          gloss: "ONE",
          synonyms: ["one", "uno"],
          signType: "static",
          hands: "one",
          source: "FSL-105 (DLSU/DOST)",
          updatedBy: "dev-user",
        }),
      },
      expect.objectContaining({ runValidators: true })
    );
    expect(AuditLog.create).toHaveBeenCalledWith(
      expect.objectContaining({
        action: "dictionary.update",
        entityId: "isa",
        before: expect.objectContaining({ slug: "isa", label: "Isa" }),
        after: expect.objectContaining({ gloss: "ONE" }),
      })
    );
  });

  it("404s for an unknown slug", async () => {
    DictionaryEntry.findOne.mockResolvedValue(null);
    const res = await patch({ gloss: "X" });
    expect(res.status).toBe(404);
  });
});

describe("POST /api/dictionary with new fields", () => {
  it("accepts the expert metadata and stamps createdBy", async () => {
    DictionaryEntry.findOne.mockResolvedValue(null);
    DictionaryEntry.create.mockImplementation(async (d) => d);
    const res = await request(createApp())
      .post("/api/dictionary")
      .send({
        slug: "salamat",
        label: "Salamat",
        language: "FSL",
        category: "FSL - Greetings",
        description: "Thank you",
        gloss: "thank-you",
        meaningEn: "Thank you",
        meaningFil: "Salamat",
        synonyms: ["Thanks"],
        signType: "dynamic",
        hands: "one",
        source: "Team-recorded",
      });
    expect(res.status).toBe(201);
    expect(DictionaryEntry.create).toHaveBeenCalledWith(
      expect.objectContaining({ gloss: "THANK-YOU", synonyms: ["thanks"], createdBy: "dev-user", signType: "dynamic" })
    );
    expect(AuditLog.create).toHaveBeenCalledWith(expect.objectContaining({ action: "dictionary.create", entityId: "salamat" }));
  });

  it("rejects unknown fields on create", async () => {
    const res = await request(createApp())
      .post("/api/dictionary")
      .send({ slug: "x", label: "X", language: "FSL", category: "c", description: "d", archived: true });
    expect(res.status).toBe(400);
  });
});

describe("GET /api/dictionary?q= search", () => {
  it("uses the text index first, then falls back to a substring match on synonyms/meanings", async () => {
    const sort = jest.fn().mockResolvedValueOnce([]).mockResolvedValueOnce([entry]);
    DictionaryEntry.find.mockReturnValue({ sort });

    const res = await request(createApp()).get("/api/dictionary").query({ q: "on" });

    expect(res.status).toBe(200);
    expect(res.body.count).toBe(1);
    expect(DictionaryEntry.find).toHaveBeenNthCalledWith(1, { archived: { $ne: true }, $text: { $search: "on" } });
    const fallback = DictionaryEntry.find.mock.calls[1][0];
    const fields = fallback.$or.map((clause) => Object.keys(clause)[0]);
    expect(fields).toEqual(expect.arrayContaining(["synonyms", "meaningEn", "meaningFil", "gloss", "label"]));
    expect(fallback.archived).toEqual({ $ne: true });
  });

  it("ignores object-shaped query params (no operator injection)", async () => {
    DictionaryEntry.find.mockReturnValue({ sort: jest.fn().mockResolvedValue([]) });
    await request(createApp()).get("/api/dictionary?language[$ne]=FSL");
    expect(DictionaryEntry.find).toHaveBeenCalledWith({ archived: { $ne: true } });
  });
});

describe("audit helper", () => {
  it("never throws when the audit write fails", async () => {
    AuditLog.create.mockRejectedValue(new Error("db down"));
    const spy = jest.spyOn(console, "error").mockImplementation(() => {});
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, archived: true });

    const res = await request(createApp()).post("/api/dictionary/isa/archive");

    expect(res.status).toBe(200);
    expect(spy).toHaveBeenCalledWith(expect.stringMatching(/\[audit\] Failed to record dictionary.archive/));
    spy.mockRestore();
  });
});
