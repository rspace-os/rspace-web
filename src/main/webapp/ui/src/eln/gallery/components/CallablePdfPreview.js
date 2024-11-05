// @flow

import React, { type Node } from "react";
import { take, incrementForever } from "../../../util/iterators";
import { Document, Page, pdfjs } from "react-pdf";
import "react-pdf/dist/esm/Page/TextLayer.css";
import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import ZoomInIcon from "@mui/icons-material/ZoomIn";
import ZoomOutIcon from "@mui/icons-material/ZoomOut";
import ButtonGroup from "@mui/material/ButtonGroup";
import Divider from "@mui/material/Divider";

/**
 * Much like how `window.open` allows any JS code on the page to trigger the
 * opening of in a new window/tab with a specified URL, this module provides a
 * mechanism to allow any JS code to trigger the opening of a dialog that
 * displays PDFs by providing a URL that points to a PDF.
 */

/*
 * This snippet is a necessary step in initialising the PDF preview
 * functionality. Taken from the example code for react-pdf
 * https://github.com/wojtekmaj/react-pdf/blob/main/sample/webpack5/Sample.tsx
 */
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString();

const PdfPreviewContext = React.createContext((_link: string) => {});

/**
 * Use the callable PDF preview component to display a PDF in a dialog.
 */
export function usePdfPreview(): {|
  /**
   * Preview the PDF to be found at the passed URL. If the PDF cannot be
   * loaded then the dialog opens with the message "Failed to load PDF file".
   */
  openPdfPreview: (string) => void,
|} {
  const openPdfPreview = React.useContext(PdfPreviewContext);
  return {
    openPdfPreview,
  };
}

/**
 * This components provides a mechanism for any other component that is its
 * descendent to trigger the previewing of a PDF by passing the PDF's URL to a
 * call to `usePdfPreview`'s `openPdfPreview`. Just do something like
 *    const { openPdfPreview } = usePdfPreview();
 *    openPdfPreview("http://example.com/document.pdf");
 */
export function CallablePdfPreview({ children }: {| children: Node |}): Node {
  const [pdfPreviewOpen, setPdfPreviewOpen] = React.useState<null | string>(
    null
  );
  const [numPages, setNumPages] = React.useState<number>(0);
  const [scale, setScale] = React.useState(1);

  function onDocumentLoadSuccess({
    numPages: nextNumPages,
  }: {
    numPages: number,
    ...
  }): void {
    setNumPages(nextNumPages);
  }

  return (
    <>
      <PdfPreviewContext.Provider value={setPdfPreviewOpen}>
        {children}
      </PdfPreviewContext.Provider>
      {pdfPreviewOpen && (
        <Dialog
          open={true}
          fullWidth
          onClose={() => {
            setPdfPreviewOpen(null);
          }}
        >
          <DialogContent sx={{ overflowY: "auto" }}>
            <Document
              file={pdfPreviewOpen}
              onLoadSuccess={onDocumentLoadSuccess}
            >
              {[...take(incrementForever(), numPages)].map((index) => (
                <Page
                  key={`page_${index + 1}`}
                  pageNumber={index + 1}
                  width={550}
                  scale={scale}
                />
              ))}
            </Document>
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
                  setScale(scale * 1.2);
                }}
                aria-label="zoom in"
                size="small"
              >
                <ZoomInIcon />
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
                  setScale(scale / 1.2);
                }}
                aria-label="zoom out"
                size="small"
              >
                <ZoomOutIcon />
              </IconButton>
            </ButtonGroup>
            <Box flexGrow={1}></Box>
            <Button
              onClick={() => {
                setPdfPreviewOpen(null);
              }}
            >
              Close
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </>
  );
}
