import "ketcher-react/dist/index.css";

import React, { useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import { Editor, InfoModal } from "ketcher-react";
import { StandaloneStructServiceProvider } from "ketcher-standalone";
import { styled, ThemeProvider } from "@mui/material/styles";
import Stack from "@mui/material/Stack";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { Ketcher } from "ketcher-core";
import ValidatingSubmitButton, {
  IsValid,
  ValidationResult,
} from "../ValidatingSubmitButton";
import { createTheme } from "@mui/material";

declare global {
  interface Window {
    ketcher: Ketcher;
  }
}

const structServiceProvider = new StandaloneStructServiceProvider();

/*
 * Our theme sets MuiButton defaultProps color="standardIcon" globally. Ketcher
 * uses @emotion/react's ThemeProvider internally, which overrides theme colours
 * but not MUI's component-defaults context, so Ketcher's buttons inherit
 * color="standardIcon" but can't resolve it against Ketcher's partial theme.
 * This wrapper resets the default to "primary" within Ketcher's scope.
 */
const KetcherThemeProvider = ({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode => {
  return (
    <ThemeProvider
      theme={(outerTheme) =>
        createTheme(outerTheme, {
          components: {
            MuiButton: {
              defaultProps: {
                color: "primary",
              },
            },
          },
        })
      }
    >
      {children}
    </ThemeProvider>
  );
};

type KetcherDialogArgs = {
  isOpen?: boolean;
  handleClose: () => void;
  existingChem?: string;
  title: string;
  handleInsert: (ketcher: Ketcher) => void;
  actionBtnText?: string;
  readOnly?: boolean;
  additionalControls?: React.ReactNode;
  validationResult?: ValidationResult;
  onChange?: () => void;
  instructionText?: string;
};

const StyledDialog = styled(Dialog)(() => ({
  "& > .MuiDialog-container > .MuiPaper-root": {
    height: "calc(100% - 64px)",
  },
}));

const KetcherDialog = ({
  isOpen = true,
  handleClose,
  existingChem = "",
  title,
  handleInsert,
  actionBtnText = "",
  readOnly = false,
  additionalControls = null,
  validationResult = IsValid(),
  onChange,
  instructionText,
}: KetcherDialogArgs): React.ReactNode => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  React.useEffect(() => {
    if (isOpen) trackEvent("user:open:chemistry_editor", { readOnly });
  }, [isOpen]);

  const onInsertClick = () => {
    handleInsert(window.ketcher);
  };

  const closeAndReset = () => {
    handleClose();
    void window.ketcher.setMolecule("");
  };

  return (
    <StyledDialog open={isOpen} onClose={handleClose} fullWidth maxWidth="xl">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent style={{ minHeight: "0" }}>
        <Stack sx={{ height: "100%" }}>
          {instructionText && (
            <Typography variant="body2" sx={{ pb: 2 }}>
              {instructionText}
            </Typography>
          )}
          {additionalControls}
          <KetcherThemeProvider>
            {/* @ts-expect-error doesn't appear that staticResourcesUrl is in fact required */}
            <Editor
              disableMacromoleculesEditor
              errorHandler={(message) => {
                setHasError(true);
                setErrorMessage(message.toString());
              }}
              structServiceProvider={structServiceProvider}
              onInit={(ketcher) => {
                ketcher.editor.subscribe("change", () => {
                  onChange?.();
                });
                window.ketcher = ketcher;
                void ketcher.setMolecule(existingChem);
                if (readOnly) {
                  ketcher.editor.setOptions(
                    JSON.stringify({ viewOnlyMode: true }),
                  );
                }
              }}
            />
          </KetcherThemeProvider>
          {hasError && (
            <InfoModal
              message={errorMessage}
              close={() => {
                setHasError(false);
              }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={closeAndReset}>Cancel</Button>
        {actionBtnText && (
          <ValidatingSubmitButton
            loading={false}
            validationResult={validationResult}
            onClick={onInsertClick}
          >
            {actionBtnText}
          </ValidatingSubmitButton>
        )}
      </DialogActions>
    </StyledDialog>
  );
};

export default KetcherDialog;
