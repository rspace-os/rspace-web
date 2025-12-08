// @flow

import CssBaseline from "@mui/material/CssBaseline";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect } from "react";
import { Route, Routes } from "react-router";
import { BrowserRouter } from "react-router-dom";
import Confirm from "./components/Confirm";
import ConfirmProvider from "./components/ConfirmProvider";
import StandaloneListOfMaterialsPage from "./eln-inventory-integration/MaterialsListing/StandalonePage";
import RouterNavigationContext from "./Inventory/components/RouterNavigationContext";
import InventoryRouter from "./Inventory/InventoryRouter";
import useStores from "./stores/use-stores";

function Router(): React.ReactNode {
    const { authStore } = useStores();

    useEffect(() => {
        if (!authStore.isSynchronizing && !authStore.isAuthenticated) {
            void authStore.authenticate();
        }
    }, [authStore.authenticate, authStore.isAuthenticated, authStore.isSynchronizing]);

    return (
        <BrowserRouter>
            <RouterNavigationContext>
                <CssBaseline />
                <ConfirmProvider>
                    <Routes>
                        {authStore.isAuthenticated && (
                            <>
                                <Route path="/inventory/*" element={<InventoryRouter />} />
                                <Route path="/listOfMaterials/:lomId" element={<StandaloneListOfMaterialsPage />} />
                            </>
                        )}
                    </Routes>
                </ConfirmProvider>
                <Confirm />
            </RouterNavigationContext>
        </BrowserRouter>
    );
}

export default observer(Router);
