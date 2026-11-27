import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import { Navigate, Route, Routes } from "react-router";
import ErrorBoundary from "../components/ErrorBoundary";
import { LandmarksProvider } from "../components/LandmarksContext";
import SkipToContentMenu from "../components/SkipToContentMenu";
import useStores from "../stores/use-stores";
import Analytics from "./Analytics";
import Alerts from "./components/Alerts";
import InitialScreen from "./components/Layout/InitialScreen";
import PageNotFoundScreen from "./components/Layout/PageNotFoundScreen";
import MoveDialog from "./components/MoveToTarget/MoveDialog";
import IdentifiersRouter from "./Identifiers/Router";
import ImportRouter from "./Import/ImportRouter";
import PermalinkRouter from "./PermalinkRouter";
import Requests from "./Requests";
import SearchRouter from "./Search/SearchRouter";

const RedirectToBench = () => {
  const {
    peopleStore: { currentUser },
  } = useStores();
  if (!currentUser) throw new Error("Current user is not available");
  return <Navigate to={`search?parentGlobalId=BE${currentUser.workbenchId}`} replace={true} />;
};

function InventoryRouter(): React.ReactNode {
  const { uiStore, peopleStore } = useStores();
  const { t } = useTranslation("inventory");

  return (
    <Analytics>
      <ErrorBoundary>
        <LandmarksProvider>
          <SkipToContentMenu />
          <Alerts>
            <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
              {peopleStore.inventoryReadOnlyReason && (
                <Alert severity="warning" sx={{ borderRadius: 0 }}>
                  {t("licenseExpired.alert")}
                </Alert>
              )}
              <Box sx={{ flex: 1, minHeight: 0 }}>
                <Routes>
                  <Route path="/" element={!uiStore.isVerySmall ? <RedirectToBench /> : <InitialScreen />} />
                  <Route path="/container/:id" element={<PermalinkRouter type="container" />} />
                  <Route path="/sample/:id" element={<PermalinkRouter type="sample" />} />
                  <Route path="/subsample/:id" element={<PermalinkRouter type="subsample" />} />
                  <Route path="/sampletemplate/:id" element={<PermalinkRouter type="sampletemplate" />} />
                  <Route path="/instrument/:id" element={<PermalinkRouter type="instrument" />} />
                  <Route path="/instrumenttemplate/:id" element={<PermalinkRouter type="instrumenttemplate" />} />
                  <Route path="/search/*" element={<SearchRouter />} />
                  <Route path="/import/*" element={<ImportRouter />} />
                  <Route path="/identifiers/*" element={<IdentifiersRouter />} />
                  <Route path="/requests" element={<Requests />} />
                  <Route path="*" element={<PageNotFoundScreen />} />
                </Routes>
              </Box>
            </Box>
            <MoveDialog />
          </Alerts>
        </LandmarksProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

export default observer(InventoryRouter);
