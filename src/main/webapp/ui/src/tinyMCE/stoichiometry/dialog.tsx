import React from "react";
import { Dialog } from "../../components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import DialogContent from "@mui/material/DialogContent";

export default function StandaloneDialog({
  open,
  onClose,
  editor,
}: {
  open: boolean;
  onClose: () => void;
  editor: unknown;
}): React.ReactNode {
  const titleId = React.useId();

  React.useEffect(() => {
    if (!open) {
      // reset
    }
  }, [open]);

  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby={titleId}
      maxWidth="sm"
      fullWidth
    >
      <AppBar
        variant="dialog"
        currentPage="Stoichiometry"
        accessibilityTips={{}}
      />
      <DialogTitle id={titleId} component="h3">
        Stoichiometry Calculator
      </DialogTitle>
      <DialogContent>TODO</DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
