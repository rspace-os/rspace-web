//@flow

import React, { useRef, type Node, type ComponentType } from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import { observer } from "mobx-react-lite";
import ReactCrop from "react-image-crop";
import "react-image-crop/dist/ReactCrop.css";

const imageTypeFromFile = (file: Blob): string => file.type.split("/")[1];

const readAsBinaryString = (file: Blob): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      // $FlowExpectedError[incompatible-cast] reader.result will be string because we called readAsBinaryString
      resolve((reader.result: string));
    };
    reader.onerror = () => {
      reject(reader.error);
    };
    reader.readAsBinaryString(file);
  });

type ImageEditingDialogArgs = {|
  imageFile: ?Blob,
  open: boolean,
  close: () => void,
  submitHandler: (string) => void,
|};

function ImageEditingDialog({
  imageFile,
  open,
  close,
  submitHandler,
}: ImageEditingDialogArgs): Node {
  const [editorData, setEditorData] = React.useState<?string>(null);
  const [crop, setCrop] = React.useState();

  React.useEffect(() => {
    let settable = true;
    if (imageFile) {
      const imageType = imageTypeFromFile(imageFile);
      void readAsBinaryString(imageFile).then((binaryString: string) => {
        if (settable)
          setEditorData(`data:${imageType};base64,${btoa(binaryString)}`);
      });
    }
    return () => {
      settable = false;
    };
  }, [imageFile]);

  const mainDialogSubmit = () => {
    // get cropped region from canvas
    submitHandler(newImage);
    close();
  };

  return (
    <Dialog fullScreen open={open} onClose={close}>
      <DialogContent style={{ overscrollBehavior: "contain", padding: "0px" }}>
        {editorData && (
          <ReactCrop crop={crop} onChange={setCrop}>
            <img src={editorData} />
          </ReactCrop>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={mainDialogSubmit} color="primary">
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default (observer(
  ImageEditingDialog
): ComponentType<ImageEditingDialogArgs>);
