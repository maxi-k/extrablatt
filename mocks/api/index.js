const express = require("express");
const cors = require("cors");
const init = require("./json/init.json");
const thread = require("./json/thread.json");

const app = express();
app.use(cors());
const port = 8080;

app.get("/", (req, res) => {
  res.json(init);
});

app.get("/thread/:id", (req, res) => {
  res.json(thread);
});

app.listen(port, () => {
  console.log(`Example app listening at http://localhost:${port}`);
});
