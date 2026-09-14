import Box from "@mui/material/Box";
import React from "react";
import { useTranslation } from "react-i18next";
import Header from "../components/Layout/Header";
import Sidebar from "../components/Layout/Sidebar";
import RequestsPage from "./RequestsPage";

/**
 * This is the page where users can see the requests they've made against
 * other users' samples, and the requests other users have made against
 * their own samples.
 */
export default function Requests(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const sidebarId = React.useId();

  return (
    <>
      <title>{t("requestsManagement.browserTitle")}</title>
      <Header sidebarId={sidebarId} />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar id={sidebarId} />
        <RequestsPage />
      </Box>
    </>
  );
}
