import React, { useEffect, useRef, forwardRef } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import clsx from "clsx";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InfoIcon from "@mui/icons-material/Info";
import WarningIcon from "@mui/icons-material/Warning";
import CloseIcon from "@mui/icons-material/Close";
import { amber, green } from "@mui/material/colors";
import IconButton from "@mui/material/IconButton";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { makeStyles } from "tss-react/mui";
import { createRoot } from "react-dom/client";

export default function Snackbars(props) {
  const [toasts, setToasts] = React.useState([]);
  const isMountedRef = useRef(null);

  useEffect(() => {
    isMountedRef.current = true;
    document.addEventListener("show-toast-message", function (e) {
      if (isMountedRef.current) {
        var config = {
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
    });

    return () => (isMountedRef.current = false);
  }, []);

  const addToast = (toast_cnf) => {
    setToasts((old_toasts) => {
      return [toast_cnf, ...old_toasts];
    });

    // /* handling toast disappearance manually with timeouts rather than Snackbar.autoHideDuration,
    //  * as latter doesn't seem to trigger when page is unfocused, e.g. when user keeps editing
    //  * and saving in tinymce, which results in stacked/stucked confirmation messages.  */
    if (!toast_cnf.infinite) {
      setTimeout(function () {
        removeToast(toast_cnf.id);
      }, toast_cnf.duration);
    }
  };

  const removeToast = (id) => {
    setToasts((old_toasts) => {
      return old_toasts.filter((t) => t.id != id);
    });
  };

  return (
    <div
      style={{
        position: "fixed",
        top: "20px",
        right: "20px",
        zIndex: "2147483647",
      }}
    >
      {toasts.map((toast_params) => (
        <ToastMessage
          key={toast_params.id}
          closeToast={(id) => removeToast(id)}
          {...toast_params}
        />
      ))}
    </div>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
function WrappedSnackbars(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Snackbars {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

// render the snackbar wrapper component
let domContainer = document.getElementById("toast-message");
const root = createRoot(domContainer);
root.render(<WrappedSnackbars />);

function ToastMessage(props) {
  useEffect(() => {
    if (props.callback) {
      props.callback();
    }
  }, []);

  const handleClose = (event, reason) => {
    if (reason === "clickaway") {
      return;
    }
    props.closeToast(props.id);
  };

  return (
    <Snackbar
      data-test-id={`toast-element-${props.id}`}
      anchorOrigin={{
        vertical: "top",
        horizontal: "right",
      }}
      open={props.open}
      onClose={handleClose}
      style={{
        position: "relative",
        marginBottom: "10px",
        maxWidth: "500px",
        textAlign: "left",
      }}
    >
      <MySnackbarContentWrapper
        onClose={handleClose}
        variant={props.variant}
        message={props.message}
      />
    </Snackbar>
  );
}

const variantIcon = {
  success: CheckCircleIcon,
  warning: WarningIcon,
  error: ErrorIcon,
  notice: InfoIcon,
};

const useStyles1 = makeStyles()((theme) => ({
  success: {
    backgroundColor: green[600],
  },
  error: {
    backgroundColor: theme.palette.error.dark,
  },
  notice: {
    backgroundColor: theme.palette.primary.main,
  },
  warning: {
    backgroundColor: amber[700],
  },
  icon: {
    fontSize: 20,
  },
  iconVariant: {
    opacity: 0.9,
    marginRight: theme.spacing(1),
  },
  message: {
    display: "flex",
    alignItems: "center",
  },
}));

const MySnackbarContentWrapper = forwardRef((props, ref) => {
  const { classes } = useStyles1();
  const { className, message, onClose, variant, ...other } = props;
  const Icon = variantIcon[variant];

  return (
    <SnackbarContent
      data-test-id="toast-content"
      className={clsx(classes[variant], className)}
      aria-describedby="client-snackbar"
      message={
        <span id="client-snackbar" className={classes.message}>
          <Icon className={clsx(classes.icon, classes.iconVariant)} />
          <div dangerouslySetInnerHTML={{ __html: message }} />
        </span>
      }
      action={[
        <IconButton
          data-test-id="toast-close"
          key="close"
          aria-label="close"
          color="inherit"
          onClick={onClose}
        >
          <CloseIcon className={classes.icon} />
        </IconButton>,
      ]}
      {...other}
      ref={ref}
    />
  );
});
