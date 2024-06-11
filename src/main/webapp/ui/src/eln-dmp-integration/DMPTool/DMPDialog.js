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
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import DMPTable from "./DMPTable";
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
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";

const CustomDialog = withStyles<
  {| fullScreen: boolean, ...ElementProps<typeof Dialog> |},
  {| paper?: string |}
>((theme, { fullScreen }) => ({
  paper: {
    overflow: "hidden",

    // this is to avoid intercom help button
    maxHeight: fullScreen ? "unset" : "86vh",

    // this is to ensure the picker has enough height even when list is empty
    minHeight: "86vh",
  },
}))(Dialog);

const useStyles = makeStyles()((theme) => ({
  contentWrapper: {
    overscrollBehavior: "contain",
    WebkitOverflowScrolling: "unset",
  },
  barWrapper: {
    display: "flex",
    alignSelf: "center",
    width: "95%",
    flexDirection: "column",
    alignItems: "center",
  },
  fullWidth: { width: "100%" },
  sideSpaced: { marginRight: theme.spacing(1), marginLeft: theme.spacing(1) },
  flexEndRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "flex-end",
  },
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
      <DialogTitle className={classes.dialogTitle}>
        Data Management Plans (DMPs) from DMPTool
      </DialogTitle>
      <DialogContent className={classes.contentWrapper}>
        <Grid container>
          <Grid item xs={12}>
            <ScopeField getDMPs={getDMPs} />
            <DMPTable
              plans={fetching ? [] : DMPs}
              selectedPlan={selectedPlan}
              setSelectedPlan={setSelectedPlan}
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
      <DialogActions className={clsx(classes.barWrapper)}>
        <div className={clsx(classes.flexEndRow, classes.fullWidth)}>
          <div>
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
          </div>
        </div>
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
  );
}

export default (observer(DMPDialog): ComponentType<DMPDialogArgs>);
