import React from "react";
import Header from "../../components/Layout/Header";
import Sidebar from "../../components/Layout/Sidebar";
import IgsnManagementPage from "./IgsnManagementPage";
import Box from "@mui/material/Box";
import {
  type Identifier,
  IdentifiersRefreshProvider,
} from "../../useIdentifiers";
import RsSet from "../../../util/set";

/**
 * This is the page where researchers can manage their IGSNs.
 */
export default function IGSN(): React.ReactNode {
  const sidebarId = React.useId();
  const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(
    new RsSet([])
  );

  React.useEffect(() => {
    document.title = "Manage IGSNs | RSpace Inventory";
  }, []);

  return (
    <>
      <Header sidebarId={sidebarId} />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar id={sidebarId} />
        <IdentifiersRefreshProvider>
          <IgsnManagementPage
            selectedIgsns={selectedIgsns}
            setSelectedIgsns={setSelectedIgsns}
          />
        </IdentifiersRefreshProvider>
      </Box>
    </>
  );
}
