// @flow

import React, {
  type Node,
  useState,
  useEffect,
  useContext,
  useRef,
  type ComponentType,
  type ElementProps,
} from "react";
import Button from "@mui/material/Button";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import clsx from "clsx";
import WarningIcon from "@mui/icons-material/Warning";
import Typography from "@mui/material/Typography";
import axios from "axios";
import { type UseState } from "../../util/types";
import ScopeField, { type Scope } from "./ScopeField";
import { Optional } from "../../util/optional";
import useViewportDimensions from "../../util/useViewportDimensions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import Portal from "@mui/material/Portal";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";
import Link from "@mui/material/Link";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import docLinks from "../../assets/DocLinks";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import AccessibilityTips from "../../components/AccessibilityTips";
import { DataGrid } from "@mui/x-data-grid";
import { DataGridColumn } from "../../util/table";
import Radio from "@mui/material/Radio";
import DOMPurify from "dompurify";

const COLOR = {
  main: {
    hue: 208,
    saturation: 46,
    lightness: 70,
  },
  darker: {
    hue: 208,
    saturation: 93,
    lightness: 33,
  },
  contrastText: {
    hue: 208,
    saturation: 35,
    lightness: 26,
  },
  background: {
    hue: 208,
    saturation: 25,
    lightness: 71,
  },
  backgroundContrastText: {
    hue: 208,
    saturation: 11,
    lightness: 24,
  },
};

const CustomDialog = withStyles<
  {| fullScreen: boolean, ...ElementProps<typeof Dialog> |},
  {| paper?: string |}
>((theme, { fullScreen }) => ({
  paper: {
    overflow: "hidden",
    margin: fullScreen ? 0 : theme.spacing(2.625),
    maxHeight: "unset",
    minHeight: "unset",

    // this is so that the hights of the dialog's content of constrained and scrollbars appear
    // 24px margin above and below, 3px border above and below
    height: fullScreen ? "100%" : "calc(100% - 48px)",
  },
}))(Dialog);

const useStyles = makeStyles()((theme) => ({
  contentWrapper: {
    overscrollBehavior: "contain",
    WebkitOverflowScrolling: "unset",
  },
  fullWidth: { width: "100%" },
  sideSpaced: { marginRight: theme.spacing(1), marginLeft: theme.spacing(1) },
  warningRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "flex-end",
    alignItems: "center",
    fontSize: "13px",
    marginTop: theme.spacing(0.5),
  },
  warningMessage: { marginRight: "3vw" },
  warningRed: { color: theme.palette.warningRed },
  dialogTitle: {
    paddingBottom: theme.spacing(0.5),
  },
}));

export type Plan = {
  id: number,
  title: string,
  description: string,
  modified: string,
  created: string,
};

const WarningBar = observer(() => {
  const { classes } = useStyles();
  return (
    <div
      className={clsx(
        classes.warningRow,
        classes.fullWidth,
        classes.warningRed
      )}
    >
      <WarningIcon />
      <span className={classes.warningMessage}>Warning text</span>
    </div>
  );
});

