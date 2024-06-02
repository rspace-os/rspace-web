//@flow

import React, { useRef, type Node, type ComponentType } from "react";
import "./ImageEditingDialogStyles.css";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import FileFormatPrompt from "./FileFormatPrompt";
import ImageEditor from "@toast-ui/react-image-editor";
import whiteTheme from "../common/theme";
import { observer } from "mobx-react-lite";

function getImageSize(base64String: string): string {
  var stringLength = base64String.length - "data:image/png;base64,".length;
  var sizeInBytes = 4 * Math.ceil(stringLength / 3) * 0.5624896334383812;
  var sizeInKb = sizeInBytes / 1000;

  if (sizeInKb < 1024) {
    return `${sizeInKb.toFixed(0)} KB`;
  } else {
    return `${(sizeInKb / 1024).toFixed(2)} MB`;
  }
}

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
  const [imageType, setImageType] = React.useState("");
  const [promptOpen, setPromptOpen] = React.useState(false);
  const closeButton = useRef(null);
  const editor = useRef(null);

  React.useEffect(() => {
    let settable = true;
    if (imageFile) {
      setImageType(imageTypeFromFile(imageFile));
      void readAsBinaryString(imageFile).then((binaryString: string) => {
        if (settable)
          setEditorData(`data:${imageType};base64,${btoa(binaryString)}`);
      });
    }
    return () => {
      settable = false;
    };
  }, [imageFile]);

  const getImageInFormat = (format: string): string => {
    if (!editor.current) return "";
    return editor.current.getInstance().toDataURL(
      format === "jpeg"
        ? {
            format: "jpeg",
            quality: 0.85,
          }
        : null
    );
  };

  const submit = (format: string) => {
    setPromptOpen(false);
    close();
    submitHandler(getImageInFormat(format));
  };

  const mainDialogSubmit = () => {
    if (editor.current?.getInstance().isEmptyUndoStack()) {
      submit(imageType);
    } else if (imageType === "jpeg") {
      setPromptOpen(true);
    } else {
      submit(imageType);
    }
  };

  return (
    <Dialog fullScreen open={open} onClose={close}>
      <DialogContent style={{ overscrollBehavior: "contain", padding: "0px" }}>
        {editorData && (
          <ImageEditor
            ref={editor}
            includeUI={{
              loadImage: {
                path: editorData,
                name: "Blank",
              },
              theme: whiteTheme,
              menu: ["crop", "flip", "rotate", "filter"],
              initMenu: null,
              uiSize: {
                width: "100%",
                height: "100%",
              },
              menuBarPosition: "right",
            }}
          />
        )}
        {promptOpen && (
          <FileFormatPrompt
            pngSize={getImageSize(getImageInFormat("png"))}
            jpegSize={getImageSize(getImageInFormat("jpeg"))}
            saveAs={submit}
            open={promptOpen}
            closePrompt={() => setPromptOpen(false)}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button
          ref={closeButton}
          onClick={mainDialogSubmit}
          color="primary"
          data-test-id="confirm-action"
        >
          Done
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default (observer(
  ImageEditingDialog
): ComponentType<ImageEditingDialogArgs>);
