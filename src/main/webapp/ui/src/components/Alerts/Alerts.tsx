import Box from "@mui/material/Box";
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

  // Ordered from the top down
  const [alerts, setAlerts] = useState<ReadonlyArray<Alert>>([]);

  /*
   * The context value that is exposed to all of the components down the tree
   * is memoised so that every time an alert is added or removed the whole
   * page does not need to re-render. All that needs to re-render is the
   * displaying of the active alerts.
   */
  const contextValue = useMemo(
    () => ({
      addAlert: (alert: Alert) => {
        setAlerts((current) => [...current, alert]);
      },
      removeAlert: (alert: Alert) => {
        setAlerts((current) => current.filter((a) => a.id !== alert.id));
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

export default Alerts;
