import Box from "@mui/material/Box";
import { observable, runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { type Alert } from "../../stores/contexts/Alert";
import { preventEventBubbling } from "../../util/Util";
import { DialogBoundary } from "../DialogBoundary";
import ToastMessage from "./ToastMessage";

type AlertsArgs = {
  children: React.ReactNode;
};

function Alerts({ children }: AlertsArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const { isViewportVerySmall } = useViewportDimensions();

  // Ordered from the top down. Shallow (deep: false) so only add/remove are tracked and each alert
  // object is stored by reference rather than deep-observed. Deep-observing an alert would turn its
  // React-element `icon` into a MobX proxy, and React 19 dev-mode fiber work then invokes
  // `element._debugTask.run(...)` on that proxy, throwing "'run' called with illegal receiver".
  // Alerts are immutable after mkAlert (only add/remove happens), so nothing reactive is lost.
  const [alerts] = useState(() => observable.array<Alert>([], { deep: false }));

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

    [],
  );

  return (
    <AlertContext.Provider value={contextValue}>
      <DialogBoundary>{children}</DialogBoundary>
      <Box
        sx={(theme) => ({
          position: "fixed",
          top: isViewportVerySmall ? 0 : theme.spacing(-2),
          right: theme.spacing(1),
          left: isViewportVerySmall ? theme.spacing(6.25) : "initial",
          zIndex: 1400,
        })}
        onClick={preventEventBubbling<React.MouseEvent<HTMLDivElement>>()}
        data-testid="Toasts"
        component="section"
        aria-roledescription="Alerts"
        aria-label={t("alerts.countLabel", { count: alerts.length })}
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
