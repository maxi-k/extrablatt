import React from "react";
import {
  BrowserRouter as Router,
  Switch,
  Route,
  Link
} from "react-router-dom";
import Home from "./components/pages/Home";

export default function App() {
  return (
    <Router>
        <Switch>
          <Route path="/story/:id">
            <h1>Story detail view</h1>
          </Route>
          <Route path="/">
            <Home />
          </Route>
        </Switch>
    </Router>
  );
}
