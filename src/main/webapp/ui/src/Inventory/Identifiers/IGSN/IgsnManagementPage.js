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
import ChecklistIcon from "@mui/icons-material/Checklist";
import GlobalId from "../../../components/GlobalId";
import MenuItem from "@mui/material/MenuItem";
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
import DeleteOutlineOutlinedIcon from "@mui/icons-material/DeleteOutlineOutlined";
import PrintDialog from "./PrintDialog";
import PrintIcon from "@mui/icons-material/Print";
import MenuWithSelectedState from "../../../components/MenuWithSelectedState";

function Toolbar({
  state,
  setState,
  isAssociated,
  setIsAssociated,
  setColumnsMenuAnchorEl,
}: {
  state: "draft" | "findable" | "registered" | null,
  setState: ("draft" | "findable" | "registered" | null) => void,
  isAssociated: boolean | null,
  setIsAssociated: (boolean | null) => void,
  setColumnsMenuAnchorEl: (HTMLElement) => void,
}): React.Node {
  const apiRef = useGridApiContext();

  /**
   * The columns menu can be opened by either tapping the "Columns" toolbar
   * button or by tapping the "Manage columns" menu item in each column's menu,
   * logic that is handled my MUI. We provide a custom `anchorEl` so that the
   * menu is positioned beneath the "Columns" toolbar button to be consistent
   * with the other toolbar menus, otherwise is appears far to the left. Rather
   * than having to hook into the logic that triggers the opening of the
   * columns menu in both places, we just set the `anchorEl` pre-emptively.
   */
  const columnMenuRef = React.useRef();
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  const linkedItemStateLabel = (() => {
    if (isAssociated === null) return "All";
    if (isAssociated === true) return "Yes";
    return "No";
  })();

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <MenuWithSelectedState label="State" currentState={state ?? "All"}>
        <AccentMenuItem
          title="All"
          subheader="Show all IGSN IDs"
          onClick={() => {
            setState(null);
          }}
          current={state === null}
        />
        <AccentMenuItem
          title="Draft"
          subheader="A newly created IGSN ID without any public metadata."
          onClick={() => {
            setState("draft");
          }}
          current={state === "draft"}
        />
        <AccentMenuItem
          title="Findable"
          subheader="A published, searchable IGSN ID with a public landing page."
          onClick={() => {
            setState("findable");
          }}
          current={state === "findable"}
        />
        <AccentMenuItem
          title="Registered"
          subheader="An IGSN ID that has been retracted from public access."
          onClick={() => {
            setState("registered");
          }}
          current={state === "registered"}
        />
      </MenuWithSelectedState>
      <MenuWithSelectedState
        label="Linked Item"
        currentState={linkedItemStateLabel}
      >
        <AccentMenuItem
          title="All Identifiers"
          onClick={() => {
            setIsAssociated(null);
          }}
          current={isAssociated === null}
        />
        <AccentMenuItem
          title="No Linked Item"
          onClick={() => {
            setIsAssociated(false);
          }}
          current={isAssociated === false}
        />
        <AccentMenuItem
          title="Has Linked Item"
          onClick={() => {
            setIsAssociated(true);
          }}
          current={isAssociated === true}
        />
      </MenuWithSelectedState>
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

/**
 * The IGSN Management page allows users to view, bulk register, print, and
 * otherwise manage IGSN IDs.
 */
export default function IgsnManagementPage({
  selectedIgsns,
  setSelectedIgsns,
}: {|
  selectedIgsns: $ReadOnlyArray<Identifier>,
  setSelectedIgsns: ($ReadOnlyArray<Identifier>) => void,
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
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<?HTMLElement>(null);
  const [printDialogOpen, setPrintDialogOpen] = React.useState(false);

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Main sx={{ overflowY: "auto" }}>
        <Stack spacing={2} sx={{ my: 2, mr: 1 }}>
          <TitledBox title="IGSN IDs" border>
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
          <TitledBox title="Register IGSN IDs" border>
            <Stack spacing={2} alignItems="flex-start">
              <Typography>
                You can register and associate an IGSN ID with an existing item
                in Inventory by selecting{" "}
                <strong>Create a new Identifier</strong> under its{" "}
                <strong>Identifiers</strong> heading.
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
                <DialogTitle>Bulk Register IGSN IDs</DialogTitle>
                <DialogContent>
                  <TextField
                    label="Number of new IGSN IDs"
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
          <TitledBox title="Manage IGSN IDs" border>
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
                  aria-label="Actions menu for selected IGSN IDs"
                  aria-haspopup="menu"
                  aria-expanded={false}
                  id="actions-menu"
                  disabled={selectedIgsns.length === 0}
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
                    title="Print"
                    subheader="Print barcode labels for selected IGSN IDs."
                    onClick={() => {
                      setPrintDialogOpen(true);
                    }}
                    avatar={<PrintIcon />}
                    compact
                  />
                  <PrintDialog
                    showPrintDialog={printDialogOpen}
                    onClose={() => {
                      setPrintDialogOpen(false);
                      setActionsAnchorEl(null);
                    }}
                    itemsToPrint={selectedIgsns}
                  />
                  <AccentMenuItem
                    title="Delete"
                    subheader="Does not delete any linked item."
                    onClick={() => {
                      void deleteIdentifiers(selectedIgsns).then(() => {
                        setSelectedIgsns([]);
                      });
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
                  checkboxSelection
                  rowSelectionModel={selectedIgsns.map((id) => id.doi)}
                  onRowSelectionModelChange={(
                    ids: $ReadOnlyArray<Identifier["doi"]>
                  ) => {
                    const selectedIdentifiers = identifiers.filter((id) =>
                      ids.includes(id.doi)
                    );
                    setSelectedIgsns(selectedIdentifiers);
                  }}
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
                      setColumnsMenuAnchorEl,
                    },
                    panel: {
                      anchorEl: columnsMenuAnchorEl,
                    },
                  }}
                  localeText={{
                    noRowsLabel: "No IGSN IDs",
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
