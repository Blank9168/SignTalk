const request = require("supertest");

jest.mock("../src/models/User");

const User = require("../src/models/User");
const createApp = require("../src/app");

beforeEach(() => {
  jest.resetAllMocks();
});

describe("POST /api/users/sync", () => {
  it("upserts the dev user (dev-bypass identity)", async () => {
    const upserted = { _id: "u1", firebaseUid: "dev-user", email: "dev@example.com", role: "user" };
    User.findOneAndUpdate.mockResolvedValue(upserted);

    const app = createApp();
    const res = await request(app).post("/api/users/sync").send({ displayName: "Aiken" });

    expect(res.status).toBe(200);
    expect(res.body).toEqual(upserted);
    expect(User.findOneAndUpdate).toHaveBeenCalledWith(
      { firebaseUid: "dev-user" },
      expect.objectContaining({ $set: expect.objectContaining({ email: "dev@example.com" }) }),
      expect.any(Object)
    );
  });
});

describe("GET /api/users/me", () => {
  it("404s when no profile exists yet for the caller", async () => {
    User.findOneAndUpdate.mockResolvedValue(null); // requireAuth's dev-bypass lookup
    User.findOne.mockResolvedValue(null);
    const app = createApp();
    const res = await request(app).get("/api/users/me");
    expect(res.status).toBe(404);
  });
});
