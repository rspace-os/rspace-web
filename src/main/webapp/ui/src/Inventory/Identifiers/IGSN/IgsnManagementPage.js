// @flow

import React, { type Node } from "react";
import Stack from "@mui/material/Stack";
import TitledBox from "../../components/TitledBox";
import Typography from "@mui/material/Typography";
import Link from "@mui/material/Link";
import docLinks from "../../../assets/DocLinks";
import Button from "@mui/material/Button";
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridToolbarExportContainer,
} from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import Checkbox from "@mui/material/Checkbox";
import ChecklistIcon from "@mui/icons-material/Checklist";
import GlobalId from "../../../components/GlobalId";
import { type LinkableRecord } from "../../../stores/definitions/LinkableRecord";
import MenuItem from "@mui/material/MenuItem";
import DropdownButton from "../../../components/DropdownButton";
import Box from "@mui/material/Box";
import Main from "../../Main";

type Igsn = {|
  igsn: string,
  state: string,
  linkedItem: LinkableRecord | null,
|};

function Toolbar(): React.Node {
  const columnMenuRef = React.useRef();
  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <DropdownButton onClick={() => {}} name="State">
        {null}
      </DropdownButton>
      <DropdownButton onClick={() => {}} name="Linked Item">
        {null}
      </DropdownButton>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        variant="outlined"
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
      <GridToolbarExportContainer variant="outlined">
        <MenuItem onClick={() => {}}>Export all rows to CSV</MenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
}

export default function IgsnManagementPage(): Node {
  return (
    <Main sx={{ overflowY: "auto" }}>
      <Stack spacing={2} sx={{ my: 2, mr: 1 }}>
        <TitledBox title="IGSNs" border>
          <Typography>
            The RSpace IGSN ID integration enables researchers to create,
            publish and update IGSN ID metadata all within Inventory. IGSN IDs
            describe material samples and features-of-interest, and are provided
            through the DataCite DOI infrastructure. To learn more,{" "}
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
              You can register and associate an IGSN with an existing item in
              Inventory by selecting <strong>Create a new Identifier</strong>{" "}
              under its <strong>Identifiers</strong> heading.
            </Typography>
            <Typography>
              You can also bulk-register IGSN IDs to be used at a later date,
              such as a field collection trip:
            </Typography>
            <Button variant="contained" color="primary" disableElevation>
              Bulk Register
            </Button>
          </Stack>
        </TitledBox>
        <TitledBox title="Manage IGSNs" border>
          <Stack spacing={2} alignItems="flex-start">
            <Typography>
              To access actions such as editing metadata and publishing, please
              use the <strong>Identifiers</strong> section of the{" "}
              <strong>Linked Item</strong>.
            </Typography>
            <Stack orientation="horizontal" spacing={2}>
              <Button
                variant="contained"
                color="primary"
                disableElevation
                startIcon={<ChecklistIcon />}
                onClick={() => {}}
                aria-label="Actions menu for selected IGSNs"
                aria-haspopup="menu"
                aria-expanded={false}
              >
                Actions
              </Button>
            </Stack>
            <div style={{ width: "100%" }}>
              <DataGrid
                columns={[
                  {
                    field: "checkbox",
                    headerName: "Select",
                    renderCell: () => (
                      <Checkbox
                        color="primary"
                        value={false}
                        checked={false}
                        inputProps={{ "aria-label": "IGSN selection" }}
                        sx={{ p: 0.5 }}
                      />
                    ),
                    hideable: false,
                    width: 70,
                    flex: 0,
                    disableColumnMenu: true,
                    sortable: false,
                  },
                  DataGridColumn.newColumnWithFieldName<_, Igsn>("igsn", {
                    headerName: "IGSN",
                    flex: 1,
                    sortable: false,
                    resizable: true,
                  }),
                  DataGridColumn.newColumnWithFieldName<_, Igsn>("state", {
                    headerName: "State",
                    flex: 1,
                    resizable: true,
                    sortable: false,
                  }),
                  DataGridColumn.newColumnWithFieldName<_, Igsn>("linkedItem", {
                    headerName: "Linked Item",
                    flex: 1,
                    resizable: true,
                    sortable: false,
                    renderCell: ({ row }) =>
                      row.linkedItem && (
                        <GlobalId record={row.linkedItem} onClick={() => {}} />
                      ),
                  }),
                ]}
                rows={[
                  {
                    igsn: "10.5/431235",
                    state: "Draft",
                    linkedItem: null,
                  },
                  {
                    igsn: "10.5/124567",
                    state: "Registered",
                    linkedItem: {
                      id: 79832,
                      globalId: "SA79832",
                      name: "A sample",
                      permalinkURL: "/inventory/samples/79832",
                      iconName: "sample",
                      recordTypeLabel: "Sample",
                      recordType: "SAMPLES",
                    },
                  },
                  {
                    igsn: "10.5/453481",
                    state: "Findable",
                    linkedItem: {
                      id: 92873,
                      globalId: "SA92873",
                      name: "A sample",
                      permalinkURL: "/inventory/samples/92873",
                      iconName: "sample",
                      recordTypeLabel: "Sample",
                      recordType: "SAMPLES",
                    },
                  },
                ]}
                initialState={{
                  columns: {},
                }}
                density="compact"
                disableColumnFilter
                hideFooter
                autoHeight
                slots={{
                  pagination: null,
                  toolbar: Toolbar,
                }}
                localeText={{
                  noRowsLabel: "No IGSNs",
                }}
                loading={false}
                getRowId={(row) => row.igsn}
              />
            </div>
          </Stack>
        </TitledBox>
      </Stack>
    </Main>
  );
}
