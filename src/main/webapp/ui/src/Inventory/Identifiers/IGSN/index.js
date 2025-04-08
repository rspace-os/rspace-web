// @flow

import React, { type Node } from "react";
import Header from "../../components/Layout/Header";
import Sidebar from "../../components/Layout/Sidebar";
import IgsnManagementPage from "./IgsnManagementPage";
import Box from "@mui/material/Box";
import RsSet from "../../../util/set";
import { type Identifier } from "../../useIdentifiers";

/**
 * This is the page where researchers can manage their IGSNs.
 */
export default function IGSN(): Node {
  const sidebarId = React.useId();
  const [selectedIgsns, setSelectedIgsns] = React.useState(new RsSet<Identifier>());
  return (
    <>
      <Header sidebarId={sidebarId} />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Sidebar id={sidebarId} />
        <IgsnManagementPage selectedIgsns={selectedIgsns} setSelectedIgsns={setSelectedIgsns}/>
      </Box>
    </>
  );
}
