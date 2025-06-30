// @flow

import InventoryRouter from "./Inventory/InventoryRouter";
import Confirm from "./components/Confirm";
import StandaloneListOfMaterialsPage from "./eln-inventory-integration/MaterialsListing/StandalonePage";
import CssBaseline from "@mui/material/CssBaseline";
import { observer } from "mobx-react-lite";
import React, { useEffect } from "react";
import { Routes, Route } from "react-router";
import { BrowserRouter } from "react-router-dom";
import RouterNavigationContext from "./Inventory/components/RouterNavigationContext";
import useStores from "./stores/use-stores";

function Router(): React.ReactNode {
  const { authStore } = useStores();

  useEffect(() => {
    if (!authStore.isSynchronizing && !authStore.isAuthenticated) {
      void authStore.authenticate();
    }
  }, []);

  return (
    <BrowserRouter>
      <RouterNavigationContext>
        <CssBaseline />
        <Routes>
          {authStore.isAuthenticated && (
            <>
              <Route path="/inventory/*" element={<InventoryRouter />} />
              <Route
                path="/listOfMaterials/:lomId"
                element={<StandaloneListOfMaterialsPage />}
              />
            </>
          )}
        </Routes>
        <Confirm />
      </RouterNavigationContext>
    </BrowserRouter>
  );
}

export default observer(Router);
