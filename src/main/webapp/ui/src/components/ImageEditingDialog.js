//@flow

import React, { type Node, type ComponentType } from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import { observer } from "mobx-react-lite";
import ReactCrop from "react-image-crop";
import "react-image-crop/dist/ReactCrop.css";
import RotateLeftIcon from "@mui/icons-material/RotateLeft";
import RotateRightIcon from "@mui/icons-material/RotateRight";
import ButtonGroup from "@mui/material/ButtonGroup";
import Divider from "@mui/material/Divider";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import useViewportDimensions from "../util/useViewportDimensions";
import CardMedia from "@mui/material/CardMedia";
import Card from "@mui/material/Card";
import { styled } from "@mui/material/styles";

const StyledDialog = styled(Dialog)(() => ({
  "& > .MuiDialog-container > .MuiPaper-root": {
    height: "100%",
  },
}));

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

  const onRotate = (direction: "clockwise" | "counter clockwise"): void => {
    const getRotatedImageURL = (): string => {
      const image = imageElement.current;
      if (!image) throw new Error("Image file not present");
      const canvas = document.createElement("canvas");
      const ctx = canvas.getContext("2d");
      canvas.width = image.naturalHeight;
      canvas.height = image.naturalWidth;
      if (ctx) {
        ctx.translate(canvas.width / 2, canvas.height / 2);
        ctx.rotate(((direction === "clockwise" ? 90 : -90) * Math.PI) / 180);
        ctx.drawImage(image, -image.naturalWidth / 2, -image.naturalHeight / 2);
      }
      return canvas.toDataURL("image/jpeg", "1.0");
    };
    setEditorData(getRotatedImageURL());
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
    <StyledDialog maxWidth="md" open={open} onClose={close}>
      <DialogContent>
        {editorData && (
          <Card sx={{ height: "100%" }}>
            <CardMedia
              component="img"
              image={editorData}
              onLoad={onImageLoad}
              sx={{
                maxHeight: "100%",
                maxWidth: "100%",
                objectFit: "scale-down",
              }}
            />
          </Card>
        )}
      </DialogContent>
      <DialogActions>
        <ButtonGroup
          variant="outlined"
          sx={{
            border: "2px solid #cfc9d2",
            borderRadius: "8px",
          }}
        >
          <IconButton
            onClick={() => {
              onRotate("counter clockwise");
            }}
            aria-label="rotate left"
            size="small"
          >
            <RotateLeftIcon />
          </IconButton>
          <Divider
            orientation="vertical"
            sx={{
              height: "26px",
              marginTop: "4px",
              borderRightWidth: "1px",
            }}
          />
          <IconButton
            onClick={() => {
              onRotate("clockwise");
            }}
            aria-label="rotate right"
            size="small"
          >
            <RotateRightIcon />
          </IconButton>
        </ButtonGroup>
        <Box flexGrow={1}></Box>
        <Button onClick={mainDialogSubmit} color="primary">
          Done
        </Button>
      </DialogActions>
    </StyledDialog>
  );
}

export default (observer(
  ImageEditingDialog
): ComponentType<ImageEditingDialogArgs>);
