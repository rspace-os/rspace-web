import React from "react";
import { makeStyles } from "tss-react/mui";
import Dialog from "@mui/material/Dialog";
import useStores from "../../../stores/use-stores";

const useStyles = makeStyles()(() => ({
  dialog: {
    position: "absolute",
    top: 0,
    left: 0,
  },
}));

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
  const { classes } = useStyles();

  return (
    <Dialog
      classes={{
        paper: uiStore.isTouchDevice ? classes.dialog : undefined,
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
