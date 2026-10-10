const request = require("supertest");

jest.mock("../src/models/DictionaryEntry");
jest.mock("../src/models/User");
jest.mock("../src/models/AuditLog");

const DictionaryEntry = require("../src/models/DictionaryEntry");
const User = require("../src/models/User");
const AuditLog = require("../src/models/AuditLog");
const createApp = require("../src/app");
const { requireRole, requireAdmin } = require("../src/middleware/auth");

const asRole = (role) => User.findOneAndUpdate.mockResolvedValue({ _id: "u1", firebaseUid: "dev-user", role });
const entry = { _id: "e1", slug: "isa", label: "Isa", archived: false, validationStatus: "unverified" };

beforeEach(() => {
  jest.resetAllMocks();
  AuditLog.create.mockResolvedValue({});
});

describe("requireRole middleware", () => {
  const run = (mw, user) => {
    const res = { status: jest.fn() };
    const next = jest.fn();
    mw({ user }, res, next);
    return { res, next };
  };

  it("passes when the user has one of the roles", () => {
    const { next } = run(requireRole("validator", "admin"), { role: "validator" });
    expect(next).toHaveBeenCalledWith();
  });

  it("403s when the role is missing or there is no user", () => {
    for (const user of [{ role: "user" }, null]) {
      const { res, next } = run(requireRole("validator", "admin"), user);
      expect(res.status).toHaveBeenCalledWith(403);
      expect(next.mock.calls[0][0]).toBeInstanceOf(Error);
    }
  });

  it("requireAdmin still works as requireRole('admin')", () => {
    expect(run(requireAdmin, { role: "admin" }).next).toHaveBeenCalledWith();
    const { next } = run(requireAdmin, { role: "validator" });
    expect(next.mock.calls[0][0].message).toMatch(/admin account/);
  });
});

describe("dictionary permissions by role", () => {
  it("lets a validator edit an entry", async () => {
    asRole("validator");
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, gloss: "ISA" });
    const res = await request(createApp()).patch("/api/dictionary/isa").send({ gloss: "isa" });
    expect(res.status).toBe(200);
  });

  it("lets a validator verify an entry", async () => {
    asRole("validator");
    DictionaryEntry.findOne.mockResolvedValue(entry);
    DictionaryEntry.findOneAndUpdate.mockResolvedValue({ ...entry, validationStatus: "verified" });
    const res = await request(createApp()).post("/api/dictionary/isa/verify");
    expect(res.status).toBe(200);
  });

  it("blocks a plain user from editing", async () => {
    asRole("user");
    const res = await request(createApp()).patch("/api/dictionary/isa").send({ gloss: "ISA" });
    expect(res.status).toBe(403);
    expect(DictionaryEntry.findOneAndUpdate).not.toHaveBeenCalled();
  });

  it.each([
    ["post", "/api/dictionary/isa/archive"],
    ["post", "/api/dictionary/isa/restore"],
    ["delete", "/api/dictionary/isa"],
  ])("blocks a validator from %s %s (admin only)", async (method, path) => {
    asRole("validator");
    const res = await request(createApp())[method](path);
    expect(res.status).toBe(403);
    expect(DictionaryEntry.findOneAndUpdate).not.toHaveBeenCalled();
  });

  it("blocks a validator from creating entries (admin only)", async () => {
    asRole("validator");
    const res = await request(createApp())
      .post("/api/dictionary")
      .send({ slug: "x", label: "X", language: "FSL", category: "c", description: "d" });
    expect(res.status).toBe(403);
  });

  it("only validators/admins may use ?includeArchived=true", async () => {
    asRole("user");
    let res = await request(createApp()).get("/api/dictionary").query({ includeArchived: "true" });
    expect(res.status).toBe(403);

    asRole("validator");
    DictionaryEntry.find.mockReturnValue({ sort: jest.fn().mockResolvedValue([entry]) });
    res = await request(createApp()).get("/api/dictionary").query({ includeArchived: "true" });
    expect(res.status).toBe(200);
    expect(DictionaryEntry.find).toHaveBeenCalledWith({}); // no archived filter
  });
});

describe("PATCH /api/users/:id/role", () => {
  const target = { _id: "507f1f77bcf86cd799439011", firebaseUid: "uid-2", email: "v@example.com", role: "user" };

  it("lets an admin promote a user to validator and writes an audit entry", async () => {
    asRole("admin");
    User.findOne.mockResolvedValue(target);
    User.findByIdAndUpdate.mockResolvedValue({ ...target, role: "validator" });

    const res = await request(createApp()).patch("/api/users/uid-2/role").send({ role: "validator" });

    expect(res.status).toBe(200);
    expect(res.body.role).toBe("validator");
    expect(User.findOne).toHaveBeenCalledWith({ firebaseUid: "uid-2" });
    expect(User.findByIdAndUpdate).toHaveBeenCalledWith(target._id, { role: "validator" }, expect.any(Object));
    expect(AuditLog.create).toHaveBeenCalledWith(
      expect.objectContaining({ action: "user.role", entityType: "user", entityId: "uid-2", actorRole: "admin" })
    );
  });

  it("looks users up by _id or email too", async () => {
    asRole("admin");
    User.findOne.mockResolvedValue(target);
    User.findByIdAndUpdate.mockResolvedValue({ ...target, role: "validator" });
    await request(createApp()).patch(`/api/users/${target._id}/role`).send({ role: "validator" });
    expect(User.findOne).toHaveBeenLastCalledWith({ _id: target._id });
    await request(createApp()).patch("/api/users/V@Example.com/role").send({ role: "validator" });
    expect(User.findOne).toHaveBeenLastCalledWith({ email: "v@example.com" });
  });

  it("rejects an invalid role, unknown fields, and empty bodies", async () => {
    asRole("admin");
    const app = createApp();
    expect((await request(app).patch("/api/users/uid-2/role").send({ role: "superuser" })).status).toBe(400);
    expect((await request(app).patch("/api/users/uid-2/role").send({ role: "admin", email: "x" })).status).toBe(400);
    expect((await request(app).patch("/api/users/uid-2/role").send({})).status).toBe(400);
    expect(User.findByIdAndUpdate).not.toHaveBeenCalled();
  });

  it("blocks validators from changing roles", async () => {
    asRole("validator");
    const res = await request(createApp()).patch("/api/users/uid-2/role").send({ role: "admin" });
    expect(res.status).toBe(403);
  });

  it("stops an admin from demoting themselves", async () => {
    asRole("admin");
    User.findOne.mockResolvedValue({ _id: "u1", firebaseUid: "dev-user", role: "admin" });
    const res = await request(createApp()).patch("/api/users/dev-user/role").send({ role: "user" });
    expect(res.status).toBe(400);
    expect(User.findByIdAndUpdate).not.toHaveBeenCalled();
  });

  it("404s for an unknown user", async () => {
    asRole("admin");
    User.findOne.mockResolvedValue(null);
    const res = await request(createApp()).patch("/api/users/nobody/role").send({ role: "validator" });
    expect(res.status).toBe(404);
  });
});
