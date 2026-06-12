import "ketcher-react/dist/index.css";

// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { createTheme, type ThemeOptions } from "@mui/material";
import Button from "@mui/material/Button";
import Dialog, { dialogClasses } from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import { paperClasses } from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import { ThemeProvider, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type { Ketcher } from "ketcher-core";
import { Editor, InfoModal } from "ketcher-react";
import { StandaloneStructServiceProvider } from "ketcher-standalone";
import React, { useMemo, useRef, useState } from "react";
import AnalyticsContext from "../../stores/contexts/Analytics";
import ValidatingSubmitButton, { IsValid, type ValidationResult } from "../ValidatingSubmitButton";

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
const KetcherThemeProvider = ({ children }: { children: React.ReactNode }): React.ReactNode => {
  const outerTheme = useTheme();
  const theme = useMemo(
    () =>
      createTheme(outerTheme as unknown as ThemeOptions, {
        components: {
          MuiButton: {
            defaultProps: {
              color: "primary",
            },
          },
        },
      }),
    [outerTheme],
  );
  return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
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
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);
  const initialKet = useRef<string | null>(null);

  React.useEffect(() => {
    if (isOpen) {
      trackEvent("user:open:chemistry_editor", { readOnly });
    } else {
      initialKet.current = null;
      setShowDiscardConfirm(false);
    }
  }, [isOpen]);

  const onInsertClick = () => {
    handleInsert(window.ketcher);
  };

  const closeAndReset = () => {
    handleClose();
    void window.ketcher.setMolecule("");
  };

  const handleCancelClick = async () => {
    if (readOnly || initialKet.current === null) {
      closeAndReset();
      return;
    }
    try {
      const currentKet = await window.ketcher?.getKet();
      if (currentKet !== undefined && currentKet !== initialKet.current) {
        setShowDiscardConfirm(true);
      } else {
        closeAndReset();
      }
    } catch (_e) {
      closeAndReset();
    }
  };

  const handleDialogClose = (_event: object, reason: "backdropClick" | "escapeKeyDown") => {
    if (reason === "backdropClick" || reason === "escapeKeyDown") {
      void handleCancelClick();
    }
  };

  return (
    <Dialog
      open={isOpen}
      onClose={handleDialogClose}
      fullWidth
      maxWidth="xl"
      sx={{
        [`& > .${dialogClasses.container} > .${paperClasses.root}`]: {
          height: "calc(100% - 64px)",
        },
      }}
    >
      <DialogTitle>{title}</DialogTitle>
      <DialogContent sx={{ minHeight: "0" }}>
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
                void ketcher
                  .setMolecule(existingChem)
                  .then(() => ketcher.getKet())
                  .then((ket) => {
                    initialKet.current = ket;
                  })
                  .catch(() => {
                    initialKet.current = null;
                  });
                if (readOnly) {
                  ketcher.editor.setOptions(JSON.stringify({ viewOnlyMode: true }));
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
        <Button onClick={handleCancelClick}>Cancel</Button>
        {actionBtnText && (
          <ValidatingSubmitButton loading={false} validationResult={validationResult} onClick={onInsertClick}>
            {actionBtnText}
          </ValidatingSubmitButton>
        )}
      </DialogActions>
      <Dialog open={showDiscardConfirm} onClose={() => setShowDiscardConfirm(false)}>
        <DialogTitle>Discard changes?</DialogTitle>
        <DialogContent>
          <DialogContentText>You have unsaved changes. Are you sure you want to discard them?</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowDiscardConfirm(false)}>Keep Editing</Button>
          <Button
            onClick={() => {
              setShowDiscardConfirm(false);
              closeAndReset();
            }}
            color="error"
          >
            Discard
          </Button>
        </DialogActions>
      </Dialog>
    </Dialog>
  );
};

export default KetcherDialog;
