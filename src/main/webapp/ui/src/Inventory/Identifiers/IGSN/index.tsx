import Box from "@mui/material/Box";
import React from "react";
import { useTranslation } from "react-i18next";
import RsSet from "../../../util/set";
import Header from "../../components/Layout/Header";
import Sidebar from "../../components/Layout/Sidebar";
import { type Identifier, IdentifiersRefreshProvider } from "../../useIdentifiers";
import IgsnManagementPage from "./IgsnManagementPage";

/**
 * This is the page where researchers can manage their IGSNs.
 */
export default function IGSN(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const sidebarId = React.useId();
  const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<Identifier>>(new RsSet([]));

  React.useEffect(() => {
    document.title = t("igsnManagement.browserTitle");
  }, [t]);

  return (
    <>
      <Header sidebarId={sidebarId} />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar id={sidebarId} />
        <IdentifiersRefreshProvider>
          <IgsnManagementPage selectedIgsns={selectedIgsns} setSelectedIgsns={setSelectedIgsns} />
        </IdentifiersRefreshProvider>
      </Box>
    </>
  );
}
