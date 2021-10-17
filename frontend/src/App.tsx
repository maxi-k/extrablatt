import React from "react";
import {
  BrowserRouter as Router,
  Switch,
  Route,
  Link
} from "react-router-dom";
import Loader from "./components/atoms/Loader/Loader";
import Home from "./components/pages/Home";

export default function App() {
  return (
    <Router>
        <Switch>
          <Route path="/thread/:id">
            <Loader />
          </Route>
          <Route path="/">
            <Home />
          </Route>
        </Switch>
    </Router>
  );
}
