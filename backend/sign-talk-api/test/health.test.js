const request = require("supertest");
const createApp = require("../src/app");

describe("GET /api/health", () => {
  it("returns ok status", async () => {
    const app = createApp();
    const res = await request(app).get("/api/health");
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("ok");
    expect(res.body.time).toBeDefined();
  });
});

describe("unknown route", () => {
  it("returns 404 with a helpful message", async () => {
    const app = createApp();
    const res = await request(app).get("/api/does-not-exist");
    expect(res.status).toBe(404);
    expect(res.body.error).toMatch(/Route not found/);
  });
});
