//@flow

import React, { type Context, type Node } from "react";

export type ConfirmState = {|
  title: Node,
  message: Node,
  yesLabel?: string,
  noLabel?: string,
  yes: () => void,
  no: () => void,
  confirmationSpinner: boolean,
|};

/*
 * When `confirmState` is null the dialog is not shown. As such, calling
 * `setConfirmState` with null closes the dialog.
 */
type ConfirmContextType = {|
  confirmState: ConfirmState | null,
  setConfirmState: (ConfirmState | null) => void,
|};

const DEFAULT_CONFIRM_CONTEXT: ConfirmContextType = {
  confirmState: null,
  setConfirmState: () => {},
};

const ConfirmContext: Context<ConfirmContextType> = React.createContext(
  DEFAULT_CONFIRM_CONTEXT
);

export default ConfirmContext;
