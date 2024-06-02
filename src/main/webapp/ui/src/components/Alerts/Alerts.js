// @flow strict

import Box from "@mui/material/Box";
import ToastMessage from "./ToastMessage";
import {
  default as React,
  type Node,
  type ComponentType,
  useMemo,
} from "react";
import { makeStyles } from "tss-react/mui";
import { observer, useLocalObservable } from "mobx-react-lite";
import { preventEventBubbling } from "../../util/Util";
import useViewportDimensions from "../../util/useViewportDimensions";
import AlertContext, { mkAlert, type Alert } from "../../stores/contexts/Alert";
import { runInAction } from "mobx";
import { DialogBoundary } from "../DialogBoundary";

const useStyles = makeStyles()((theme, { verySmallLayout }) => ({
  snackbarsContainer: {
    position: "fixed",
    top: verySmallLayout ? 0 : theme.spacing(-2),
    right: theme.spacing(1),
    left: verySmallLayout ? theme.spacing(6.25) : "initial",
    zIndex: 1400,
  },
}));

type AlertsArgs = {|
  children: Node,
|};

function Alerts({ children }: AlertsArgs): Node {
  const { isViewportVerySmall } = useViewportDimensions();
  const { classes } = useStyles({ verySmallLayout: isViewportVerySmall });

  // ordered from the top down
  const alerts = useLocalObservable(() => ([]: Array<Alert>));

  /*
   * The context value that is exposed to all of the components down the tree
   * is memoised so that every time an alert is added or removed the whole
   * page does not need to re-render. All that needs to re-render is the
   * displaying of the active alerts.
   */
  const contextValue = useMemo(
    () => ({
      addAlert: (alert: Alert) => {
        runInAction(() => {
          alerts.push(alert);
        });
      },
      removeAlert: (alert: Alert) => {
        runInAction(() => {
          const i = alerts.findIndex((a) => a.id === alert.id);
          if (i >= 0) alerts.splice(i, 1);
        });
      },
    }),
    []
  );

  return (
    <AlertContext.Provider value={contextValue}>
      <DialogBoundary>{children}</DialogBoundary>
      <Box
        className={classes.snackbarsContainer}
        onClick={preventEventBubbling()}
        data-testid="Toasts"
        component="section"
        aria-label={`Alerts Listing. There are currently ${alerts.length} alerts.`}
      >
        {alerts.map((alert) => (
          <ToastMessage key={alert.id} alert={alert} />
        ))}
      </Box>
    </AlertContext.Provider>
  );
}

export default (observer(Alerts): ComponentType<AlertsArgs>);
