import React from "react";
import Dialog from "@mui/material/Dialog";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";

/**
 * This dialog is opened by the TinyMCE plugin, allowing the users to browse
 * chemistry files on PubChem and importing into their document.
 */
export default function ImportDialog({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}): React.ReactNode {
  const titleId = React.useId();
  return (
    <Dialog open={open} onClose={onClose} aria-labelledby={titleId}>
      <AppBar variant="dialog" currentPage="PubChem" accessibilityTips={{}} />
      <DialogTitle id={titleId} component="h3">
        Import from PubChem
      </DialogTitle>
      <DialogActions>
        <Button onClick={() => onClose()}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
}
