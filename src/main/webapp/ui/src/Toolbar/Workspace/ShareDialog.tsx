import React from "react";
import Portal from "@mui/material/Portal";
import ErrorBoundary from "../../components/ErrorBoundary";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";
import Result from "../../util/result";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/workspace";

export default function Wrapper(): React.ReactNode {
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Portal>
            <Alerts>
              <DialogBoundary>
                <ShareDialog />
              </DialogBoundary>
            </Alerts>
          </Portal>
        </ThemeProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

const ShareDialog = () => {
  const [open, setOpen] = React.useState(false);
  const { trackEvent } = React.useContext(AnalyticsContext);

  React.useEffect(() => {
    function handler(event: Event) {
      setOpen(true);
    }
    window.addEventListener("OPEN_SHARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_SHARE_DIALOG", handler);
    };
  }, []);

  function handleClose() {
    setOpen(false);
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        handleClose();
      }}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>Share</DialogTitle>
      <DialogContent>
        {/* TODO: Add share dialog content */}
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => {
            handleClose();
          }}
        >
          Cancel
        </Button>
        <ValidatingSubmitButton
          loading={false}
          onClick={() => {
            // TODO: Implement sharing logic
            trackEvent("user:opens:share_dialog:workspace");
            handleClose();
          }}
          validationResult={Result.Ok(null)}
        >
          Share
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
};
