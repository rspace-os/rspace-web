//@flow

import React, { type Node, type ComponentType } from "react";
import { makeStyles } from "tss-react/mui";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import ExpandCollapseIcon from "../../../components/ExpandCollapseIcon";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import useViewportDimensions from "../../../util/useViewportDimensions";

/**
 * The main Inventory UI is divided into two column on large viewports and a
 * single column on smaller viewports, such as mobile devices. This module
 * implements the logic that determines how these two panels are rendered.
 */

/**
 * When using a device with a wide viewport, the user has the option to hide
 * the right panel so that they can view the table of search results with
 * sufficient room to compare several columns of data. This context wraps the
 * whole component tree inside of the Layout2x1 component (the default export
 * of this module) but is not exported and thus is hidden from all those
 * components.
 */
const UserHiddenRightPanelContext = React.createContext<{|
  userHiddenRightPanel: boolean,
  setUserHiddenRightPanel: (boolean) => void,
|}>({ userHiddenRightPanel: false, setUserHiddenRightPanel: () => {} });

/**
 * If the UI is to be rendered with a single column -- either because the
 * browser's viewport isn't sufficiently wide enough or because they have opted
 * to hide the right panel on wider viewports -- then this custom hook returns
 * true.
 *
 * It is through this hook that the all of the components within the layout can
 * access the state managed by the UserHiddenRightPanelContext. All of these
 * components shouldn't care whether the UI is in single column mode because of
 * a small viewport or because the user has opted to hide the right panel, as
 * they can only determine whether the value is true or false; not why.
 */
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

/**
 * This is the toggle for hiding the right panel. It is the only the component
 * that cares whether the single column mode is due to an insufficiently wide
 * viewport or because the user has opted to hide the right panel by tapping
 * it. As such, it is included as an additional export in this module, allowing
 * it to access the UserHiddenRightPanelContext without requiring that it be
 * exported to the whole codebase.
 */
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
