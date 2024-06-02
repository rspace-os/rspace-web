//@flow

/*
 * A general-purpose confirmation dialog that wraps all child nodes in a
 * context through which they can invoke the dialog.
 *
 * How to use:
 * (1) Somewhere near the root of the application, add this component. It only
 *     needs to be added once to the whole application, and there's no reason
 *     not to place it somewhere high up. Any components that are one of its
 *     descendents will be able to invoke a confirmation dialog.
 * (2) In the child component, call `const confirm = useConfirm();` from
 *     `../util/confirm.js`, and then call `await confirm(...)` when you want
 *     to invoke the confirmation dialog.
 *
 * Note that all of this relies on the context defined in
 * `../stores/contexts/Confirm.js` and the custom react hook defined in
 * `../util/useConfirm`.
 */

import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import {
  default as React,
  type Node,
  type ComponentType,
  useState,
} from "react";
import { observer } from "mobx-react-lite";
import SubmitSpinnerButton from "./SubmitSpinnerButton";
import ConfirmContext, { type ConfirmState } from "../stores/contexts/Confirm";

type ConfirmArgs = {|
  children: Node,
|};

function Confirm({ children }: ConfirmArgs): Node {
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null);

  return (
    <ConfirmContext.Provider value={{ confirmState, setConfirmState }}>
      {children}
      <Dialog
        open={Boolean(confirmState)}
        onClose={() => {
          confirmState?.no();
        }}
      >
        <DialogTitle>{confirmState?.title ?? ""}</DialogTitle>
        <DialogContent style={{ overscrollBehavior: "contain" }}>
          <DialogContentText>{confirmState?.message ?? ""}</DialogContentText>
        </DialogContent>
        <DialogActions>
          {confirmState?.noLabel && (
            <Button
              onClick={() => {
                confirmState.no();
              }}
            >
              {confirmState.noLabel}
            </Button>
          )}
          <SubmitSpinnerButton
            onClick={() => {
              confirmState?.yes();
            }}
            loading={Boolean(confirmState?.confirmationSpinner)}
            disabled={Boolean(confirmState?.confirmationSpinner)}
            label={confirmState?.yesLabel ?? "OK"}
          />
        </DialogActions>
      </Dialog>
    </ConfirmContext.Provider>
  );
}

export default (observer(Confirm): ComponentType<ConfirmArgs>);
