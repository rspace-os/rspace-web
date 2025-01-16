// @flow

import React, { type Node, useEffect, useContext } from "react";
import RecordsImport from "./RecordsImport";
import { Navigate } from "react-router-dom";
import NavigateContext from "../../stores/contexts/Navigate";
import Header from "../components/Layout/Header";
import Sidebar from "../components/Layout/Sidebar";
import useStores from "../../stores/use-stores";
import NavigationContext from "./NavigationContext";
import Main from "../Main";
import Box from "@mui/material/Box";

export default function ImportRouter(): Node {
  const { uiStore, importStore } = useStores();
  const { useLocation } = useContext(NavigateContext);
  const location = useLocation();

  /*
   * Whenever the URL path changes, such as because the user tapped a link to
   * navigate around the Import UI, we want to update the state stored by
   * importStore.importData
   */
  useEffect(() => {
    importStore.importData?.updateRecordType(
      new URLSearchParams(location.search)
    );
  }, [location.search]);

  const sidebarId = React.useId();

  const recordType = importStore.importData?.recordType;
  if (
    recordType === "SAMPLES" ||
    recordType === "CONTAINERS" ||
    recordType === "SUBSAMPLES"
  ) {
    return (
      <NavigationContext>
        <Header sidebarId={sidebarId} />
        <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
          <Sidebar id={sidebarId} />
          <Main>
            <RecordsImport />
          </Main>
        </Box>
      </NavigationContext>
    );
  }
  return <Navigate to="/inventory/pageNotFound" replace={true} />;
}
