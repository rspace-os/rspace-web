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
import { toTitleCase, doNotAwait } from "../../../util/Util";
import Menu from "@mui/material/Menu";
import AccentMenuItem from "../../../components/AccentMenuItem";
import { ThemeProvider, useTheme, lighten, darken } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/inventory";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import TextField from "@mui/material/TextField";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import RsSet from "../../../util/set";
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";

function Toolbar({
  state,
  setState,
  isAssociated,
  setIsAssociated,
}: {
  state: "draft" | "findable" | "registered" | null,
  setState: ("draft" | "findable" | "registered" | null) => void,
  isAssociated: boolean | null,
  setIsAssociated: (boolean | null) => void,
}): React.Node {
  const columnMenuRef = React.useRef();
  const apiRef = useGridApiContext();
  const [stateAnchorEl, setStateAnchorEl] = React.useState<HTMLElement | null>(
    null
  );
  const [isAssociatedAnchorEl, setIsAssociatedAnchorEl] =
    React.useState<HTMLElement | null>(null);
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
      <DropdownButton
        onClick={(event) => {
          setIsAssociatedAnchorEl(event.currentTarget);
        }}
        name="Linked Item"
      >
        <Menu
          anchorEl={isAssociatedAnchorEl}
          open={Boolean(isAssociatedAnchorEl)}
          onClose={() => setIsAssociatedAnchorEl(null)}
          MenuListProps={{
            disablePadding: true,
          }}
        >
          <AccentMenuItem
            title="All Identifiers"
            onClick={() => {
              setIsAssociated(null);
              setIsAssociatedAnchorEl(null);
            }}
            current={isAssociated === null}
          />
          <AccentMenuItem
            title="No Linked Item"
            onClick={() => {
              setIsAssociated(false);
              setIsAssociatedAnchorEl(null);
            }}
            current={isAssociated === false}
          />
          <AccentMenuItem
            title="Has Linked Item"
            onClick={() => {
              setIsAssociated(true);
              setIsAssociatedAnchorEl(null);
            }}
            current={isAssociated === true}
          />
        </Menu>
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
  selectedIgsns: RsSet<Identifier>,
  setSelectedIgsns: (RsSet<Identifier>) => void,
|}): Node {
  const [state, setState] = React.useState<
    "draft" | "findable" | "registered" | null
  >(null);
  const [isAssociated, setIsAssociated] = React.useState<boolean | null>(null);
  const { identifiers, loading, bulkRegister, deleteIdentifiers } =
    useIdentifiers({
      state,
      isAssociated,
    });
  const [bulkRegisterDialogOpen, setBulkRegisterDialogOpen] =
    React.useState(false);
  const [numberOfNewIdentifiers, setNumberOfNewIdentifiers] = React.useState(1);
  const [registeringInProgress, setRegisteringInProgress] =
    React.useState(false);
  const [actionsAnchorEl, setActionsAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const theme = useTheme();

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
              <Button
                variant="contained"
                color="primary"
                disableElevation
                onClick={() => setBulkRegisterDialogOpen(true)}
              >
                Bulk Register
              </Button>
              <Dialog
                open={bulkRegisterDialogOpen}
                onClose={() => setBulkRegisterDialogOpen(false)}
              >
                <DialogTitle>Bulk Register IGSNs</DialogTitle>
                <DialogContent>
                  <TextField
                    label="Number of new IGSNs"
                    type="number"
                    inputProps={{ min: 1, max: 100 }}
                    value={numberOfNewIdentifiers}
                    onChange={(e) => setNumberOfNewIdentifiers(e.target.value)}
                    fullWidth
                    sx={{ mt: 1 }}
                    error={
                      numberOfNewIdentifiers < 1 || numberOfNewIdentifiers > 100
                    }
                  />
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setBulkRegisterDialogOpen(false)}>
                    Cancel
                  </Button>
                  <SubmitSpinnerButton
                    onClick={doNotAwait(async () => {
                      setRegisteringInProgress(true);
                      await bulkRegister({ count: numberOfNewIdentifiers });
                      setRegisteringInProgress(false);
                      setBulkRegisterDialogOpen(false);
                    })}
                    disabled={registeringInProgress}
                    loading={registeringInProgress}
                    label="Register"
                  />
                </DialogActions>
              </Dialog>
            </Stack>
          </TitledBox>
          <TitledBox title="Manage IGSNs" border>
            <Stack spacing={0.5} alignItems="flex-start">
              <Typography>
                To access actions such as editing metadata and publishing,
                please use the <strong>Identifiers</strong> section of the{" "}
                <strong>Linked Item</strong>.
              </Typography>
              <Box height={12}></Box>
              <Stack orientation="horizontal">
                <Button
                  variant="contained"
                  color="callToAction"
                  size="small"
                  disableElevation
                  startIcon={<ChecklistIcon />}
                  aria-label="Actions menu for selected IGSNs"
                  aria-haspopup="menu"
                  aria-expanded={false}
                  id="actions-menu"
                  disabled={selectedIgsns.size === 0}
                  onClick={(event) => {
                    setActionsAnchorEl(event.currentTarget);
                  }}
                >
                  Actions
                </Button>
                <Menu
                  anchorEl={actionsAnchorEl}
                  open={Boolean(actionsAnchorEl)}
                  onClose={() => setActionsAnchorEl(null)}
                  MenuListProps={{
                    "aria-labelledby": "actions-menu",
                    disablePadding: true,
                  }}
                >
                  <AccentMenuItem
                    title="Delete"
                    subheader="Does not delete any linked item."
                    onClick={() => {
                      deleteIdentifiers(selectedIgsns);
                      setActionsAnchorEl(null);
                    }}
                    backgroundColor={lighten(theme.palette.error.light, 0.5)}
                    foregroundColor={darken(theme.palette.error.dark, 0.3)}
                    avatar={<DeleteOutlineOutlinedIcon />}
                    compact
                  />
                </Menu>
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
                          value={selectedIgsns.hasWithEq(
                            params.row,
                            (a, b) => a.doi === b.doi
                          )}
                          checked={selectedIgsns.hasWithEq(
                            params.row,
                            (a, b) => a.doi === b.doi
                          )}
                          inputProps={{ "aria-label": "IGSN selection" }}
                          onChange={(e) => {
                            if (e.target.checked) {
                              setSelectedIgsns(
                                selectedIgsns.unionWithEq(
                                  new RsSet([params.row]),
                                  (a, b) => a.doi === b.doi
                                )
                              );
                            } else {
                              setSelectedIgsns(
                                selectedIgsns.subtractWithEq(
                                  new RsSet([params.row]),
                                  (a, b) => a.doi === b.doi
                                )
                              );
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
                        renderCell: ({ row }) => {
                          if (row.associatedGlobalId === null) {
                            return "None";
                          }
                          return (
                            <GlobalId
                              record={
                                new LinkableRecordFromGlobalId(
                                  row.associatedGlobalId
                                )
                              }
                              onClick={() => {}}
                            />
                          );
                        },
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
                  loading={loading}
                  slots={{
                    pagination: null,
                    toolbar: Toolbar,
                  }}
                  slotProps={{
                    toolbar: {
                      state,
                      setState,
                      isAssociated,
                      setIsAssociated,
                    },
                  }}
                  localeText={{
                    noRowsLabel: "No IGSNs",
                  }}
                />
              </div>
            </Stack>
          </TitledBox>
        </Stack>
      </Main>
    </ThemeProvider>
  );
}
