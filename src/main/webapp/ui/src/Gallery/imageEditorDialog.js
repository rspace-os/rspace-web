import React, { useEffect, useRef } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Button from "@mui/material/Button";
import axios from "axios";
import LoadingCircular from "../components/LoadingCircular";
import { makeStyles } from "tss-react/mui";
import "tui-image-editor/dist/tui-image-editor.css";
import "./custom.css";
import ImageEditor from "@toast-ui/react-image-editor";
import whiteTheme from "../common/theme.js";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faExclamationTriangle } from "@fortawesome/free-solid-svg-icons";
library.add(faExclamationTriangle);

import FileFormatPrompt from "../components/FileFormatPrompt";
import { createRoot } from "react-dom/client";

const useStyles = makeStyles()(() => ({
  dialogFiller: {
    minHeight: "80vh",
  },
  dialog: {
    display: "flex",
    position: "relative",
  },
  error: {
    width: "100%",
    height: "100%",
  },
  errorIcon: {
    width: "100px",
    height: "100px",
    position: "absolute",
    top: "calc(50% - 50px)",
    left: "calc(50% - 30px)",
  },
  errorText: {
    fontSize: "18px",
    width: "250px",
    height: "100px",
    position: "absolute",
    top: "calc(50% + 40px)",
    left: "calc(50% - 125px)",
    textAlign: "center",
  },
}));

function getImageSize(base64String) {
  var stringLength = base64String.length - "data:image/png;base64,".length;
  var sizeInBytes = 4 * Math.ceil(stringLength / 3) * 0.5624896334383812;
  var sizeInKb = sizeInBytes / 1000;

  if (sizeInKb < 1024) {
    return `${sizeInKb.toFixed(0)} KB`;
  } else {
    return `${(sizeInKb / 1024).toFixed(2)} MB`;
  }
}

export default function ImageEditorDialog(props) {
  const { classes } = useStyles();
  const [open, setOpen] = React.useState(false);
  const [recordId, setRecordId] = React.useState(null);
  const [base64, setBase64] = React.useState(null);
  const [submitted, setSubmitted] = React.useState(false);
  const [error, setError] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState("");
  const [dirty, setDirty] = React.useState(false);
  const [imageType, setImageType] = React.useState("");
  const [pngSize, setPngSize] = React.useState("");
  const [jpegSize, setJpegSize] = React.useState("");
  const [promptOpen, setPromptOpen] = React.useState(false);
  const editor = useRef(null);
  const closeButton = useRef(null);

  useEffect(() => {
    document.addEventListener("open-image-editor", function (e) {
      setRecordId(e.detail.recordid);
      setOpen(true);

      getImage(
        `/image/getImageForEdit/${e.detail.recordid}/${Date.now()}`,
        function (dataUrl) {
          setBase64(dataUrl);
        }
      );

      RS.hideHelpButton();
    });
  }, []);

  const closeDialog = () => {
    setPromptOpen(false);
    setOpen(false);
    setSubmitted(false);
    setRecordId(null);
    setBase64(null);
    setError(false);
    setDirty(false);
    RS.showHelpButton();
  };

  const getImage = (url, callback) => {
    var xhr = new XMLHttpRequest();

    xhr.onreadystatechange = function (e) {
      if (xhr.readyState === 4) {
        if (xhr.responseURL.split("/").pop() == "login") {
          setErrorMessage("You have been logged out. Please, log in again.");
          setError(true);
          closeButton.current.focus();
        } else if (xhr.status >= 200 && xhr.status < 300) {
          // get orignal image format
          setImageType(xhr.response.type.split("/")[1]);

          var reader = new FileReader();
          reader.onloadend = function () {
            callback(reader.result);
          };
          reader.readAsDataURL(xhr.response);
        } else {
          setErrorMessage(
            "The image could not be loaded or it can't be edited."
          );
          setError(true);
          closeButton.current.focus();
        }
      }
    };

    xhr.open("GET", url);
    xhr.responseType = "blob";
    xhr.send();
  };

  const handleSubmit = () => {
    // if JPEG, ask user what format to save the image in
    if (imageType == "jpeg") {
      setJpegSize(getImageSize(getImageInFormat("jpeg")));
      setPngSize(getImageSize(getImageInFormat("png")));
      setPromptOpen(true);
    } else {
      // otherwise, save as .png
      submit("png");
    }
  };

  const getImageInFormat = (format) => {
    const editorInstance = editor.current.getInstance();

    if (format == "jpeg") {
      return editorInstance.toDataURL({
        format: "jpeg",
        quality: 0.85,
      });
    } else {
      return editorInstance.toDataURL();
    }
  };

  const submit = (format) => {
    setSubmitted(true);
    setPromptOpen(false);

    let url = `/image/ajax/saveEditedImage`;
    axios
      .post(url, {
        imageId: recordId,
        imageBase64: getImageInFormat(format),
      })
      .then((response) => {
        gallery();
        RS.confirm("Image successfully saved.", "success");
      })
      .catch((error) => {
        RS.confirm(error.response.data, "warning", "infinite");
      })
      .finally(function () {
        closeDialog();
      });
  };

  const handleStackChange = (length) => {
    if (length) {
      setDirty(true);
    } else {
      setDirty(false);
    }
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Dialog fullScreen open={open} onClose={closeDialog}>
          <DialogContent
            style={{ padding: "0px" }}
            className={
              submitted || (!submitted && !base64) || error
                ? classes.dialogFiller
                : classes.dialog
            }
          >
            {!submitted && base64 && (
              <ImageEditor
                ref={editor}
                onUndoStackChanged={handleStackChange}
                includeUI={{
                  loadImage: {
                    path: base64,
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
            {!submitted && !base64 && !error && (
              <LoadingCircular message="Loading image..." />
            )}
            {submitted && (
              <LoadingCircular message="Your image is being processed. Please wait..." />
            )}
            {error && (
              <div className={classes.error}>
                <FontAwesomeIcon
                  icon="exclamation-triangle"
                  size="5x"
                  className={classes.errorIcon}
                />
                <br></br>
                <span className={classes.errorText}>{errorMessage}</span>
              </div>
            )}
            <FileFormatPrompt
              pngSize={pngSize}
              jpegSize={jpegSize}
              saveAs={submit}
              open={promptOpen}
              closePrompt={() => setPromptOpen(false)}
            />
          </DialogContent>
          <DialogActions>
            <Button
              ref={closeButton}
              onClick={closeDialog}
              style={{ color: "grey" }}
              data-test-id="close-confirmation"
            >
              Cancel
            </Button>
            <Button
              disabled={submitted || !dirty}
              onClick={handleSubmit}
              color="primary"
              data-test-id="confirm-action"
            >
              Save as new image
            </Button>
          </DialogActions>
        </Dialog>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("react-image-editor");
  const root = createRoot(domContainer);
  root.render(<ImageEditorDialog />);
});
