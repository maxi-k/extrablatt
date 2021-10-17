const express = require("express");
const init = require("./json/init.json");
const thread = require("./json/thread.json");

const app = express();
const port = 3000;

app.get("/", (req, res) => {
  res.json(init);
});

app.get("/thread/:id", (req, res) => {
  res.json(thread);
});

app.listen(port, () => {
  console.log(`Example app listening at http://localhost:${port}`);
});
