//@flow

import React, { type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";

const useStyles = makeStyles()((theme) => ({
  paper: {
    width: "100%",
    padding: theme.spacing(0),
  },
  wrapper: {
    padding: 0,
    width: "100%",
    margin: "0",
  },
  leftPanel: {
    position: "sticky",
    top: 0,
    padding: "0px !important",
    display: ({ hideLeftPanel }) => (hideLeftPanel ? "none" : "flex"),
    flexDirection: "column",
    maxHeight: "100vh",
  },
  rightPanel: {
    height: "100%",
    padding: "0px !important",
    display: ({ hideRightPanel }) => (hideRightPanel ? "none" : "flex"),
    flexDirection: "column",
  },
  leftPanelMobile: {
    display: ({ hideLeftPanel }) => (hideLeftPanel ? "none" : "flex"),
    flexDirection: "column",
    padding: theme.spacing(1),
    paddingTop: 0,
  },
  dialogWrapper: {
    height: "calc(100vh - 100px)",
    padding: "0px",
    width: "100%",
    margin: "0",
  },
}));

type Layout2x1Args = {|
  colRight: Node,
  colLeft: Node,
  isDialog?: boolean,
|};

/*
 * Defines a responsive layout, where both panels are shown on larger devices,
 * but one panel is hidden on smaller ones (depending on viewport size).
 * This logic is determined based on the state of uiStore.
 */
function Layout2x1(props: Layout2x1Args): Node {
  const { uiStore } = useStores();
  const hideLeftPanel =
    uiStore.isSingleColumnLayout && uiStore.visiblePanel !== "left";
  const hideRightPanel =
    uiStore.isSingleColumnLayout && uiStore.visiblePanel !== "right";
  const { classes } = useStyles({ hideLeftPanel, hideRightPanel });

  return (
    <Grid
      container
      spacing={uiStore.isSingleColumnLayout ? 0 : 1}
      className={
        uiStore.isSingleColumnLayout
          ? classes.paper
          : props.isDialog
          ? classes.dialogWrapper
          : classes.wrapper
      }
    >
      <Grid
        hidden={hideLeftPanel}
        item
        xs={uiStore.isSingleColumnLayout ? 12 : 5}
        className={
          uiStore.isSingleColumnLayout
            ? classes.leftPanelMobile
            : classes.leftPanel
        }
      >
        {props.colLeft}
      </Grid>
      <Grid
        item
        xs={uiStore.isSingleColumnLayout ? 12 : 7}
        className={classes.rightPanel}
        hidden={hideRightPanel}
      >
        {props.colRight}
      </Grid>
    </Grid>
  );
}

export default (observer(Layout2x1): ComponentType<Layout2x1Args>);
