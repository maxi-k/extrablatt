import React from "react";
import {
  BrowserRouter as Router,
  Switch,
  Route,
} from "react-router-dom";
import Detail from "./components/pages/Detail";
import Home from "./components/pages/Home";

export default function App() {
  return (
    <Router>
        <Switch>
          <Route path="/thread/:id">
            <Detail />
          </Route>
          <Route path="/">
            <Home />
          </Route>
        </Switch>
    </Router>
  );
}
