import React, { useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { createRoot } from "react-dom/client";

export default function ConfirmationDialog(props) {
  const [open, setOpen] = React.useState(false);
  const [error, setError] = React.useState(false);
  const [input, setInput] = React.useState("");
  const [details, setDetails] = React.useState({});

  useEffect(() => {
    document.addEventListener("confirm-action", function (e) {
      setDetails({
        title: e.detail.title,
        consequences: e.detail.consequences,
        confirmTextLabel: e.detail.confirmTextLabel,
        confirmText: e.detail.confirmText || false,
        variant: e.detail.variant || "notice", // success/warning/error/notice
        callback: e.detail.callback,
      });

      setOpen(true);
    });
  }, []);

  const validate = (e) => {
    e.preventDefault();

    if (details.confirmText && input != details.confirmText) {
      setError("Names do not match");
    } else {
      details.callback();
      closeDialog();
    }
  };

  const focusInputField = (input) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  const closeDialog = () => {
    setOpen(false);
    setInput("");
    setError(false);
    setDetails({});
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Dialog open={open} onClose={closeDialog} fullWidth>
          <DialogTitle id="form-dialog-title">
            <>{details.title}</>
          </DialogTitle>
          <DialogContent style={{ overscrollBehavior: "contain" }}>
            <Typography
              variant="subtitle1"
              gutterBottom
              dangerouslySetInnerHTML={{ __html: details.consequences }}
            />
            <form onSubmit={validate}>
              {details.confirmText && (
                <FormControl error={error != null} fullWidth>
                  <TextField
                    data-test-id="confirmation-name"
                    inputRef={focusInputField}
                    margin="dense"
                    placeholder={details.confirmTextLabel}
                    fullWidth
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    inputProps={{ "aria-label": details.confirmTextLabel }}
                    variant="standard"
                  />
                  <FormHelperText id="component-error-text">
                    {error}
                  </FormHelperText>
                </FormControl>
              )}
            </form>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={closeDialog}
              style={{ color: "grey" }}
              data-test-id="close-confirmation"
            >
              Cancel
            </Button>
            <Button
              onClick={validate}
              color="primary"
              data-test-id="confirm-action"
            >
              Confirm
            </Button>
          </DialogActions>
        </Dialog>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

// render the snackbar wrapper component
let domContainer = document.getElementById("confirmation-dialog");
const root = createRoot(domContainer);
root.render(<ConfirmationDialog />);
