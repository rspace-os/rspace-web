//@flow

import React, { type Node } from "react";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import CloseIcon from "@mui/icons-material/Close";
import IconButton from "@mui/material/IconButton";

const styles = {
  closeButton: {
    position: "absolute",
    right: 0,
    top: 0,
    width: "auto",
  },
};

type ImageEditorDialogArgs = {|
  jpegSize: string,
  pngSize: string,
  saveAs: ("png" | "jpeg") => void,
  closePrompt: () => void,
  open: boolean,
|};

export default function ImageEditorDialog(props: ImageEditorDialogArgs): Node {
  return (
    <Dialog open={props.open}>
      <DialogTitle>
        Choose image format
        <IconButton
          aria-label="Close"
          data-test-id="closeModal"
          style={styles.closeButton}
          onClick={props.closePrompt}
        >
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent style={{ overscrollBehavior: "contain" }}>
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
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => props.saveAs("png")}
          style={{ color: "grey" }}
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
