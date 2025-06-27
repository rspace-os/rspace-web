import React from "react";
import Dialog from "@mui/material/Dialog";
import AppBar from "../../components/AppBar";

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
  return (
    <Dialog open={open} onClose={onClose} aria-labelledby="test">
      <AppBar variant="dialog" currentPage="PubChem" accessibilityTips={{}} />
      <h2 id="test">test</h2>
    </Dialog>
  );
}
