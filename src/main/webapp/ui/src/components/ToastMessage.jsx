import React, { useEffect, useRef, forwardRef, useCallback } from "react";
import { ThemeProvider, useTheme } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InfoIcon from "@mui/icons-material/Info";
import WarningIcon from "@mui/icons-material/Warning";
import CloseIcon from "@mui/icons-material/Close";
import Box from "@mui/material/Box";
import { amber, green } from "@mui/material/colors";
import IconButton from "@mui/material/IconButton";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { createRoot } from "react-dom/client";
import PropTypes from "prop-types";

/**
 * Global snackbar host for legacy toast events.
 */
export default function Snackbars() {
  const [toasts, setToasts] = React.useState([]);
  const isMountedRef = useRef(false);

  const removeToast = useCallback((id) => {
    setToasts((oldToasts) => oldToasts.filter((toast) => toast.id !== id));
  }, []);

  const addToast = useCallback(
    (toastConfig) => {
      setToasts((oldToasts) => [toastConfig, ...oldToasts]);

      // /* handling toast disappearance manually with timeouts rather than Snackbar.autoHideDuration,
      //  * as latter doesn't seem to trigger when page is unfocused, e.g. when user keeps editing
      //  * and saving in tinymce, which results in stacked/stucked confirmation messages.  */
      if (!toastConfig.infinite) {
        setTimeout(() => {
          removeToast(toastConfig.id);
        }, toastConfig.duration);
      }
    },
    [removeToast],
  );

  useEffect(() => {
    isMountedRef.current = true;
    const handleShowToastMessage = (e) => {
      if (isMountedRef.current) {
        const config = {
          id: Date.now(),
          message: e.detail.message,
          variant: e.detail.variant || "notice", // success/warning/error/notice
          duration: e.detail.duration || 4000,
          callback: e.detail.callback,
          infinite: e.detail.infinite || false,
          open: true,
        };
        addToast(config);
      }
    };

    document.addEventListener("show-toast-message", handleShowToastMessage);

    return () => {
      isMountedRef.current = false;
      document.removeEventListener(
        "show-toast-message",
        handleShowToastMessage,
      );
    };
  }, [addToast]);

  return (
    <Box
      sx={{
        position: "fixed",
        top: "20px",
        right: "20px",
        zIndex: "2147483647",
      }}
    >
      {toasts.map((toastParams) => (
        <ToastMessage
          key={toastParams.id}
          closeToast={(id) => removeToast(id)}
          {...toastParams}
        />
      ))}
    </Box>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
function WrappedSnackbars() {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Snackbars />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

// render the snackbar wrapper component
const domContainer = document.getElementById("toast-message");
const root = createRoot(domContainer);
root.render(<WrappedSnackbars />);

function ToastMessage({ callback, closeToast, id, open, variant, message }) {
  useEffect(() => {
    if (callback) {
      callback();
    }
  }, [callback]);

  const handleClose = (event, reason) => {
    if (reason === "clickaway") {
      return;
    }
    closeToast(id);
  };

  return (
    <Snackbar
      data-test-id={`toast-element-${id}`}
      anchorOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      open={open}
      onClose={handleClose}
      sx={{
        position: "relative",
        marginBottom: "10px",
        maxWidth: "500px",
        textAlign: "left",
      }}
    >
      <MySnackbarContentWrapper
        onClose={handleClose}
        variant={variant}
        message={message}
      />
    </Snackbar>
  );
}

ToastMessage.propTypes = {
  callback: PropTypes.func,
  closeToast: PropTypes.func.isRequired,
  id: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  open: PropTypes.bool.isRequired,
  variant: PropTypes.oneOf(["success", "warning", "error", "notice"])
    .isRequired,
  message: PropTypes.string.isRequired,
};

const variantIcon = {
  success: CheckCircleIcon,
  warning: WarningIcon,
  error: ErrorIcon,
  notice: InfoIcon,
};

const MySnackbarContentWrapper = forwardRef(
  ({ message, onClose, variant, ...other }, ref) => {
    const theme = useTheme();
    const Icon = variantIcon[variant];
    const backgroundColors = {
      success: green[600],
      error: theme.palette.error.dark,
      warning: amber[700],
      notice: theme.palette.primary.main,
    };
    const backgroundColor =
      backgroundColors[variant] ?? backgroundColors.notice;
    const iconStyle = { fontSize: 20 };

    return (
      <SnackbarContent
        data-test-id="toast-content"
        aria-describedby="client-snackbar"
        sx={{ backgroundColor }}
        message={
          <Box
            component="span"
            id="client-snackbar"
            sx={{ display: "flex", alignItems: "center" }}
          >
            <Icon
              sx={{
                ...iconStyle,
                opacity: 0.9,
                mr: 1,
              }}
            />
            <div dangerouslySetInnerHTML={{ __html: message }} />
          </Box>
        }
        action={[
          <IconButton
            data-test-id="toast-close"
            key="close"
            aria-label="close"
            color="inherit"
            onClick={onClose}
          >
            <CloseIcon sx={iconStyle} />
          </IconButton>,
        ]}
        {...other}
        ref={ref}
      />
    );
  },
);

MySnackbarContentWrapper.propTypes = {
  message: PropTypes.string.isRequired,
  onClose: PropTypes.func.isRequired,
  variant: PropTypes.oneOf(["success", "warning", "error", "notice"])
    .isRequired,
};

MySnackbarContentWrapper.displayName = "MySnackbarContentWrapper";
