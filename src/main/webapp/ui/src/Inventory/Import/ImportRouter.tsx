import Box from "@mui/material/Box";
import React, { useContext, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Navigate } from "react-router";
import { useLandmark } from "@/components/LandmarksContext";
import NavigateContext from "../../stores/contexts/Navigate";
import useStores from "../../stores/use-stores";
import Header from "../components/Layout/Header";
import Sidebar from "../components/Layout/Sidebar";
import Main from "../Main";
import NavigationContext from "./NavigationContext";
import RecordsImport from "./RecordsImport";

export default function ImportRouter(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { importStore } = useStores();
  const { useLocation } = useContext(NavigateContext);
  const location = useLocation();

  /*
   * Whenever the URL path changes, such as because the user tapped a link to
   * navigate around the Import UI, we want to update the state stored by
   * importStore.importData
   */
  useEffect(() => {
    importStore.importData?.updateRecordType(new URLSearchParams(location.search));
  }, [location.search]);

  useEffect(() => {
    document.title = t("import.browserTitle");
  }, []);

  const sidebarId = React.useId();
  const mainContentRef = useLandmark(t("import.landmark"));

  const recordType = importStore.importData?.recordType;
  if (recordType === "SAMPLES" || recordType === "CONTAINERS" || recordType === "SUBSAMPLES") {
    return (
      <NavigationContext>
        <Header sidebarId={sidebarId} />
        <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
          <Sidebar id={sidebarId} />
          <Main aria-label={t("import.landmark")} ref={mainContentRef}>
            <RecordsImport />
          </Main>
        </Box>
      </NavigationContext>
    );
  }
  return <Navigate to="/inventory/pageNotFound" replace={true} />;
}
