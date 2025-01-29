// @flow

import React, { type Node, type ComponentType } from "react";
import { Routes, Route } from "react-router";
import SearchRouter from "./Search/SearchRouter";
import ImportRouter from "./Import/ImportRouter";
import useStores from "../stores/use-stores";
import { observer } from "mobx-react-lite";
import PageNotFoundScreen from "./components/Layout/PageNotFoundScreen";
import { Navigate } from "react-router-dom";
import InitialScreen from "./components/Layout/InitialScreen";
import PermalinkRouter from "./PermalinkRouter";
import MoveDialog from "./components/MoveToTarget/MoveDialog";
import ErrorBoundary from "../components/ErrorBoundary";
import Alerts from "./components/Alerts";
import Analytics from "./Analytics";
import Box from "@mui/material/Box";

const RedirectToBench = () => {
  const {
    peopleStore: { currentUser },
  } = useStores();
  if (!currentUser) throw new Error("Current user is not available");
  return (
    <Navigate
      to={`search?parentGlobalId=BE${currentUser.workbenchId}`}
      replace={true}
    />
  );
};

function InventoryRouter(): Node {
  const { uiStore } = useStores();

  return (
    <Analytics>
      <ErrorBoundary>
        <Alerts>
          <Box height="100%">
            <Routes>
              <Route
                exact
                path="/"
                element={
                  !uiStore.isVerySmall ? <RedirectToBench /> : <InitialScreen />
                }
              />
              <Route
                path="/container/:id"
                element={<PermalinkRouter type="container" />}
              />
              <Route
                path="/sample/:id"
                element={<PermalinkRouter type="sample" />}
              />
              <Route
                path="/subsample/:id"
                element={<PermalinkRouter type="subsample" />}
              />
              <Route
                path="/sampletemplate/:id"
                element={<PermalinkRouter type="sampletemplate" />}
              />
              <Route path="/search/*" element={<SearchRouter />} />
              <Route path="/import/*" element={<ImportRouter />} />
              <Route path="*" element={<PageNotFoundScreen />} />
            </Routes>
          </Box>
          <MoveDialog />
        </Alerts>
      </ErrorBoundary>
    </Analytics>
  );
}

export default (observer(InventoryRouter): ComponentType<{||}>);
