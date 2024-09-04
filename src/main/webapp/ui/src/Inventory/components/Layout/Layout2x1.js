//@flow

import React, { type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import useViewportDimensions from "../../../util/useViewportDimensions";

const UserHiddenRightPanelContext = React.createContext<{|
  userHiddenRightPanel: boolean,
  setUserHiddenRightPanel: (boolean) => void,
|}>({ userHiddenRightPanel: false, setUserHiddenRightPanel: () => {} });

export function useIsSingleColumnLayout(): boolean {
  const viewportDimensions = useViewportDimensions();
  const { userHiddenRightPanel } = React.useContext(
    UserHiddenRightPanelContext
  );
  return (
    viewportDimensions.isViewportSmall ||
    viewportDimensions.isViewportVerySmall ||
    userHiddenRightPanel
  );
}

export const RightPanelToggle: ComponentType<{||}> = observer(() => {
  const { userHiddenRightPanel, setUserHiddenRightPanel } = React.useContext(
    UserHiddenRightPanelContext
  );
  const { uiStore } = useStores();

  if (uiStore.isVerySmall || uiStore.isSmall) return null;
  return (
    <IconButtonWithTooltip
      onClick={() => {
        uiStore.setVisiblePanel("left");
        setUserHiddenRightPanel(!userHiddenRightPanel);
      }}
      sx={{ transform: "rotate(-90deg)", border: "2px solid" }}
      size="small"
      color="primary"
      icon={<ExpandCollapseIcon open={userHiddenRightPanel} />}
      title={userHiddenRightPanel ? "Show right panel" : "Hide right panel"}
    />
  );
});

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
const Layout2x1 = observer((props: Layout2x1Args) => {
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const hideLeftPanel = isSingleColumnLayout && uiStore.visiblePanel !== "left";
  const hideRightPanel =
    isSingleColumnLayout && uiStore.visiblePanel !== "right";
  const { classes } = useStyles({ hideLeftPanel, hideRightPanel });

  return (
    <Grid
      container
      spacing={isSingleColumnLayout ? 0 : 1}
      className={
        isSingleColumnLayout
          ? classes.paper
          : props.isDialog
          ? classes.dialogWrapper
          : classes.wrapper
      }
    >
      <Grid
        hidden={hideLeftPanel}
        item
        xs={isSingleColumnLayout ? 12 : 5}
        className={
          isSingleColumnLayout ? classes.leftPanelMobile : classes.leftPanel
        }
      >
        {props.colLeft}
      </Grid>
      <Grid
        item
        xs={isSingleColumnLayout ? 12 : 7}
        className={classes.rightPanel}
        hidden={hideRightPanel}
      >
        {props.colRight}
      </Grid>
    </Grid>
  );
});

export default function Layout2x1Wrapper(props: Layout2x1Args): Node {
  const [userHiddenRightPanel, setUserHiddenRightPanel] = React.useState(false);
  return (
    <UserHiddenRightPanelContext.Provider
      value={{ userHiddenRightPanel, setUserHiddenRightPanel }}
    >
      <Layout2x1 {...props} />
    </UserHiddenRightPanelContext.Provider>
  );
}
