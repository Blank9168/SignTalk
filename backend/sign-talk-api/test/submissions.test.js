const request = require("supertest");

jest.mock("../src/models/Submission");
jest.mock("../src/models/DictionaryEntry");
jest.mock("../src/models/User");
jest.mock("../src/models/AuditLog");

const Submission = require("../src/models/Submission");
const DictionaryEntry = require("../src/models/DictionaryEntry");
const User = require("../src/models/User");
const AuditLog = require("../src/models/AuditLog");
const createApp = require("../src/app");

const SUB_ID = "507f1f77bcf86cd799439011";

const validBody = {
  name: "Salamat Po",
  gloss: "thank-you",
  meaningEn: "Thank you (polite)",
  meaningFil: "Salamat po",
  category: "FSL - Greetings",
  signType: "dynamic",
  hands: "one",
  source: "Team-recorded",
  mediaUrl: "https://example.com/salamat.mp4",
  notes: "Recorded by a Deaf signer in Tarlac.",
  submitterName: "Juan",
};

const asRole = (role) => User.findOneAndUpdate.mockResolvedValue({ _id: "u1", firebaseUid: "dev-user", role });

beforeEach(() => {
  jest.resetAllMocks();
  asRole("admin");
  AuditLog.create.mockResolvedValue({});
  DictionaryEntry.findOne.mockResolvedValue(null);
  Submission.findOne.mockResolvedValue(null);
  Submission.create.mockImplementation(async (d) => ({ _id: SUB_ID, ...d }));
});

describe("POST /api/submissions", () => {
  it("creates a submission (201), derives the slug, records history and audit", async () => {
    const res = await request(createApp()).post("/api/submissions").send(validBody);

    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({ _id: SUB_ID, slug: "salamat_po", status: "submitted", gloss: "THANK-YOU" });
    expect(Submission.create).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Salamat Po",
        slug: "salamat_po",
        status: "submitted",
        submittedBy: "dev-user", // optionalAuth attaches the uid when a token/dev identity is present
        statusHistory: [expect.objectContaining({ status: "submitted" })],
      })
    );
    expect(AuditLog.create).toHaveBeenCalledWith(
      expect.objectContaining({ action: "submission.create", entityType: "submission", entityId: SUB_ID })
    );
  });

  it("works without mediaUrl (optional)", async () => {
    const { mediaUrl, ...body } = validBody;
    const res = await request(createApp()).post("/api/submissions").send(body);
    expect(res.status).toBe(201);
  });

  it.each(["name", "meaningEn", "meaningFil", "category", "signType", "hands", "source"])(
    "rejects a missing required field: %s",
    async (field) => {
      const body = { ...validBody };
      delete body[field];
      const res = await request(createApp()).post("/api/submissions").send(body);
      expect(res.status).toBe(400);
      expect(res.body.error).toBe("Validation failed");
      expect(res.body.details.map((d) => d.path)).toContain(field);
      expect(Submission.create).not.toHaveBeenCalled();
    }
  );

  it.each([
    ["bad signType", { signType: "moving" }],
    ["bad hands", { hands: "both" }],
    ["non-URL mediaUrl", { mediaUrl: "not a url" }],
    ["ftp mediaUrl", { mediaUrl: "ftp://example.com/x.mp4" }],
    ["unknown field", { status: "published" }],
    ["client-set submittedBy", { submittedBy: "someone-else" }],
    ["name with no letters/digits", { name: "!!!" }],
    ["over-long name", { name: "x".repeat(81) }],
  ])("rejects %s with 400", async (_label, patch) => {
    const res = await request(createApp())
      .post("/api/submissions")
      .send({ ...validBody, ...patch });
    expect(res.status).toBe(400);
    expect(Submission.create).not.toHaveBeenCalled();
  });

  it("rejects an empty body", async () => {
    const res = await request(createApp()).post("/api/submissions").send({});
    expect(res.status).toBe(400);
  });

  it("409s when a non-archived dictionary entry has the same slug", async () => {
    DictionaryEntry.findOne.mockResolvedValue({ _id: "e1", slug: "salamat_po", label: "Salamat Po", validationStatus: "verified" });
    const res = await request(createApp()).post("/api/submissions").send(validBody);
    expect(res.status).toBe(409);
    expect(res.body.conflict).toMatchObject({ type: "dictionary", slug: "salamat_po", matchedOn: "slug" });
    expect(DictionaryEntry.findOne).toHaveBeenCalledWith(
      expect.objectContaining({ archived: { $ne: true }, $or: expect.arrayContaining([{ slug: "salamat_po" }, { synonyms: "salamat po" }]) })
    );
    expect(Submission.create).not.toHaveBeenCalled();
  });

  it("409s when the name matches an entry's synonym", async () => {
    DictionaryEntry.findOne.mockResolvedValue({ _id: "e2", slug: "isa", label: "Isa", synonyms: ["one"] });
    const res = await request(createApp())
      .post("/api/submissions")
      .send({ ...validBody, name: "One" });
    expect(res.status).toBe(409);
    expect(res.body.conflict).toMatchObject({ type: "dictionary", slug: "isa", matchedOn: "synonym" });
  });

  it("409s when an open submission already exists for the slug", async () => {
    Submission.findOne.mockResolvedValue({ _id: "s9", slug: "salamat_po", name: "Salamat po", status: "for_review" });
    const res = await request(createApp()).post("/api/submissions").send(validBody);
    expect(res.status).toBe(409);
    expect(res.body.conflict).toMatchObject({ type: "submission", status: "for_review" });
    expect(Submission.findOne).toHaveBeenCalledWith({
      slug: "salamat_po",
      status: { $in: ["submitted", "for_review", "verified"] },
    });
  });

  it("rate-limits submissions per IP (10/hour)", async () => {
    const app = createApp();
    for (let i = 0; i < 10; i += 1) {
      // eslint-disable-next-line no-await-in-loop
      const ok = await request(app).post("/api/submissions").send(validBody);
      expect(ok.status).toBe(201);
    }
    const res = await request(app).post("/api/submissions").send(validBody);
    expect(res.status).toBe(429);
    expect(res.body.error).toMatch(/too many/i);
  });
});

