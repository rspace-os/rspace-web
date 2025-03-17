// @flow

import React, { type Node } from "react";
import Header from "../../components/Layout/Header";
import Sidebar from "../../components/Layout/Sidebar";
import Main from "../../Main";
import Box from "@mui/material/Box";

/**
 * This is the page where researchers can manage their IGSNs.
 */
export default function IgsnManagementPage(): Node {
  const sidebarId = React.useId();
  return (
    <>
      <Header sidebarId={sidebarId} />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar id={sidebarId} />
        <Main>
          <h1>IGSN Management</h1>
        </Main>
      </Box>
    </>
  );
}
