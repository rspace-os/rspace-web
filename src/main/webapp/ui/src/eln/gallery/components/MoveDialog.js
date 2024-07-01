//@flow

import React, { type Node, type ComponentType } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";

type MoveDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

export default function MoveDialog({ open, onClose }: MoveDialogArgs): Node {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          // do move
        }}
      >
        <DialogTitle>Move</DialogTitle>
        <DialogContent>
          <DialogContentText variant="body2" sx={{ mb: 2 }}>
            Choose a folder, enter a path, or tap the &quot;top-level&quot;
            button.
          </DialogContentText>
        </DialogContent>
      </form>
    </Dialog>
  );
}
