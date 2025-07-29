import React from "react";
import { Dialog } from "../DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";

interface AboutRSpaceDialogProps {
  open: boolean;
  onClose: () => void;
}

export default function AboutRSpaceDialog({
  open,
  onClose,
}: AboutRSpaceDialogProps): React.ReactElement {
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>About RSpace</DialogTitle>
      <DialogContent>{/* TODO: Add content about RSpace */}</DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
