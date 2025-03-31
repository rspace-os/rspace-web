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
  useGridApiContext,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridToolbarExportContainer,
} from "@mui/x-data-grid";
import { DataGridColumn } from "../../../util/table";
import Checkbox from "@mui/material/Checkbox";
import ChecklistIcon from "@mui/icons-material/Checklist";
import GlobalId from "../../../components/GlobalId";
import MenuItem from "@mui/material/MenuItem";
import DropdownButton from "../../../components/DropdownButton";
import Box from "@mui/material/Box";
import Main from "../../Main";
import { useIdentifiers, type Identifier } from "../../useIdentifiers";
import LinkableRecordFromGlobalId from "../../../stores/models/LinkableRecordFromGlobalId";
import { toTitleCase } from "../../../util/Util";
import Menu from "@mui/material/Menu";
import AccentMenuItem from "../../../components/AccentMenuItem";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";

function Toolbar({
  state,
  setState,
}: {
  state: "draft" | "findable" | "registered" | null,
  setState: ("draft" | "findable" | "registered" | null) => void,
}): React.Node {
  const columnMenuRef = React.useRef();
  const apiRef = useGridApiContext();
  const [stateAnchorEl, setStateAnchorEl] = React.useState<HTMLElement | null>(null);
  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <DropdownButton
        onClick={(event) => {
          setStateAnchorEl(event.currentTarget);
        }}
        name="State"
      >
        <Menu
          anchorEl={stateAnchorEl}
          open={Boolean(stateAnchorEl)}
          MenuListProps={{
            disablePadding: true,
          }}
          onClose={() => {
            setStateAnchorEl(null);
          }}
        >
          <AccentMenuItem
            title="All"
            subheader="Show all IGSN IDs"
            onClick={() => {
              setState(null);
              setStateAnchorEl(null);
            }}
            current={state === null}
          />
          <AccentMenuItem
            title="Draft"
            subheader="A newly created IGSN ID without any public metadata."
            onClick={() => {
              setState("draft");
              setStateAnchorEl(null);
            }}
            current={state === "draft"}
          />
          <AccentMenuItem
            title="Findable"
            subheader="A published, searchable IGSN ID with a public landing page."
            onClick={() => {
              setState("findable");
              setStateAnchorEl(null);
            }}
            current={state === "findable"}
          />
          <AccentMenuItem
            title="Registered"
            subheader="An IGSN ID that has been retracted from public access."
            onClick={() => {
              setState("registered");
              setStateAnchorEl(null);
            }}
            current={state === "registered"}
          />
        </Menu>
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
        <MenuItem
          onClick={() => {
            apiRef.current?.exportDataAsCsv({
              allColumns: true,
            });
          }}
        >
          Export to CSV
        </MenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
}

export default function IgsnManagementPage({
  selectedIgsns,
  setSelectedIgsns,
}: {|
  selectedIgsns: Set<string>,
  setSelectedIgsns: (Set<string>) => void,
|}): Node {
  const [state, setState] = React.useState<
    "draft" | "findable" | "registered" | null
  >(null);
  const { identifiers } = useIdentifiers({ state });

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Main sx={{ overflowY: "auto" }}>
        <Stack spacing={2} sx={{ my: 2, mr: 1 }}>
          <TitledBox title="IGSNs" border>
            <Typography>
              The RSpace IGSN ID integration enables researchers to create,
              publish and update IGSN ID metadata all within Inventory. IGSN IDs
              describe material samples and features-of-interest, and are
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
                To access actions such as editing metadata and publishing,
                please use the <strong>Identifiers</strong> section of the{" "}
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
                      renderCell: (params: { row: Identifier, ... }) => (
                        <Checkbox
                          color="primary"
                          value={selectedIgsns.has(params.row.doi)}
                          checked={selectedIgsns.has(params.row.doi)}
                          inputProps={{ "aria-label": "IGSN selection" }}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedIgsns(
                                new Set(selectedIgsns).add(params.row.doi)
                              );
                            } else {
                              const newSet = new Set(selectedIgsns);
                              newSet.delete(params.row.doi);
                              setSelectedIgsns(newSet);
                            }
                          }}
                          sx={{ p: 0.5 }}
                        />
                      ),
                      hideable: false,
                      width: 70,
                      flex: 0,
                      disableColumnMenu: true,
                      sortable: false,
                      disableExport: true,
                    },
                    DataGridColumn.newColumnWithFieldName<_, Identifier>(
                      "doi",
                      {
                        headerName: "DOI",
                        flex: 1,
                        sortable: false,
                        resizable: true,
                      }
                    ),
                    DataGridColumn.newColumnWithFieldName<_, Identifier>(
                      "state",
                      {
                        headerName: "State",
                        flex: 1,
                        resizable: true,
                        sortable: false,
                        renderCell: ({ row }) => toTitleCase(row.state),
                      }
                    ),
                    DataGridColumn.newColumnWithFieldName<_, Identifier>(
                      "associatedGlobalId",
                      {
                        headerName: "Linked Item",
                        flex: 1,
                        resizable: true,
                        sortable: false,
                        renderCell: ({ row }) => (
                          <GlobalId
                            record={
                              new LinkableRecordFromGlobalId(
                                row.associatedGlobalId
                              )
                            }
                            onClick={() => {}}
                          />
                        ),
                      }
                    ),
                  ]}
                  rows={identifiers}
                  getRowId={(row) => row.doi}
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
                  slotProps={{
                    toolbar: {
                      state,
                      setState,
                    },
                  }}
                  localeText={{
                    noRowsLabel: "No IGSNs",
                  }}
                  loading={false}
                />
              </div>
            </Stack>
          </TitledBox>
        </Stack>
      </Main>
    </ThemeProvider>
  );
}
