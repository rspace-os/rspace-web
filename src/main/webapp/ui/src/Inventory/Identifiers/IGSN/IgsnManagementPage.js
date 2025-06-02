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
import {
  useIdentifiers,
  useIdentifiersListing,
  useIdentifiersRefresh,
  type Identifier,
} from "../../useIdentifiers";
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
import IgsnTable from "./IgsnTable";
import RsSet from "../../../util/set";

/**
 * The IGSN Management page allows users to view, bulk register, print, and
 * otherwise manage IGSN IDs.
 */
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
  const { identifiers, loading } = useIdentifiersListing({
    state,
    isAssociated,
  });
  const { refreshListing } = useIdentifiersRefresh();
  const { bulkRegister, deleteIdentifiers } = useIdentifiers();
  const [bulkRegisterDialogOpen, setBulkRegisterDialogOpen] =
    React.useState(false);
  const [numberOfNewIdentifiers, setNumberOfNewIdentifiers] = React.useState(1);
  const [registeringInProgress, setRegisteringInProgress] =
    React.useState(false);
  const [actionsAnchorEl, setActionsAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const theme = useTheme();
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
                  <Stack spacing={3}>
                    <Typography>
                      IGSN IDs in Draft state will be created.
                    </Typography>
                    <TextField
                      label="Number of new IGSN IDs"
                      type="number"
                      inputProps={{ min: 1, max: 100 }}
                      value={numberOfNewIdentifiers}
                      onChange={(e) =>
                        setNumberOfNewIdentifiers(e.target.value)
                      }
                      fullWidth
                      sx={{ mt: 1 }}
                      error={
                        numberOfNewIdentifiers < 1 ||
                        numberOfNewIdentifiers > 100
                      }
                    />
                  </Stack>
                </DialogContent>
                <DialogActions>
                  <Button onClick={() => setBulkRegisterDialogOpen(false)}>
                    Cancel
                  </Button>
                  <SubmitSpinnerButton
                    onClick={doNotAwait(async () => {
                      setRegisteringInProgress(true);
                      try {
                        await bulkRegister({ count: numberOfNewIdentifiers });
                        if (refreshListing) void refreshListing();
                        setBulkRegisterDialogOpen(false);
                      } finally {
                        setRegisteringInProgress(false);
                      }
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
                    itemsToPrint={[...selectedIgsns]}
                  />
                  <AccentMenuItem
                    title="Delete"
                    subheader="Does not delete any linked item."
                    onClick={() => {
                      void deleteIdentifiers(selectedIgsns).then(() => {
                        if (refreshListing) void refreshListing();
                        setSelectedIgsns(new RsSet([]));
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
                <IgsnTable
                  selectedIgsns={selectedIgsns}
                  setSelectedIgsns={setSelectedIgsns}
                />
              </div>
            </Stack>
          </TitledBox>
        </Stack>
      </Main>
    </ThemeProvider>
  );
}
