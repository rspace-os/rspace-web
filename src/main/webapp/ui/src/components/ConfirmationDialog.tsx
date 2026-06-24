import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { type FormEventHandler, type JSX, useId } from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import materialTheme from "../theme";

declare global {
  interface RSGlobal {
    createConfirmationDialog?: (payload: ConfirmActionPayload) => void;
  }

  interface Window {
    RS: RSGlobal;
  }
}

export interface ConfirmActionPayload {
  title: string;
  consequences: string | JSX.Element;
  confirmTextLabel?: string;
  confirmText?: string;
  variant: string;
  // eslint-disable-next-line @typescript-eslint/no-unsafe-function-type
  // biome-ignore lint/complexity/noBannedTypes: initial biome migration
  callback: Function;
}

export function ConfirmationDialog({
  title,
  consequences,
  confirmTextLabel,
  confirmText,
  callback,
  handleCloseDialog,
  open = true,
}: ConfirmActionPayload & {
  handleCloseDialog: () => void;
  open?: boolean;
}) {
  const { t } = useTranslation("common");
  const [input, setInput] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const formTitle = useId();

  const focusInputField = (input: HTMLInputElement) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  const handleSubmit: FormEventHandler<HTMLFormElement> = (e) => {
    if (e) {
      e.preventDefault();
    }
    if (confirmText) {
      if (input !== confirmText) {
        setError(t("confirmationDialog.inputMismatch"));
        return;
      }
    }

    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    callback();
    handleCloseDialog();
  };

  return (
    <dialog>
      <form onSubmit={handleSubmit}>
        <Dialog open={open} onClose={handleCloseDialog} fullWidth aria-labelledby={formTitle}>
          <DialogTitle id={formTitle}>{title}</DialogTitle>
          <DialogContent sx={{ overscrollBehavior: "contain" }}>
            {typeof consequences === "string" ? (
              <Typography variant="subtitle1" gutterBottom dangerouslySetInnerHTML={{ __html: consequences }} />
            ) : (
              consequences
            )}

            {confirmText && (
              <FormControl error={error !== null} fullWidth>
                <TextField
                  data-test-id="confirmation-name"
                  inputRef={focusInputField}
                  margin="dense"
                  placeholder={confirmTextLabel}
                  fullWidth
                  value={input}
                  onChange={(e) => {
                    setInput(e.target.value);
                    if (error) {
                      setError(null);
                    }
                  }}
                  variant="standard"
                  slotProps={{
                    htmlInput: { "aria-label": confirmTextLabel },
                  }}
                />
                <FormHelperText id="component-error-text">{error}</FormHelperText>
              </FormControl>
            )}
          </DialogContent>
          <DialogActions>
            <Button onClick={handleCloseDialog} color="secondary">
              {t("actions.cancel")}
            </Button>
            {/* @ts-expect-error Fix types later */}
            <Button color="primary" onClick={handleSubmit}>
              {t("actions.confirm")}
            </Button>
          </DialogActions>
        </Dialog>
      </form>
    </dialog>
  );
}

export function createConfirmationDialog(payload: ConfirmActionPayload) {
  const domContainer = document.createElement("div");
  document.body.appendChild(domContainer);

  const root = createRoot(domContainer);

  const handleCloseDialog = () => {
    root.unmount();
    domContainer.remove();
  };

  root.render(
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <I18nRoot>
          <ConfirmationDialog {...payload} handleCloseDialog={handleCloseDialog} />
        </I18nRoot>
      </ThemeProvider>
    </StyledEngineProvider>,
  );
}

window.addEventListener("load", () => {
  window.RS.createConfirmationDialog = createConfirmationDialog;
});
