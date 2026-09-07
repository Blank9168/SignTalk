const express = require("express");

const dictionaryRoutes = require("./dictionary.routes");
const usersRoutes = require("./users.routes");
const logsRoutes = require("./logs.routes");
const modelRoutes = require("./model.routes");

const router = express.Router();

router.get("/health", (req, res) => res.json({ status: "ok", time: new Date().toISOString() }));

router.use("/dictionary", dictionaryRoutes);
router.use("/users", usersRoutes);
router.use("/logs", logsRoutes);
router.use("/model", modelRoutes);

module.exports = router;
