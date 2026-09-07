const request = require("supertest");

jest.mock("../src/models/DictionaryEntry");
jest.mock("../src/models/User");

const DictionaryEntry = require("../src/models/DictionaryEntry");
const User = require("../src/models/User");
const createApp = require("../src/app");

const adminUser = { _id: "u1", firebaseUid: "dev-user", email: "dev@example.com", role: "admin" };

beforeEach(() => {
  jest.resetAllMocks();
  // AUTH_DEV_BYPASS calls User.findOneAndUpdate to load/create the dev user.
  User.findOneAndUpdate.mockResolvedValue(adminUser);
});

describe("GET /api/dictionary", () => {
  it("lists entries and applies query filters", async () => {
    const fakeEntries = [{ slug: "isa", label: "Isa", language: "FSL" }];
    const sort = jest.fn().mockResolvedValue(fakeEntries);
    DictionaryEntry.find.mockReturnValue({ sort });

    const app = createApp();
    const res = await request(app).get("/api/dictionary").query({ language: "FSL", category: "FSL - Numbers" });

    expect(res.status).toBe(200);
    expect(res.body.count).toBe(1);
    expect(res.body.entries).toEqual(fakeEntries);
    expect(DictionaryEntry.find).toHaveBeenCalledWith({ language: "FSL", category: "FSL - Numbers" });
  });
});

describe("GET /api/dictionary/:slug", () => {
  it("404s when the slug doesn't exist", async () => {
    DictionaryEntry.findOne.mockResolvedValue(null);
    const app = createApp();
    const res = await request(app).get("/api/dictionary/not-a-real-slug");
    expect(res.status).toBe(404);
    expect(res.body.error).toMatch(/no dictionary entry/i);
  });

  it("returns the entry when found", async () => {
    const entry = { slug: "isa", label: "Isa" };
    DictionaryEntry.findOne.mockResolvedValue(entry);
    const app = createApp();
    const res = await request(app).get("/api/dictionary/isa");
    expect(res.status).toBe(200);
    expect(res.body).toEqual(entry);
  });
});

describe("POST /api/dictionary (admin-only)", () => {
  const payload = {
    slug: "test_word",
    label: "Test Word",
    language: "FSL",
    category: "FSL - Numbers",
    description: "A test entry.",
  };

  it("rejects requests with no auth when dev bypass is off", async () => {
    process.env.AUTH_DEV_BYPASS = "false";
    jest.resetModules();
    const createAppNoBypass = require("../src/app");
    const app = createAppNoBypass();

    const res = await request(app).post("/api/dictionary").send(payload);
    expect(res.status).toBe(401);

    process.env.AUTH_DEV_BYPASS = "true"; // restore for the rest of the suite
  });

  it("creates a new entry when authorized as admin (dev bypass)", async () => {
    DictionaryEntry.findOne.mockResolvedValue(null); // no existing entry with that slug
    DictionaryEntry.create.mockResolvedValue({ ...payload, hasVideo: false, hasTrainingData: false });

    const app = createApp();
    const res = await request(app).post("/api/dictionary").send(payload);

    expect(res.status).toBe(201);
    expect(DictionaryEntry.create).toHaveBeenCalledWith(expect.objectContaining({ slug: "test_word" }));
  });

  it("rejects a duplicate slug with 409", async () => {
    DictionaryEntry.findOne.mockResolvedValue({ slug: "test_word" });
    const app = createApp();
    const res = await request(app).post("/api/dictionary").send(payload);
    expect(res.status).toBe(409);
  });

  it("rejects an invalid payload with 400", async () => {
    const app = createApp();
    const res = await request(app).post("/api/dictionary").send({ slug: "missing_fields" });
    expect(res.status).toBe(400);
    expect(res.body.error).toBe("Validation failed");
  });

  it("blocks a non-admin user with 403", async () => {
    User.findOneAndUpdate.mockResolvedValue({ ...adminUser, role: "user" });
    DictionaryEntry.findOne.mockResolvedValue(null);
    const app = createApp();
    const res = await request(app).post("/api/dictionary").send(payload);
    expect(res.status).toBe(403);
  });
});
