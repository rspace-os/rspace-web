import React from "react";
import { Dialog } from "../../components/DialogBoundary";
import AppBar from "../../components/AppBar";
import DialogTitle from "@mui/material/DialogTitle";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import DialogContent from "@mui/material/DialogContent";
import StoichiometryTable from "./table";

export default function StandaloneDialog({
  open,
  onClose,
  chemId,
}: {
  open: boolean;
  onClose: () => void;
  chemId: number | null;
}): React.ReactNode {
  const titleId = React.useId();

  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby={titleId}
      maxWidth="md"
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
      <DialogContent>
        {open && <StoichiometryTable chemId={chemId} />}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
