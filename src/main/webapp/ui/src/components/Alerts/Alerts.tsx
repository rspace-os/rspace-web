import Box from "@mui/material/Box";
import ToastMessage from "./ToastMessage";
import React, { useMemo } from "react";
import { makeStyles } from "tss-react/mui";
import { observer, useLocalObservable } from "mobx-react-lite";
import { preventEventBubbling } from "../../util/Util";
import useViewportDimensions from "../../util/useViewportDimensions";
import AlertContext, { type Alert } from "../../stores/contexts/Alert";
import { runInAction } from "mobx";
import { DialogBoundary } from "../DialogBoundary";

const useStyles = makeStyles<{ verySmallLayout: boolean }>()(
  (theme, { verySmallLayout }: { verySmallLayout: boolean }) => ({
    snackbarsContainer: {
      position: "fixed",
      top: verySmallLayout ? 0 : theme.spacing(-2),
      right: theme.spacing(1),
      left: verySmallLayout ? theme.spacing(6.25) : "initial",
      zIndex: 1400,
    },
  })
);

type AlertsArgs = {
  children: React.ReactNode;
};

function Alerts({ children }: AlertsArgs): React.ReactNode {
  const { isViewportVerySmall } = useViewportDimensions();
  const { classes } = useStyles({ verySmallLayout: isViewportVerySmall });

  // ordered from the top down
  const alerts: Array<Alert> = useLocalObservable(() => []);

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
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - alerts will not change as it is a local observable
     */
    []
  );

  return (
    <AlertContext.Provider value={contextValue}>
      <DialogBoundary>{children}</DialogBoundary>
      <Box
        className={classes.snackbarsContainer}
        onClick={preventEventBubbling<React.MouseEvent<HTMLDivElement>>()}
        data-testid="Toasts"
        component="section"
        aria-roledescription="Alerts"
        aria-label={`There are currently ${alerts.length} alerts.`}
      >
        {alerts.map((alert) => (
          <ToastMessage key={alert.id} alert={alert} />
        ))}
      </Box>
    </AlertContext.Provider>
  );
}

/**
 * This component maintains the state of the current alerts and displays them
 * in the top-right corner of the viewport.
 */
export default observer(Alerts);
