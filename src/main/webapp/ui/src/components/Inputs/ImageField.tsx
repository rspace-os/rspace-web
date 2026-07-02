import CameraAltIcon from "@mui/icons-material/CameraAlt";
import CropIcon from "@mui/icons-material/Crop";
import ImageIcon from "@mui/icons-material/Image";
import Avatar from "@mui/material/Avatar";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { isMobile } from "react-device-detect";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";
import ImagePreview from "../ImagePreview";
import DynamicallyLoadedImageEditor from "./DynamicallyLoadedImageEditor";
import FileField from "./FileField";
export type ImageData = {
  dataURL: string;
  file: Blob;
};
type ImageFieldArgs = {
  // required
  storeImage: (newImageData: ImageData) => void;
  imageAsObjectURL: string | null;
  alt: string;

  // optional
  id?: string;
  disabled?: boolean;
  width?: string | number;
  height?: string | number;
  showPreview?: boolean;
  warningAlert?: string;
  endAdornment?: React.ReactNode;
  noValueLabel?: string | null;
};
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
}: ImageFieldArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const [editorFile, setEditorFile] = useState<Blob | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [link, setLink] = useState<string | null>(null);
  const [size, setSize] = useState<{
    width: number;
    height: number;
  } | null>(null);
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
  const imageSelection = ({ dataURL, file }: { dataURL: string; file: File }) => {
    if (!/^image/.test(file.type)) {
      throw new Error("Not an image");
    }
    storeNewImage({ dataURL, file });
  };
  const readAsDataUrl = (file: Blob): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        resolve(reader.result as string);
      };
      reader.onerror = () => {
        reject(reader.error ?? new Error("Failed to read file as data URL"));
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
  return (
    <>
      {disabled && !imageAsObjectURL && <NoValue label={noValueLabel ?? t("values.none")} />}
      {showPreview && imageAsObjectURL && (
        <Grid
          container
          sx={{
            justifyContent: "center",
            alignItems: "center",
          }}
        >
          <Avatar
            variant="rounded"
            src={imageAsObjectURL}
            data-test-id="PreviewImage"
            onClick={openPreview}
            sx={{ m: "10px", width, height, cursor: "zoom-in" }}
            slotProps={{ img: { alt, sx: { objectFit: "contain" } } }}
          />
          {link && <ImagePreview closePreview={closePreview} link={link} size={size} setSize={setSize} />}
        </Grid>
      )}
      {!disabled && (
        <>
          <FileField
            accept=".png, .jpg, .jpeg, .gif"
            buttonLabel={imageAsObjectURL ? t("inputs.imageField.replaceImage") : t("inputs.imageField.addImage")}
            data-test-id={imageAsObjectURL ? "ReplaceImageButton" : "AddImageButton"}
            id={id}
            onChange={imageSelection}
            icon={isMobile ? <CameraAltIcon /> : <ImageIcon />}
            slotProps={{
              input: {
                endAdornment: (
                  <>
                    <Grid
                      sx={{
                        flexGrow: 1,
                      }}
                    >
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
                        {t("imageEditingDialog.title")}
                      </Button>
                    </Grid>
                    {endAdornment}
                  </>
                ),
              },
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
              submitHandler={(...args) => void submit(...args)}
              alt={alt}
            />
          )}
        </>
      )}
    </>
  );
}
export default observer(ImageField);
