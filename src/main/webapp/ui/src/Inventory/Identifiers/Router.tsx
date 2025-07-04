import React from "react";
import { Routes, Route } from "react-router";
import { Navigate } from "react-router-dom";
import IGSNs from "./IGSN";
import PageNotFoundScreen from "../components/Layout/PageNotFoundScreen";
import NavigationContext from "./NavigationContext";

/**
 * This component provides the routing logic for the /identifiers section of
 * Inventory, where users may manage the identifiers that they have minted,
 * registered, and/or reserved.
 */
export default function Router(): React.ReactNode {
  /*
   * Currently we only support IGSNs, but we may support more in the future so
   * this configuration leaves open that possibility.
   */
  return (
    <NavigationContext>
      <Routes>
        <Route
          path="/"
          element={<Navigate to="/inventory/identifiers/igsn" />}
        />
        <Route path="igsn" element={<IGSNs />} />
        <Route path="*" element={<PageNotFoundScreen />} />
      </Routes>
    </NavigationContext>
  );
}
