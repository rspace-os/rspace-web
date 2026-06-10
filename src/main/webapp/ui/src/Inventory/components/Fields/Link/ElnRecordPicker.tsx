import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import ElnFolderBrowser from "./ElnFolderBrowser";

export type ElnRecordPickerResult = {
  globalId: string;
  name: string;
  type: string;
};

export interface ElnRecordPickerProps {
  open: boolean;
  onPick: (target: ElnRecordPickerResult) => void;
  onCancel: () => void;
}

/**
 * Dialog for picking an ELN link target (document, notebook or Gallery file) by browsing the
 * workspace/Gallery folder tree. Opened from a "Browse ELN" affordance, so it drops straight into
 * the folder picker with no search/browse mode choice. Clicking a tree node only selects it (and
 * expands folders/notebooks), so notebooks can be browsed into; the selection is confirmed with
 * the explicit Choose button, which reports the target via onPick. Decoupled from TinyMCE and
 * MobX so it can be reused outside the Inventory dialog.
 */
export default function ElnRecordPicker(
  props: ElnRecordPickerProps,
): React.ReactElement {
  const [selection, setSelection] =
    React.useState<ElnRecordPickerResult | null>(null);

  // a reopened dialog must not retain (and offer to Choose) a stale selection
  React.useEffect(() => {
    if (props.open) setSelection(null);
  }, [props.open]);

  return (
    <Dialog
      open={props.open}
      onClose={props.onCancel}
      fullWidth
      maxWidth="sm"
      aria-label="Browse the ELN for a link target"
    >
      <DialogTitle>Browse ELN</DialogTitle>
      <DialogContent dividers sx={{ minHeight: "40vh" }}>
        <ElnFolderBrowser onSelectionChange={setSelection} />
      </DialogContent>
      <DialogActions>
        <Button
          variant="contained"
          color="callToAction"
          disableElevation
          disabled={selection === null}
          onClick={() => {
            if (selection) props.onPick(selection);
          }}
        >
          Choose
        </Button>
        <Button onClick={props.onCancel}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
}
