// biome-ignore lint/style/useImportType: initial biome migration
import Snackbar, { SnackbarCloseReason } from "@mui/material/Snackbar";
import { observer } from "mobx-react-lite";
import React, { useContext } from "react";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { type Alert } from "../../stores/contexts/Alert";
import ErrorBoundary from "../ErrorBoundary";
import SnackbarContentWrapper from "./SnackbarContentWrapper";

type ToastMessageArgs = {
  alert: Alert;
};

function ToastMessage({ alert }: ToastMessageArgs): React.ReactNode {
  const [expanded, setExpanded] = React.useState(false);
  const [timeoutId, setTimeoutId] = React.useState<NodeJS.Timeout | null>(null);
  const { isViewportVerySmall } = useViewportDimensions();
  const { removeAlert } = useContext(AlertContext);

  const handleClose = (
    // biome-ignore lint/correctness/noUnusedFunctionParameters: initial biome migration
    event?: Event | React.SyntheticEvent,
    reason?: SnackbarCloseReason,
  ): void => {
    if (reason !== "clickaway") removeAlert(alert);
  };

  React.useEffect(() => {
    if (!alert.isInfinite) {
      setTimeoutId(setTimeout(() => removeAlert(alert), alert.duration));
    }
  }, []);

  const stopTimeout = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      setTimeoutId(null);
    }
  };

  return (
    <ErrorBoundary>
      <Snackbar
        data-test-id={`toast-element-${alert.id}`}
        anchorOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
        open={alert.isOpen}
        onClose={handleClose}
        sx={{
          position: "relative",
          mb: 1.25,
          maxWidth: isViewportVerySmall ? "100%" : 500,
          textAlign: "left",
          width: "100%",
          left: 0,
        }}
        role="group"
        aria-label={`${alert.variant} alert`}
      >
        <SnackbarContentWrapper
          onClose={handleClose}
          alert={alert}
          expanded={expanded}
          setExpanded={setExpanded}
          onInteraction={stopTimeout}
        />
      </Snackbar>
    </ErrorBoundary>
  );
}

/**
 * This components displays a single toast alert.
 */
export default observer(ToastMessage);
