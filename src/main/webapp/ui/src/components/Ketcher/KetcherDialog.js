//@flow
import "ketcher-react/dist/index.css";

import React, { useState, StrictMode, type Node } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import { Editor, InfoModal } from "ketcher-react";
import { StandaloneStructServiceProvider } from "ketcher-standalone";
import { styled } from "@mui/material/styles";
import Stack from "@mui/material/Stack";
import AnalyticsContext from "../../stores/contexts/Analytics";

const structServiceProvider = new StandaloneStructServiceProvider();

type KetcherDialogArgs = {|
  isOpen?: boolean,
  handleClose: ({| closeAndReset: () => Promise<string> |}) => void,
  existingChem?: string,
  title: string,
  handleInsert: (any) => void,
  actionBtnText?: string,
  readOnly?: boolean,
  additionalControls?: Node,
|};

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
}: KetcherDialogArgs): Node => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [hasError, setHasError] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");

  React.useEffect(() => {
    if (isOpen) trackEvent("user:open:chemistry_editor", { readOnly });
  }, [isOpen]);

  const onInsertClick = () => {
    handleInsert(window.ketcher);
    window.ketcher.setMolecule("");
  };

  const closeAndReset = () => {
    handleClose(window.ketcher);
    window.ketcher.setMolecule("");
  };

  return (
    <StrictMode>
      <StyledDialog open={isOpen} onClose={handleClose} fullWidth maxWidth="xl">
        <DialogTitle>{title}</DialogTitle>
        <DialogContent style={{ minHeight: "0" }}>
          <Stack sx={{ height: "100%" }}>
            {additionalControls}
            <Editor
              errorHandler={(message) => {
                setHasError(true);
                setErrorMessage(message.toString());
              }}
              structServiceProvider={structServiceProvider}
              onInit={(ketcher) => {
                window.ketcher = ketcher;
                ketcher.setMolecule(existingChem);
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
        <DialogActions>
          <Button onClick={closeAndReset}>Cancel</Button>
          {actionBtnText && (
            <Button onClick={onInsertClick} type="submit">
              {actionBtnText}
            </Button>
          )}
        </DialogActions>
      </StyledDialog>
    </StrictMode>
  );
};

export default KetcherDialog;
