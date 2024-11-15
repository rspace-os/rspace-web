//@flow

import React, { type Node, type ComponentType } from "react";
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
  const [crop, setCrop] = React.useState({
    unit: "px",
    x: 0,
    y: 0,
    width: 0,
    height: 0,
  });
  const [scale, setScale] = React.useState({
    x: 1,
    y: 1,
  });
  const imageElement = React.useRef<HTMLImageElement | null>(null);

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

  const onImageLoad = (e: Event): void => {
    if (e.target instanceof HTMLImageElement) {
      const { naturalHeight, naturalWidth, height, width } = e.target;
      if (!imageElement) return;
      imageElement.current = e.target;
      setScale({
        x: naturalWidth / width,
        y: naturalHeight / height,
      });
      setCrop({
        unit: "px",
        x: 0,
        y: 0,
        width,
        height,
      });
    }
  };

  const cropImage = (): string => {
    const image = imageElement.current;
    if (!image) throw new Error("Image file not present");
    const canvas = document.createElement("canvas");

    const maxWidth = 600;
    const imageRatio = maxWidth / crop.width;
    canvas.width = maxWidth;
    canvas.height = crop.height * imageRatio;
    const ctx = canvas.getContext("2d");
    if (ctx)
      ctx.drawImage(
        image,
        crop.x * scale.x,
        crop.y * scale.y,
        crop.width * scale.x,
        crop.height * scale.y,
        0,
        0,
        crop.width * imageRatio,
        crop.height * imageRatio
      );

    return canvas.toDataURL("image/jpeg", "1.0");
  };

  const mainDialogSubmit = () => {
    const newImage = cropImage();
    submitHandler(newImage);
    close();
  };

  return (
    <Dialog fullScreen open={open} onClose={close}>
      <DialogContent style={{ overscrollBehavior: "contain", padding: "0px" }}>
        {editorData && (
          <ReactCrop crop={crop} onChange={setCrop}>
            <img src={editorData} onLoad={onImageLoad} />
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
