const request = require("supertest");

jest.mock("../src/models/ModelVersion");
jest.mock("../src/models/User");

const ModelVersion = require("../src/models/ModelVersion");
const User = require("../src/models/User");
const createApp = require("../src/app");

beforeEach(() => {
  jest.resetAllMocks();
  User.findOneAndUpdate.mockResolvedValue({ _id: "u1", role: "admin", email: "dev@example.com" });
});

describe("GET /api/model/version", () => {
  it("404s when no model version is registered yet", async () => {
    ModelVersion.findOne.mockReturnValue({ sort: jest.fn().mockResolvedValue(null) });
    const app = createApp();
    const res = await request(app).get("/api/model/version");
    expect(res.status).toBe(404);
  });

  it("returns the active version's metadata", async () => {
    const active = {
      version: "2026-09-06_acc0.9280",
      numClasses: 50,
      overallValAccuracy: 0.928,
      labels: ["isa", "dalawa"],
    };
    ModelVersion.findOne.mockReturnValue({ sort: jest.fn().mockResolvedValue(active) });
    const app = createApp();
    const res = await request(app).get("/api/model/version");
    expect(res.status).toBe(200);
    expect(res.body.numClasses).toBe(50);
  });
});

describe("POST /api/model/versions (admin-only)", () => {
  it("rejects a payload missing required fields", async () => {
    const app = createApp();
    const res = await request(app).post("/api/model/versions").send({ version: "x" });
    expect(res.status).toBe(400);
  });

  it("creates a version and deactivates prior ones when makeActive is set", async () => {
    ModelVersion.updateMany.mockResolvedValue({ acknowledged: true });
    ModelVersion.create.mockResolvedValue({
      version: "v2",
      labels: ["isa"],
      numClasses: 50,
      isActive: true,
    });

    const app = createApp();
    const res = await request(app)
      .post("/api/model/versions")
      .send({ version: "v2", labels: ["isa"], numClasses: 50, makeActive: true });

    expect(res.status).toBe(201);
    expect(ModelVersion.updateMany).toHaveBeenCalledWith({ isActive: true }, { isActive: false });
  });
});
