// Shared Jest setup. These tests are mocked-model unit tests (no real
// MongoDB) so they run anywhere, including sandboxed/offline CI -- the
// binary download that a real in-memory MongoDB would need
// (mongodb-memory-server -> fastdl.mongodb.org) is blocked on some
// networks (it was blocked in the environment this backend was
// originally built in). See README.md's Testing section for how to run
// real end-to-end checks against an actual MongoDB instead.

process.env.NODE_ENV = "test";
process.env.AUTH_DEV_BYPASS = "true";
process.env.ADMIN_EMAILS = "";
process.env.MONGODB_URI = "mongodb://127.0.0.1:27017/signtalk-test-unused";
