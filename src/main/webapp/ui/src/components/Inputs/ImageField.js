// @flow

import Avatar from "@mui/material/Avatar";
import Button from "@mui/material/Button";
import CameraAltIcon from "@mui/icons-material/CameraAlt";
import CropIcon from "@mui/icons-material/Crop";
import Grid from "@mui/material/Grid";
import ImageIcon from "@mui/icons-material/Image";
import React, {
  useState,
  useEffect,
  type Node,
  type ComponentType,
  type ElementProps,
} from "react";
import { isMobile } from "react-device-detect";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import DynamicallyLoadedImageEditor from "./DynamicallyLoadedImageEditor";
import { doNotAwait } from "../../util/Util";
import ImagePreview from "../ImagePreview";
import NoValue from "../../components/NoValue";
import FileField from "./FileField";

const useStyles = makeStyles()((theme, { width, height }) => ({
  rounded: {
    margin: 10,
    width,
    height,
  },
  skeleton: {
    width: "100%",
    display: "flex",
    justifyContent: "center",
  },
  preview: { cursor: "zoom-in" },
}));

export type ImageData = {|
  dataURL: string,
  file: Blob,
  binaryString?: string,
|};

type ImageFieldArgs = {|
  // required
  storeImage: (ImageData) => void,
  imageAsObjectURL: ?string,
  alt: string,

  // optional
  id?: string,
  disabled?: boolean,
  width?: string | number,
  height?: string | number,
  showPreview?: boolean,
  warningAlert?: string,
  endAdornment?: Node,
  noValueLabel?: ?string,
|};

function ImageField({
  storeImage,
  imageAsObjectURL,
  disabled,
  id,
  width = "100%",
  height = "100%",
  endAdornment = null,
  showPreview = true,
  warningAlert = "",
  noValueLabel,
  alt,
}: ImageFieldArgs): Node {
  const { classes } = useStyles({ width, height });
  const [editorFile, setEditorFile] = useState<?Blob>(null);
  const [editorOpen, setEditorOpen] = useState(false);

  const [link, setLink] = useState<?string>(null);
  const [size, setSize] = useState<?{| width: number, height: number |}>(null);

  const openPreview = () => {
    if (imageAsObjectURL) {
      setLink(imageAsObjectURL);
    }
  };

  const closePreview = () => {
    setLink(null);
  };

  const storeNewImage = (imageData: ImageData) => {
    setEditorFile(imageData.file);
    storeImage(imageData);
  };

  const imageSelection = ({
    binaryString,
    file,
  }: {
    binaryString: string,
    file: File,
  }) => {
    if (!/^image/.test(file.type)) {
      throw new Error("Not an image");
    }
    storeNewImage({
      binaryString,
      dataURL: `data:${file.type};base64,${btoa(binaryString)}`,
      file,
    });
  };

  const readAsDataUrl = (file: Blob): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        // $FlowExpectedError[incompatible-cast] reader.result will be string because we called readAsDataUrl
        resolve((reader.result: string));
      };
      reader.onerror = () => {
        reject(reader.error);
      };
      reader.readAsDataURL(file);
    });

  const submit = async (editedImage: Blob) => {
    storeNewImage({
      dataURL: await readAsDataUrl(editedImage),
      file: editedImage,
    });
  };

  useEffect(() => {
    let mountedCheck = true;
    void (async () => {
      if (imageAsObjectURL) {
        const file = await fetch(imageAsObjectURL).then((r) => r.blob());
        if (mountedCheck) setEditorFile(file);
      }
    })();
    return () => {
      mountedCheck = false;
    };
  }, [imageAsObjectURL]);

  const Preview = withStyles<
    {| width: mixed, height: mixed, ...ElementProps<typeof Avatar> |},
    { root: string, img: string }
  >(() => ({
    root: {
      margin: 10,
      width,
      height,
    },
    img: {
      objectFit: "contain",
    },
  }))((props) => (
    <Avatar
      imgProps={{
        width: typeof width === "number" ? width : null,
        height: typeof height === "number" ? height : null,
        alt,
      }}
      {...props}
    />
  ));

  return (
    <>
      {disabled && !imageAsObjectURL && (
        <NoValue label={noValueLabel ?? "None"} />
      )}
      {showPreview && imageAsObjectURL && (
        <Grid container justifyContent="center" alignItems="center">
          <Preview
            className={classes.preview}
            variant="rounded"
            src={imageAsObjectURL}
            data-test-id="PreviewImage"
            onClick={openPreview}
          />
          {link && (
            <ImagePreview
              closePreview={closePreview}
              link={link}
              size={size}
              setSize={setSize}
            />
          )}
        </Grid>
      )}
      {!disabled && (
        <>
          <FileField
            accept=".png, .jpg, .jpeg, .gif"
            buttonLabel={imageAsObjectURL ? "Replace Image" : "Add Image"}
            datatestid={
              imageAsObjectURL ? "ReplaceImageButton" : "AddImageButton"
            }
            id={id}
            onChange={imageSelection}
            icon={isMobile ? <CameraAltIcon /> : <ImageIcon />}
            InputProps={{
              endAdornment: (
                <>
                  <Grid item flexGrow={1}>
                    <Button
                      fullWidth
                      size="large"
                      color="primary"
                      variant="outlined"
                      disabled={!imageAsObjectURL}
                      onClick={() => {
                        setEditorOpen(true);
                      }}
                      startIcon={<CropIcon />}
                      data-test-id="EditImageButton"
                    >
                      Edit Image
                    </Button>
                  </Grid>
                  {endAdornment}
                </>
              ),
            }}
            warningAlert={warningAlert}
          />
          {editorOpen && (
            <DynamicallyLoadedImageEditor
              editorFile={editorFile}
              editorOpen={editorOpen}
              close={() => {
                setEditorOpen(false);
              }}
              submitHandler={doNotAwait(submit)}
              alt={alt}
            />
          )}
        </>
      )}
    </>
  );
}

export default (observer(ImageField): ComponentType<ImageFieldArgs>);