describe("GET /api/submissions (validator/admin)", () => {
  const mockList = (rows) => {
    const limit = jest.fn().mockResolvedValue(rows);
    const skip = jest.fn().mockReturnValue({ limit });
    const sort = jest.fn().mockReturnValue({ skip });
    Submission.find.mockReturnValue({ sort });
    Submission.countDocuments.mockResolvedValue(rows.length);
  };

  it("lists by status for a validator", async () => {
    asRole("validator");
    mockList([{ _id: SUB_ID, status: "submitted" }]);
    const res = await request(createApp()).get("/api/submissions").query({ status: "submitted" });
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ count: 1, total: 1, page: 1, limit: 50 });
    expect(Submission.find).toHaveBeenCalledWith({ status: "submitted" });
  });

  it("403s for a plain user", async () => {
    asRole("user");
    const res = await request(createApp()).get("/api/submissions");
    expect(res.status).toBe(403);
  });

  it("400s for an invalid status filter or id", async () => {
    expect((await request(createApp()).get("/api/submissions").query({ status: "bogus" })).status).toBe(400);
    expect((await request(createApp()).get("/api/submissions/not-an-id")).status).toBe(400);
  });
});

describe("POST /api/submissions/:id/review", () => {
  const sub = (status) => ({ _id: SUB_ID, slug: "salamat_po", status, ...validBody, gloss: "THANK-YOU", language: "FSL" });
  const review = (body) => request(createApp()).post(`/api/submissions/${SUB_ID}/review`).send(body);

  beforeEach(() => {
    asRole("validator");
    Submission.findOneAndUpdate.mockImplementation(async (_q, update) => ({ ...sub("x"), ...update.$set }));
  });

  it.each([
    ["start_review", "submitted", "for_review"],
    ["verify", "for_review", "verified"],
    ["reject", "submitted", "rejected"],
    ["reject", "for_review", "rejected"],
    ["reject", "verified", "rejected"],
  ])("%s: %s -> %s", async (action, from, to) => {
    Submission.findById.mockResolvedValue(sub(from));
    const res = await review({ action, note: "looks fine" });
    expect(res.status).toBe(200);
    expect(res.body.submission.status).toBe(to);
    expect(Submission.findOneAndUpdate).toHaveBeenCalledWith(
      { _id: SUB_ID, status: from },
      {
        $set: expect.objectContaining({ status: to, reviewedBy: "dev-user", reviewNote: "looks fine" }),
        $push: { statusHistory: expect.objectContaining({ status: to, by: "dev-user", note: "looks fine" }) },
      },
      expect.any(Object)
    );
    expect(AuditLog.create).toHaveBeenCalledWith(
      expect.objectContaining({ action: "submission.review", entityType: "submission", entityId: SUB_ID })
    );
  });

  it.each([
    ["verify", "submitted"],
    ["publish", "submitted"],
    ["publish", "for_review"],
    ["start_review", "verified"],
    ["reject", "published"],
    ["start_review", "rejected"],
    ["verify", "published"],
  ])("refuses %s from %s with 409", async (action, from) => {
    Submission.findById.mockResolvedValue(sub(from));
    const res = await review({ action });
    expect(res.status).toBe(409);
    expect(res.body.currentStatus).toBe(from);
    expect(Array.isArray(res.body.allowedActions)).toBe(true);
    expect(Submission.findOneAndUpdate).not.toHaveBeenCalled();
    expect(DictionaryEntry.create).not.toHaveBeenCalled();
  });

  it("publish creates a verified, dictionary-only entry and links it", async () => {
    Submission.findById.mockResolvedValue(sub("verified"));
    DictionaryEntry.create.mockImplementation(async (d) => ({ _id: "e-new", ...d }));

    const res = await review({ action: "publish" });

    expect(res.status).toBe(200);
    expect(DictionaryEntry.create).toHaveBeenCalledWith(
      expect.objectContaining({
        slug: "salamat_po",
        label: "Salamat Po",
        meaningEn: "Thank you (polite)",
        meaningFil: "Salamat po",
        signType: "dynamic",
        hands: "one",
        source: "Team-recorded",
        validationStatus: "verified",
        validatedBy: "dev-user",
        hasTrainingData: false, // never feeds the recognition model
      })
    );
    expect(res.body.submission).toMatchObject({ status: "published", publishedEntry: "e-new" });
    expect(res.body.entry.slug).toBe("salamat_po");
    const actions = AuditLog.create.mock.calls.map(([d]) => d.action);
    expect(actions).toEqual(expect.arrayContaining(["dictionary.create", "submission.review"]));
  });

  it("publish 409s if the slug was taken in the meantime", async () => {
    Submission.findById.mockResolvedValue(sub("verified"));
    DictionaryEntry.findOne.mockResolvedValue({ _id: "e1", slug: "salamat_po", label: "Salamat Po" });
    const res = await review({ action: "publish" });
    expect(res.status).toBe(409);
    expect(DictionaryEntry.create).not.toHaveBeenCalled();
    expect(Submission.findOneAndUpdate).not.toHaveBeenCalled();
  });

  it("409s if another reviewer changed the status concurrently", async () => {
    Submission.findById.mockResolvedValue(sub("submitted"));
    Submission.findOneAndUpdate.mockResolvedValue(null);
    const res = await review({ action: "start_review" });
    expect(res.status).toBe(409);
  });

  it("validates the review body", async () => {
    expect((await review({ action: "approve" })).status).toBe(400);
    expect((await review({})).status).toBe(400);
    expect((await review({ action: "verify", extra: 1 })).status).toBe(400);
  });

  it("404s for an unknown submission", async () => {
    Submission.findById.mockResolvedValue(null);
    expect((await review({ action: "verify" })).status).toBe(404);
  });

  it("403s for a plain user", async () => {
    asRole("user");
    expect((await review({ action: "start_review" })).status).toBe(403);
  });
});
