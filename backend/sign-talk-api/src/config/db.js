const mongoose = require("mongoose");
const env = require("./env");

let connectPromise = null;

/**
 * Connects to MongoDB using MONGODB_URI. Safe to call multiple times --
 * returns the same in-flight/resolved connection instead of reconnecting.
 */
function connectDB() {
  if (connectPromise) return connectPromise;

  mongoose.set("strictQuery", true);

  connectPromise = mongoose
    .connect(env.mongodbUri)
    .then((conn) => {
      // eslint-disable-next-line no-console
      console.log(`[db] Connected to MongoDB at ${conn.connection.host}/${conn.connection.name}`);
      return conn;
    })
    .catch((err) => {
      connectPromise = null; // allow a retry on next call
      // eslint-disable-next-line no-console
      console.error("[db] MongoDB connection failed:", err.message);
      throw err;
    });

  return connectPromise;
}

async function disconnectDB() {
  await mongoose.disconnect();
  connectPromise = null;
}

module.exports = { connectDB, disconnectDB, mongoose };
