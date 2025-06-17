import "ketcher-react/dist/index.css";

import React, { useState, StrictMode } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import { Editor, InfoModal } from "ketcher-react";
import { StandaloneStructServiceProvider } from "ketcher-standalone";
import { styled } from "@mui/material/styles";
import Stack from "@mui/material/Stack";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { Ketcher } from "ketcher-core";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
  ValidationResult,
} from "../ValidatingSubmitButton";

declare global {
  interface Window {
    ketcher: Ketcher;
  }
}

const structServiceProvider = new StandaloneStructServiceProvider();

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
  onChange: () => void;
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
    <StrictMode>
      <StyledDialog open={isOpen} onClose={handleClose} fullWidth maxWidth="xl">
        <DialogTitle>{title}</DialogTitle>
        <DialogContent style={{ minHeight: "0" }}>
          <Stack sx={{ height: "100%" }}>
            {additionalControls}
            {/* @ts-expect-error doesn't appear that staticResourcesUrl is in fact required */}
            <Editor
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
                    JSON.stringify({ viewOnlyMode: true })
                  );
                }
              }}
            />
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
        {instructionText && (
          <Typography sx={{ px: 4, py: 1 }}>{instructionText}</Typography>
        )}
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
    </StrictMode>
  );
};

export default KetcherDialog;
