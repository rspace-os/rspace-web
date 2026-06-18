import Dialog from "@mui/material/Dialog";
import type React from "react";
import useStores from "../../../stores/use-stores";

type ContextDialogArgs = {
  children: React.ReactNode;
  open: boolean;
  onClose: () => void;
  maxWidth?: "xs" | "sm" | "lg";
  fullWidth?: boolean;
};

export default function ContextDialog({
  children,
  open,
  onClose,
  maxWidth,
  fullWidth,
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
      onClose={onClose}
      maxWidth={maxWidth}
      fullWidth={fullWidth}
    >
      {children}
    </Dialog>
  );
}
