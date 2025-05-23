import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import React from "react";
import useStores from "../../../stores/use-stores";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import clsx from "clsx";
import { useIsSingleColumnLayout } from "./Layout2x1";

// Full height and full width
const AlmostFullscreenDialog = withStyles<
  { children: React.ReactNode } & React.ComponentProps<typeof Dialog>,
  { root: string; paper: string }
>(() => ({
  root: {
    userSelect: "none",
  },
  paper: {
    height: "100%",
  },
}))(({ children, ...props }) => (
  <Dialog {...props} maxWidth="xl" fullWidth>
    {children}
  </Dialog>
));

const useStyles = makeStyles<{ isSingleColumnLayout: boolean }>()(
  (theme, { isSingleColumnLayout }) => ({
    content: {
      padding: "0 !important",
      overflowX: "hidden",
      /*
       * This is so that absolutely positioned elments inside the dialog
       * automatically move as the dialog content is scrolled
       */
      position: "relative",
    },
    container: {
      justifyContent: "space-between",
    },
    leftPane: {
      maxWidth: `${((isSingleColumnLayout ? 12 : 5) / 12) * 100}%`,
      flexBasis: `${((isSingleColumnLayout ? 12 : 5) / 12) * 100}%`,
    },
    rightPane: {
      maxWidth: `${((isSingleColumnLayout ? 12 : 7) / 12) * 100}%`,
      flexBasis: `${((isSingleColumnLayout ? 12 : 7) / 12) * 100}%`,
    },
    hide: {
      display: "none",
    },
  })
);

type Layout2x1DialogArgs = {
  open: boolean;
  onClose: () => void;
  colLeft: React.ReactNode;
  colRight: React.ReactNode;
  actions: React.ReactNode;
  dialogTitle: React.ReactNode;
};

function Layout2x1Dialog(props: Layout2x1DialogArgs): React.ReactNode {
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { classes } = useStyles({
    isSingleColumnLayout,
  });

  return (
    <AlmostFullscreenDialog
      open={props.open}
      onClose={props.onClose}
      fullScreen={uiStore.isVerySmall}
    >
      {props.dialogTitle !== null &&
        typeof props.dialogTitle !== "undefined" && (
          <DialogTitle
            component="h2"
            variant="h5"
            sx={{ px: 2, pt: 1.5, pb: 1 }}
          >
            {props.dialogTitle}
          </DialogTitle>
        )}
      <DialogContent dividers className={classes.content}>
        <Grid container className={classes.container}>
          <Grid
            item
            className={clsx(
              classes.leftPane,
              isSingleColumnLayout &&
                !(uiStore.dialogVisiblePanel === "left") &&
                classes.hide
            )}
          >
            {props.colLeft}
          </Grid>
          <Grid
            item
            className={clsx(
              classes.rightPane,
              isSingleColumnLayout &&
                !(uiStore.dialogVisiblePanel === "right") &&
                classes.hide
            )}
          >
            {props.colRight}
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>{props.actions}</DialogActions>
    </AlmostFullscreenDialog>
  );
}

export default observer(Layout2x1Dialog);
