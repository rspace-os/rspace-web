//@flow

import React, { type Node } from "react";
import { Dialog } from "../../../components/DialogBoundary";

type AddFilestoreDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

export default function AddFilestoreDialog({
  open,
  onClose,
}: AddFilestoreDialogArgs): Node {
  // always have the dialog be open; we'll conditionally render the whole thing
  // fetch the file systems, and provide a menu
  // once one is selected, provide a tree of the file system to pick a folder
  //   let's look at how the move dialog does it?
  // provide a text field for naming the filesystem
  // submit button with validation
  // actually maybe it should be a 3-step wizard?

  const [step, setStep] = React.useState(-1);
  React.useEffect(() => {
    if (!open) setStep(-1);
  }, [open, setStep]);

  return (
    <Dialog open={open} onClose={onClose}>
      Content
    </Dialog>
  );
}
