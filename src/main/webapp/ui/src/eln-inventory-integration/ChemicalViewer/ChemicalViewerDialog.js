//@flow

import React, { type Node } from "react";
import { makeStyles } from "tss-react/mui";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import clsx from "clsx";
import { type Attachment } from "../../stores/definitions/Attachment";
import { useIsSingleColumnLayout } from "../../Inventory/components/Layout/Layout2x1";

const useStyles = makeStyles()((theme) => ({
  contentWrapper: {
    overscrollBehavior: "contain",
    WebkitOverflowScrolling: "unset",
  },
  actionsBar: { marginBottom: theme.spacing(1) },
  barWrapper: {
    display: "flex",
    alignSelf: "center",
    width: "95%",
    flexDirection: "column",
    alignItems: "center",
  },
  fullWidth: { width: "100%" },
  bottomSpaced: {
    marginBottom: (isSingleColumnLayout) =>
      isSingleColumnLayout ? theme.spacing(2) : theme.spacing(0.5),
  },
  sideSpaced: { marginRight: theme.spacing(1), marginLeft: theme.spacing(1) },
  spacedBetweenRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  flexEndRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "flex-end",
  },
  warningRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "flex-end",
    alignItems: "center",
    fontSize: "13px",
    marginTop: theme.spacing(0.5),
  },
  warningMessage: { marginRight: "3vw" },
  warningRed: { color: theme.palette.warningRed },
  disableBackground: {
    transition: "all 125ms ease-in-out",
  },
  black: {
    "& input": {
      color: "black",
    },
  },
  textField: {
    marginLeft: theme.spacing(0.5),
    marginRight: theme.spacing(0.5),
    fontWeight: "normal",
  },
  dialogTitle: {
    paddingBottom: theme.spacing(0.5),
  },
  iframe: {
    borderWidth: "0px",
    width: "100%",
    minWidth: "100%",
    height: "98%",
  },
  dialog: {
    height: "100%",
  },
}));

type ViewerArgs = {| attachment: Attachment, open: boolean |};

export default function ChemicalViewerDialog({
  attachment,
  open,
}: ViewerArgs): Node {
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const onClose = () => {
    attachment.revokeChemicalPreview();
  };

  const { classes } = useStyles(isSingleColumnLayout);
  return (
    <Dialog
      open={open}
      fullWidth
      maxWidth="xl"
      fullScreen={isSingleColumnLayout}
      onClose={onClose}
      classes={{
        paper: classes.dialog,
      }}
    >
      <DialogTitle>Chemical Viewer &mdash; File: {attachment.name}</DialogTitle>
      <DialogContent className={classes.contentWrapper}></DialogContent>
      <DialogActions
        className={clsx(classes.barWrapper, classes.disableBackground)}
      >
        <div className={clsx(classes.fullWidth, classes.flexEndRow)}>
          <Button onClick={onClose} className={classes.sideSpaced}>
            Close
          </Button>
        </div>
      </DialogActions>
    </Dialog>
  );
}
