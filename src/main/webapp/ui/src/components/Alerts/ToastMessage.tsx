import Snackbar, { type SnackbarCloseReason } from "@mui/material/Snackbar";
import { observer } from "mobx-react-lite";
import React, { useContext } from "react";
import { makeStyles } from "tss-react/mui";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { type Alert } from "../../stores/contexts/Alert";
import ErrorBoundary from "../ErrorBoundary";
import SnackbarContentWrapper from "./SnackbarContentWrapper";

const useStyles = makeStyles<{ verySmallLayout: boolean }>()(
    (_theme, { verySmallLayout }: { verySmallLayout: boolean }) => ({
        toastMessageSnackbar: {
            position: "relative",
            marginBottom: 10,
            maxWidth: verySmallLayout ? "100%" : 500,
            textAlign: "left",
            width: "100%",
            left: 0,
        },
    }),
);

type ToastMessageArgs = {
    alert: Alert;
};

function ToastMessage({ alert }: ToastMessageArgs): React.ReactNode {
    const [expanded, setExpanded] = React.useState(false);
    const [timeoutId, setTimeoutId] = React.useState<NodeJS.Timeout | null>(null);
    const { isViewportVerySmall } = useViewportDimensions();
    const { classes } = useStyles({ verySmallLayout: isViewportVerySmall });
    const { removeAlert } = useContext(AlertContext);

    const handleClose = (_event?: Event | React.SyntheticEvent, reason?: SnackbarCloseReason): void => {
        if (reason !== "clickaway") removeAlert(alert);
    };

    React.useEffect(() => {
        if (!alert.isInfinite) {
            setTimeoutId(setTimeout(() => removeAlert(alert), alert.duration));
        }
    }, [alert, removeAlert]);

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
                className={classes.toastMessageSnackbar}
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
