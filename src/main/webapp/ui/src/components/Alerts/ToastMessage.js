// @flow strict

import ErrorBoundary from "../ErrorBoundary";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContentWrapper from "./SnackbarContentWrapper";
import {
  default as React,
  useContext,
  type Node,
  type ComponentType,
} from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import useViewportDimensions from "../../util/useViewportDimensions";
import AlertContext, { mkAlert, type Alert } from "../../stores/contexts/Alert";

const useStyles = makeStyles()((theme, { verySmallLayout }) => ({
  toastMessage_Snackbar: {
    position: "relative",
    marginBottom: 10,
    maxWidth: verySmallLayout ? "100%" : 500,
    textAlign: "left",
    width: "100%",
    left: 0,
  },
}));

type ToastMessageArgs = {|
  alert: Alert,
|};

function ToastMessage({ alert }: ToastMessageArgs): Node {
  const [expanded, setExpanded] = React.useState(false);
  const [timeoutId, setTimeoutId] = React.useState<?TimeoutID>(null);
  const { isViewportVerySmall } = useViewportDimensions();
  const { classes } = useStyles({ verySmallLayout: isViewportVerySmall });
  const { removeAlert } = useContext(AlertContext);

  const handleClose = (event: ?Event, reason: ?string): void => {
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
        className={classes.toastMessage_Snackbar}
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

export default (observer(ToastMessage): ComponentType<ToastMessageArgs>);
