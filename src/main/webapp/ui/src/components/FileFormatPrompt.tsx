import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import CloseIcon from "@mui/icons-material/Close";
import IconButton from "@mui/material/IconButton";
import styled from "@emotion/styled";

const CloseButton = styled(IconButton)`
  position: absolute;
  right: 0;
  top: 0;
  width: auto;
`;

const StyledDialogContent = styled(DialogContent)`
  overscroll-behavior: contain;
`;

type ImageEditorDialogArgs = {
  jpegSize: string;
  pngSize: string;
  saveAs: (extension: "png" | "jpeg") => void;
  closePrompt: () => void;
  open: boolean;
};

export default function ImageEditorDialog(
  props: ImageEditorDialogArgs
): React.ReactNode {
  return (
    <Dialog open={props.open}>
      <DialogTitle>
        Choose image format
        <CloseButton
          aria-label="Close"
          data-test-id="closeModal"
          onClick={props.closePrompt}
        >
          <CloseIcon />
        </CloseButton>
      </DialogTitle>
      <StyledDialogContent>
        <Typography variant="subtitle1" gutterBottom>
          The image you edited was in a JPEG format.
          <ul>
            <li>
              If you save as JPEG, the image will lose the transparent
              background.
            </li>
            <li>If you save as PNG, the image size might increase.</li>
          </ul>
        </Typography>
      </StyledDialogContent>
      <DialogActions>
        <Button
          onClick={() => props.saveAs("png")}
          sx={{ color: "grey " }}
          data-test-id="save-as-png"
        >
          {`Save as PNG (${props.pngSize})`}
        </Button>
        <Button
          onClick={() => props.saveAs("jpeg")}
          color="primary"
          data-test-id="save-as-jpeg"
        >
          {`Save as JPEG (${props.jpegSize})`}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
