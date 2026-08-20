import Dialog from "@mui/material/Dialog";
import type React from "react";
import useStores from "../../../stores/use-stores";

type ContextDialogArgs = {
  children: React.ReactNode;
  open: boolean;
  onClose: () => void;
  maxWidth?: "xs" | "sm" | "lg";
  fullWidth?: boolean;
  /** When set, a click on the backdrop does not dismiss the dialog (Escape still closes it). Use for
   *  a multi-step flow where an accidental outside click should not discard the user's progress. */
  disableBackdropClick?: boolean;
};

export default function ContextDialog({
  children,
  open,
  onClose,
  maxWidth,
  fullWidth,
  disableBackdropClick = false,
}: ContextDialogArgs): React.ReactNode {
  const { uiStore } = useStores();

  return (
    <Dialog
      slotProps={{
        paper: {
          sx: uiStore.isTouchDevice
            ? {
                position: "absolute",
                top: 0,
                left: 0,
              }
            : undefined,
        },
      }}
      open={open}
      onClose={(_event, reason) => {
        if (disableBackdropClick && reason === "backdropClick") return;
        onClose();
      }}
      maxWidth={maxWidth}
      fullWidth={fullWidth}
    >
      {children}
    </Dialog>
  );
}
