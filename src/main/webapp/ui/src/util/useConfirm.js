//@flow

/*
 * This is a custom react hook for invoking the confirmation dialog, as
 * implemented in `../components/ConfirmContextDialog.js`. It allows any code
 * that is a child component of said dialog to call the following code and
 * cause the dialog to be opened:
 *    const confirm = useConfirm();
 *    const proceed = await confirm(...);
 * `proceed` will then be a boolean value; true if the user tapped ok and false
 * if they tapped cancel. The types of the arguments to be passed to `confirm`
 * are documented below as the type `ConfirmFunction`.
 *
 * Note that unlike the very similar functionality provided by UiStore and
 * the Confirmation dialog in `../components/Confirm.js`, this dialog does not
 * provide any checks for multiple confirmation dialogs being opened at once.
 * If a second dialog were to be triggered whilst the first is still pending
 * user interaction, the second will replace the first and the promise
 * returned by the first call to `confirm` will never resolve. An attempt was
 * made to add the same logic as UiStore's confirmation dialog (i.e. to throw
 * an error when a second dialog is opened) but for an unknown reasons
 * `cc.confirmState` is always null within the scope of the code below.
 */

import { useContext, type Node } from "react";
import ConfirmContext from "../stores/contexts/Confirm";

type ConfirmFunction = (
  title: Node,
  message: Node,
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

export default function useConfirm(): ConfirmFunction {
  const cc = useContext(ConfirmContext);

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
