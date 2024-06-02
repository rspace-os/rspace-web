//@flow

import React, { type Node } from "react";
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

type ContextDialogArgs = {|
  classes: { ... },
  children: Node,
  ...$Rest<ContextDialogArgs, {| classes: mixed |}>,
|};

export default function ContextDialog({
  classes: _classes,
  children,
  ...props
}: ContextDialogArgs): Node {
  const { uiStore } = useStores();
  const { classes } = useStyles();

  return (
    <Dialog
      classes={{
        paper: uiStore.isTouchDevice ? classes.dialog : null,
        ..._classes,
      }}
      {...props}
    >
      {children}
    </Dialog>
  );
}
