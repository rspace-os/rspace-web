// @flow

import React, { type Node } from "react";
import Header from "../../components/Layout/Header";
import Sidebar from "../../components/Layout/Sidebar";
import Main from "../../Main";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import TitledBox from "../../components/TitledBox";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import docLinks from "../../../assets/DocLinks";
import Button from "@mui/material/Button";

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
          <Stack spacing={2} sx={{ mt: 2, mr: 1 }}>
            <TitledBox title="IGSNs" border>
              <Typography>
                The RSpace IGSN ID integration enables researchers to create,
                publish and update IGSN ID metadata all within Inventory. IGSN
                IDs describe material samples and features-of-interest, and are
                provided through the DataCite DOI infrastructure. To learn more,{" "}
                <Link
                  target="_blank"
                  rel="noreferrer"
                  href={docLinks.IGSNIdentifiers}
                >
                  see the IGSN ID documentation
                </Link>
                .
              </Typography>
            </TitledBox>
            <TitledBox title="Register IGSNs" border>
              <Stack spacing={2} alignItems="flex-start">
                <Typography>
                  You can register and associate an IGSN with an existing item
                  in Inventory by selecting{" "}
                  <strong>Create a new Identifier</strong> under its{" "}
                  <strong>Identifiers</strong> heading.
                </Typography>
                <Typography>
                  You can also bulk-register IGSN IDs to be used at a later
                  date, such as a field collection trip:
                </Typography>
                <Button variant="contained" color="primary" disableElevation>
                  Bulk Register
                </Button>
              </Stack>
            </TitledBox>
          </Stack>
        </Main>
      </Box>
    </>
  );
}
