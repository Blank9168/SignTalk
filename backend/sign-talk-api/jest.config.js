module.exports = {
  testEnvironment: "node",
  setupFilesAfterEnv: ["<rootDir>/test/setup.js"],
  testTimeout: 30000, // mongodb-memory-server's first binary download/start can be slow
};
