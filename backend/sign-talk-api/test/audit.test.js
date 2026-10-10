const request = require("supertest");

jest.mock("../src/models/AuditLog");
jest.mock("../src/models/User");

const AuditLog = require("../src/models/AuditLog");
const User = require("../src/models/User");
const createApp = require("../src/app");

const asRole = (role) => User.findOneAndUpdate.mockResolvedValue({ _id: "u1", firebaseUid: "dev-user", role });

function mockFind(rows) {
  const limit = jest.fn().mockResolvedValue(rows);
  const skip = jest.fn().mockReturnValue({ limit });
  const sort = jest.fn().mockReturnValue({ skip });
  AuditLog.find.mockReturnValue({ sort });
  AuditLog.countDocuments.mockResolvedValue(rows.length);
  return { sort, skip, limit };
}

beforeEach(() => {
  jest.resetAllMocks();
});

describe("GET /api/audit (admin only)", () => {
  it("returns paginated, filtered audit entries", async () => {
    asRole("admin");
    const chain = mockFind([{ action: "dictionary.archive", entityType: "dictionary", entityId: "isa" }]);

    const res = await request(createApp())
      .get("/api/audit")
      .query({ entityType: "dictionary", entityId: "isa", actor: "uid-1", limit: 10, page: 2 });

    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ count: 1, total: 1, page: 2, limit: 10 });
    expect(AuditLog.find).toHaveBeenCalledWith({ entityType: "dictionary", entityId: "isa", actorUid: "uid-1" });
    expect(chain.sort).toHaveBeenCalledWith({ at: -1 });
    expect(chain.skip).toHaveBeenCalledWith(10);
    expect(chain.limit).toHaveBeenCalledWith(10);
  });

  it("403s for validators", async () => {
    asRole("validator");
    const res = await request(createApp()).get("/api/audit");
    expect(res.status).toBe(403);
  });

  it("400s for bad paging/filter params", async () => {
    asRole("admin");
    expect((await request(createApp()).get("/api/audit").query({ limit: 1000 })).status).toBe(400);
    expect((await request(createApp()).get("/api/audit").query({ entityType: "bogus" })).status).toBe(400);
  });
});

describe("write rate limit", () => {
  it("caps non-GET requests at 60 per 15 min per IP but leaves GETs alone", async () => {
    asRole("user");
    const app = createApp();
    for (let i = 0; i < 60; i += 1) {
      // 403 (not admin) still counts as a write attempt
      // eslint-disable-next-line no-await-in-loop
      await request(app).patch("/api/users/x/role").send({ role: "user" });
    }
    const blocked = await request(app).patch("/api/users/x/role").send({ role: "user" });
    expect(blocked.status).toBe(429);

    const read = await request(app).get("/api/health");
    expect(read.status).toBe(200);
  });
});
