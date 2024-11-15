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
import { styled } from "@mui/material/styles";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((_theme, { height }) => ({
  /*
   * These height style attributes are to ensure that when a tall image is
   * being edited, it is scaled down rather than overflowing the dialog and
   * resulting in the need to scroll: ideally the whole image should be visible
   * when cropping. Setting `maxHeight: 100%` and `objectFit: scale-down` is
   * not sufficient; all of the DOM nodes between the HTMLImageElement and the
   * DialogContent need to have `height: 100%`. We then have to shrink back the crop-mask to only cover the image itself and not the whitespace below.
   */
  crop: {
    height: "100%",
    "& .ReactCrop__child-wrapper": {
      height: "100%",
    },
    "& .ReactCrop__crop-mask": {
      height: `${height}px`,
    },
  },
}));

const StyledDialog = styled(Dialog)(() => ({
  "& > .MuiDialog-container > .MuiPaper-root": {
    /*
     * Even though this makes the dialog taller than it needs to be for
     * horizontal images, this is necessary to ensure that vertical images are
     * scaled down rather than overflowing the dialog with a scrollbar.
     */
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
  const [imageHeight, setImageHeight] = React.useState(0);
  const { classes } = useStyles({ height: imageHeight });
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
      const target: HTMLImageElement = e.target;
      const { naturalHeight, naturalWidth, height, width } = target;
      setImageHeight(height);
      if (!imageElement) return;
      imageElement.current = target;
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
      <DialogContent
        sx={{
          display: "flex",
          flexDirection: "row",
          justifyContent: "center",
        }}
      >
        {editorData && (
          <ReactCrop
            crop={crop}
            onChange={setCrop}
            className={classes.crop}
            maxHeight={imageHeight}
            onComplete={(...args) => {
              /*
               * Prevent the user from extending the cropping region to areas
               * outside of the image, below it. Normally, react-image-crop
               * would prevent this -- as it does in the other three directions
               * -- but because we're setting `height: 100%` on many of its
               * constitutent DOM nodes this is no longer enforced. As such, we
               * enforce it ourselves.
               */
              if (args[0].height + args[0].y > imageHeight)
                setCrop({
                  ...args[0],
                  height: Math.min(imageHeight - args[0].y, args[0].height),
                });
            }}
          >
            <img
              src={editorData}
              onLoad={onImageLoad}
              style={{
                /*
                 * Can't add a border or outline as it throws off the logic of
                 * the crop region and draggable anchors.
                 */
                maxHeight: "100%",
                maxWidth: "100%",
                objectFit: "scale-down",
              }}
            />
          </ReactCrop>
        )}
      </DialogContent>
      <DialogActions>
        <ButtonGroup variant="outlined">
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
              borderRightWidth: "2px",
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