function DMPDialogContent({ setOpen }: { setOpen: (boolean) => void }): Node {
  const { addAlert } = useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();

  const [DMPs, setDMPs] = useState(([]: Array<Plan>));
  const [selectedPlan, setSelectedPlan]: UseState<?Plan> = useState();

  const [fetching, setFetching] = useState(false);
  const [errorFetching, setErrorFetching] = useState(null);
  const fetchingId = useRef(0);

  const [importing, setImporting] = useState(false);

  const getDMPs = (scope: Scope) => {
    setErrorFetching(null);
    setFetching(true);
    const promise = axios.get<{
      success: true,
      data: { items: Array<{ dmp: Plan }> },
    }>(`/apps/dmptool/plans?scope=${scope}`);
    const thisId = fetchingId.current;
    fetchingId.current += 1;
    promise
      .then((r) => {
        if (thisId === fetchingId.current - 1) {
          if (r.data.success) {
            setDMPs(r.data.data.items.map((item) => item.dmp));
          } else {
            addAlert(
              mkAlert({
                title: "Fetch failed.",
                message: r.data.error?.errorMessages[0] ?? "Could not get DMPs",
                variant: "error",
                isInfinite: false,
              })
            );
          }
        } else {
          console.info(
            "The response from this request is being discarded because a different listing of plans has been requested whilst this network call was in flight."
          );
        }
      })
      .catch((e) => {
        console.error("Could not get DMPs for scope", e);
        setErrorFetching("Could not get DMPs: " + e.message);
        if (thisId === fetchingId) {
          setFetching(false);
        }
      })
      .finally(() => {
        setFetching(false);
      });
  };

  useEffect(() => {
    if (open) getDMPs("MINE");
  }, [open]);

  const handleImport = async () => {
    try {
      setImporting(true);
      const selectedPlanId = Number(selectedPlan?.id);
      if (selectedPlan) {
        await axios
          .post<{}, { success: true }>(
            `/apps/dmptool/pdfById/${selectedPlanId}`,
            {}
          )
          .then((r) => {
            addAlert(
              mkAlert(
                r.data.success
                  ? {
                      title: "Success.",
                      message: `DMP ${selectedPlanId} was successfully imported.`,
                      variant: "success",
                    }
                  : {
                      title: "Import failed.",
                      message:
                        r.data.error.errorMessages[0] || "Could not import DMP",
                      variant: "error",
                    }
              )
            );
            setSelectedPlan(null);
          });
      }
    } catch (e) {
      console.error("Could not import DMP", e);
    } finally {
      setImporting(false);
    }
  };

  const { classes } = useStyles();

  const showWarning = false; // intentionally left, may be used later

  function statusText() {
    if (errorFetching) return Optional.present(errorFetching);
    if (fetching) return Optional.present("Fetching DMPs...");
    if (DMPs.length === 0) return Optional.present("No items to display");
    return Optional.empty<string>();
  }

  return (
    <>
      <AppBar position="relative" open={true}>
        <Toolbar variant="dense">
          <Typography variant="h6" noWrap component="h2">
            DMPTool
          </Typography>
          <Box flexGrow={1}></Box>
          <Box ml={1}>
            <AccessibilityTips supportsHighContrastMode elementType="dialog" />
          </Box>
          <Box ml={1} sx={{ transform: "translateY(2px)" }}>
            <HelpLinkIcon title="DMPTool help" link={docLinks.dmptool} />
          </Box>
        </Toolbar>
      </AppBar>
      <DialogContent className={classes.contentWrapper}>
        <Grid
          container
          direction="column"
          spacing={2}
          flexWrap="nowrap"
          // this is so that just the table is scrollable
          height="calc(100% + 16px)"
        >
          <Grid item>
            <Typography variant="h3">Import a DMP into the Gallery</Typography>
          </Grid>
          <Grid item>
            <Typography variant="body2">
              Importing a DMP will make it available to view and reference
              within RSpace.
            </Typography>
            <Typography variant="body2">
              See <Link href="https://dmptool.org">dmptool.org</Link> and our{" "}
              <Link href={docLinks.dmptool}>DMPTool integration docs</Link> for
              more.
            </Typography>
          </Grid>
          <Grid item xs={12}>
            <ScopeField getDMPs={getDMPs} />
            <DataGrid
              columns={[
                {
                  field: "radio",
                  headerName: "Select",
                  renderCell: (params: { row: Plan, ... }) => (
                    <Radio
                      color="primary"
                      value={selectedPlan?.id === params.row.id}
                      checked={selectedPlan?.id === params.row.id}
                      inputProps={{ "aria-label": "Plan selection" }}
                    />
                  ),
                  hideable: false,
                  width: 60,
                  flex: 0,
                  disableColumnMenu: true,
                  sortable: false,
                },
                DataGridColumn.newColumnWithFieldName<Plan, _>("title", {
                  headerName: "Title",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName("id", {
                  headerName: "Id",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName("description", {
                  renderCell: (params: { row: Plan, ... }) => {
                    const sanitized = DOMPurify.sanitize(
                      params.row.description
                    );
                    return (
                      <span
                        dangerouslySetInnerHTML={{
                          __html: `${sanitized.substring(0, 200)} ${
                            sanitized.length > 200 ? "..." : ""
                          }`,
                        }}
                      ></span>
                    );
                  },
                  headerName: "Description",
                  display: "flex",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithValueGetter(
                  "created",
                  (params: { row: Plan, ... }) =>
                    new Date(params.row.created).toLocaleString(),
                  {
                    headerName: "Created At",
                    flex: 1,
                    sortable: false,
                  }
                ),
                DataGridColumn.newColumnWithValueGetter(
                  "modified",
                  (params: { row: Plan, ... }) =>
                    new Date(params.row.modified).toLocaleString(),
                  {
                    headerName: "Modified At",
                    flex: 1,
                    sortable: false,
                  }
                ),
              ]}
              rows={fetching ? [] : DMPs}
              initialState={{
                columns: {
                  columnVisibilityModel: {
                    id: !isViewportSmall,
                    description: false,
                    created: false,
                    modified: false,
                  },
                },
              }}
              density="compact"
              disableColumnFilter
              hideFooter
              slots={{
                pagination: null,
              }}
              localeText={{
                noRowsLabel: "No DMPs",
              }}
              loading={fetching}
              getRowId={(row) => row.id}
              onRowSelectionModelChange={(
                newSelection: $ReadOnlyArray<Plan["id"]>
              ) => {
                if (newSelection[0]) {
                  setSelectedPlan(DMPs.find((d) => d.id === newSelection[0]));
                }
              }}
              getRowHeight={() => "auto"}
            />
            {statusText()
              .map((sText) => (
                <Typography
                  key={null}
                  component="div"
                  variant="body2"
                  color="textPrimary"
                  align="center"
                >
                  {sText}
                </Typography>
              ))
              .orElse(null)}
          </Grid>
        </Grid>
      </DialogContent>
      {showWarning && <WarningBar />}
      <DialogActions>
        <Grid container direction="row" spacing={1}>
          <Grid item sx={{ ml: "auto" }}>
            <Stack direction="row" spacing={1}>
              <Button
                className={classes.sideSpaced}
                onClick={() => setOpen(false)}
                disabled={importing}
              >
                {selectedPlan ? "Cancel" : "Close"}
              </Button>
              <ValidatingSubmitButton
                onClick={() => {
                  void handleImport();
                }}
                validationResult={
                  !selectedPlan?.id ? IsInvalid("No DMP selected.") : IsValid()
                }
                loading={importing}
              >
                Import
              </ValidatingSubmitButton>
            </Stack>
          </Grid>
        </Grid>
      </DialogActions>
    </>
  );
}

type DMPDialogArgs = {|
  open: boolean,
  setOpen: (boolean) => void,
|};

/*
 * This simple function just for the outer-most components is so that the
 * content of the dialog can use the Alerts context
 *
 * A11y: note that tabbing through this dialog is not possible because the
 * custom tabbing behaviour of the Gallery page takes control of the tab key
 * events away from the React+MUI tech stack. See ../../../../scripts/global.js
 */
function DMPDialog({ open, setOpen }: DMPDialogArgs): Node {
  const { isViewportSmall } = useViewportDimensions();

  /*
   * We use DialogBoundary to wrap the Dialog so that Alerts can be shown atop
   * the dialog whilst keeping them accessible to screen readers. We then have
   * to manually add Portal back (Dialogs normally include a Portal) so that
   * the Dialog isn't rendered inside the Menu where it will not be seen once
   * the Menu is closed.
   */

  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <Portal>
        <DialogBoundary>
          <CustomDialog
            onClose={() => {
              setOpen(false);
            }}
            open={open}
            maxWidth="lg"
            fullWidth
            fullScreen={isViewportSmall}
          >
            <DMPDialogContent setOpen={setOpen} />
          </CustomDialog>
        </DialogBoundary>
      </Portal>
    </ThemeProvider>
  );
}

export default (observer(DMPDialog): ComponentType<DMPDialogArgs>);
