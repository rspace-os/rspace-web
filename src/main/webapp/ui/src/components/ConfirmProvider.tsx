/*
 * A general-purpose confirmation dialog that wraps all child nodes in a
 * context through which they can invoke the dialog.
 *
 * How to use:
 * (1) Somewhere near the root of the application, add this provider component.
 *     It only needs to be added once to the whole application, and there's no
 *     reason not to place it somewhere high up. Any components that are one of
 *     its descendents will be able to invoke a confirmation dialog.
 * (2) In the child component, call `const confirm = useConfirm();`.
 */

import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { default as React, useState } from "react";
import { observer } from "mobx-react-lite";
import SubmitSpinnerButton from "./SubmitSpinnerButton";

type ConfirmState = {
  title: React.ReactNode;
  message: React.ReactNode;
  yesLabel?: string;
  noLabel?: string;
  yes: () => void;
  no: () => void;
  confirmationSpinner: boolean;
};

type ConfirmContextType = {
  confirmState: ConfirmState | null;
  setConfirmState: React.Dispatch<React.SetStateAction<ConfirmState | null>>;
};

const DEFAULT_CONFIRM_CONTEXT: ConfirmContextType = {
  confirmState: null,
  setConfirmState: () => {},
};

const ConfirmContext: React.Context<ConfirmContextType> = React.createContext(
  DEFAULT_CONFIRM_CONTEXT
);

type ConfirmFunction = (
  title: React.ReactNode,
  message: React.ReactNode,
  yesLabel?: string,
  noLabel?: string,

  /*
   * onConfirm is a callback function that callers of confirm can pass that
   * will perform some asynchronous action. Whilst that action is being
   * performed the confirmation dialog will stay visible and the "OK" button
   * will turn into a spinner. Once the promise returned by onConfirm resolves,
   * the promise returned by confirm also resolves with true.
   */
  onConfirm?: () => Promise<void>
) => Promise<boolean>;

/**
 * This is a custom react hook for invoking the confirmation dialog. It allows
 * any code that is a child component of the provider to call the following
 * code and cause the dialog to be opened:
 *    const confirm = useConfirm();
 *    const proceed = await confirm(...);
 * `proceed` will then be a boolean value; true if the user tapped ok and false
 * if they tapped cancel. The types of the arguments to be passed to `confirm`
 * are documented below as the type `ConfirmFunction`.
 *
 * Note that unlike the very similar functionality provided by UiStore and the
 * Confirmation dialog in `./Confirm.js`, this dialog does not provide any
 * checks for multiple confirmation dialogs being opened at once.  If a second
 * dialog were to be triggered whilst the first is still pending user
 * interaction, the second will replace the first and the promise returned by
 * the first call to `confirm` will never resolve. An attempt was made to add
 * the same logic as UiStore's confirmation dialog (i.e. to throw an error when
 * a second dialog is opened) but for an unknown reasons `cc.confirmState` is
 * always null within the scope of the code below.
 */
export function useConfirm(): ConfirmFunction {
  const cc = React.useContext(ConfirmContext);

  return function confirm(
    title,
    message,
    yesLabel = "OK",
    noLabel = "Cancel",
    onConfirm
  ): Promise<boolean> {
    return new Promise((resolve) => {
      cc.setConfirmState({
        title,
        message,
        yesLabel,
        noLabel,
        yes: () => {
          const returnYes = () => {
            cc.setConfirmState(null);
            resolve(true);
          };
          if (onConfirm) {
            if (cc.confirmState) cc.confirmState.confirmationSpinner = true;
            void onConfirm().then(returnYes);
          } else {
            returnYes();
          }
        },
        no: () => {
          cc.setConfirmState(null);
          resolve(false);
        },
        confirmationSpinner: false,
      });
    });
  };
}

type ConfirmProviderArgs = {
  children: React.ReactNode;
};

function ConfirmProvider({ children }: ConfirmProviderArgs): React.ReactNode {
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

export default observer(ConfirmProvider);
