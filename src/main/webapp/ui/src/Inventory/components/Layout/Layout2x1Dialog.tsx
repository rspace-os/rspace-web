import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";
import useStores from "../../../stores/use-stores";
import { useIsSingleColumnLayout } from "./Layout2x1";

// Full height and full width
function AlmostFullscreenDialog({
  children,
  ...props
}: { children: React.ReactNode } & React.ComponentProps<typeof Dialog>): React.ReactNode {
  return (
    <Dialog
      {...props}
      maxWidth="xl"
      fullWidth
      sx={{ userSelect: "none" }}
      slotProps={{ paper: { sx: { height: "100%" } } }}
    >
      {children}
    </Dialog>
  );
}

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
  const leftPaneWidth = `${((isSingleColumnLayout ? 12 : 5) / 12) * 100}%`;
  const rightPaneWidth = `${((isSingleColumnLayout ? 12 : 7) / 12) * 100}%`;

  return (
    <AlmostFullscreenDialog open={props.open} onClose={props.onClose} fullScreen={uiStore.isVerySmall}>
      {props.dialogTitle !== null && typeof props.dialogTitle !== "undefined" && (
        <DialogTitle>{props.dialogTitle}</DialogTitle>
      )}
      <DialogContent
        dividers
        sx={{
          p: "0 !important",
          overflowX: "hidden",
          /*
           * This is so that absolutely positioned elments inside the dialog
           * automatically move as the dialog content is scrolled
           */
          position: "relative",
          border: "none",
        }}
      >
        <Grid
          container
          sx={{
            justifyContent: "space-between",
            gap: 1,
            flexWrap: "nowrap",
            px: 1,
          }}
        >
          <Grid
            sx={{
              maxWidth: leftPaneWidth,
              flexBasis: leftPaneWidth,
              ...(isSingleColumnLayout && !(uiStore.dialogVisiblePanel === "left") ? { display: "none" } : {}),
            }}
          >
            {props.colLeft}
          </Grid>
          <Grid
            sx={{
              maxWidth: rightPaneWidth,
              flexBasis: rightPaneWidth,
              ...(isSingleColumnLayout && !(uiStore.dialogVisiblePanel === "right") ? { display: "none" } : {}),
            }}
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
