//@flow

/*
 * A general-purpose confirmation dialog, designed for use with UiStore's
 * confirm method, to mimic the browser's default `confirm` function with a
 * promise-based API.
 *
 * This component MUST ONLY access UiStore, remaining independent of all other
 * global state.
 */

import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import useStores from "../stores/use-stores";
import { default as React, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import SubmitSpinnerButton from "./SubmitSpinnerButton";

function Confirm(): Node {
  const [open, setOpen] = React.useState(false);
  const { uiStore } = useStores();

  React.useEffect(() => {
    setOpen(Boolean(uiStore.confirmationDialogProps));
  }, [uiStore.confirmationDialogProps]);

  return (
    <Dialog
      open={open}
      onClose={() => {
        uiStore.confirmationDialogProps?.no();
      }}
    >
      <DialogTitle>{uiStore.confirmationDialogProps?.title ?? ""}</DialogTitle>
      <DialogContent style={{ overscrollBehavior: "contain" }}>
        <DialogContentText>
          {uiStore.confirmationDialogProps?.message ?? ""}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        {uiStore.confirmationDialogProps?.noLabel && (
          <Button
            onClick={() => {
              uiStore.confirmationDialogProps?.no();
            }}
          >
            {uiStore.confirmationDialogProps.noLabel}
          </Button>
        )}
        <SubmitSpinnerButton
          onClick={() => {
            uiStore.confirmationDialogProps?.yes();
          }}
          loading={Boolean(
            uiStore.confirmationDialogProps?.confirmationSpinner
          )}
          disabled={Boolean(
            uiStore.confirmationDialogProps?.confirmationSpinner
          )}
          label={uiStore.confirmationDialogProps?.yesLabel ?? "OK"}
        />
      </DialogActions>
    </Dialog>
  );
}

export default (observer(Confirm): ComponentType<{||}>);
