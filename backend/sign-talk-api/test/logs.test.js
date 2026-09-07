const request = require("supertest");

jest.mock("../src/models/RecognitionLog");
jest.mock("../src/models/User");

const RecognitionLog = require("../src/models/RecognitionLog");
const User = require("../src/models/User");
const createApp = require("../src/app");

beforeEach(() => {
  jest.resetAllMocks();
  User.findOneAndUpdate.mockResolvedValue({ _id: "u1", role: "admin" });
  User.findOne.mockResolvedValue({ _id: "u1", role: "admin" });
});

describe("POST /api/logs/recognition", () => {
  it("requires predictedSlug and a numeric confidence", async () => {
    const app = createApp();
    const res = await request(app).post("/api/logs/recognition").send({ predictedSlug: "isa" });
    expect(res.status).toBe(400);
  });

  it("records a valid recognition event", async () => {
    RecognitionLog.create.mockResolvedValue({ _id: "log1", predictedSlug: "isa", confidence: 0.97 });
    const app = createApp();
    const res = await request(app).post("/api/logs/recognition").send({ predictedSlug: "isa", confidence: 0.97 });
    expect(res.status).toBe(201);
    expect(res.body.predictedSlug).toBe("isa");
  });
});

describe("GET /api/logs/stats (admin-only)", () => {
  it("aggregates recognition volume and top signs", async () => {
    RecognitionLog.countDocuments.mockResolvedValue(42);
    RecognitionLog.aggregate
      .mockResolvedValueOnce([{ _id: "isa", count: 10, avgConfidence: 0.9 }])
      .mockResolvedValueOnce([
        { _id: true, count: 8 },
        { _id: false, count: 2 },
      ]);

    const app = createApp();
    const res = await request(app).get("/api/logs/stats");

    expect(res.status).toBe(200);
    expect(res.body.totalRecognitions).toBe(42);
    expect(res.body.topPredictedSigns[0].slug).toBe("isa");
    expect(res.body.userReportedAccuracy).toBeCloseTo(0.8);
  });
});
