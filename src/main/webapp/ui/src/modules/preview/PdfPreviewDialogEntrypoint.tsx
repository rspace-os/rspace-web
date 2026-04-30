import React from "react";
import { createRoot } from "react-dom/client";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "@/theme";
import ErrorBoundary from "@/components/ErrorBoundary";
import Alerts from "@/components/Alerts/Alerts";
import {
  CallablePdfPreview,
  usePdfPreview,
} from "@/eln/gallery/components/CallablePdfPreview";
import {
  CallableAsposePreview,
  useAsposePreview,
} from "@/eln/gallery/components/CallableAsposePreview";

export const OPEN_PDF_PREVIEW_DIALOG = "OPEN_PDF_PREVIEW_DIALOG";

export type OpenPdfPreviewDialogEventDetail = {
  documentId: number;
  revisionId?: number | null;
  name: string;
  fileExtension: string;
  publicView?: boolean;
};

function buildPdfStreamUrl({
  documentId,
  revisionId = null,
  publicView = false,
}: Pick<
  OpenPdfPreviewDialogEventDetail,
  "documentId" | "revisionId" | "publicView"
>): string {
  return (
    (publicView ? "/public/publicView" : "") +
    `/Streamfile/${documentId}${revisionId != null ? `?revision=${revisionId}` : ""}`
  );
}

export function PdfPreviewDialogFromGlobalEvent(): React.ReactNode {
  const { openPdfPreview } = usePdfPreview();
  const { openAsposePreviewFromDetails } = useAsposePreview();

  React.useEffect(() => {
    function handler(event: Event) {
      const {
        documentId,
        revisionId = null,
        fileExtension,
        publicView = false,
      } = (event as CustomEvent<OpenPdfPreviewDialogEventDetail>).detail;

      if (fileExtension.toLowerCase() === "pdf") {
        openPdfPreview(buildPdfStreamUrl({ documentId, revisionId, publicView }));
        return;
      }

      void openAsposePreviewFromDetails({
        documentId,
        revisionId,
        fileExtension,
        publicView,
      });
    }

    window.addEventListener(OPEN_PDF_PREVIEW_DIALOG, handler);
    return () => {
      window.removeEventListener(OPEN_PDF_PREVIEW_DIALOG, handler);
    };
  }, [openAsposePreviewFromDetails, openPdfPreview]);

  return null;
}

export function PdfPreviewDialogWrapper(): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <ErrorBoundary topOfViewport>
          <Alerts>
            <CallablePdfPreview>
              <CallableAsposePreview>
                <PdfPreviewDialogFromGlobalEvent />
              </CallableAsposePreview>
            </CallablePdfPreview>
          </Alerts>
        </ErrorBoundary>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

window.addEventListener("load", () => {
  const domContainer = document.createElement("div");
  domContainer.setAttribute("id", "pdf-preview-dialog-root");
  document.body.appendChild(domContainer);

  const root = createRoot(domContainer);
  root.render(
    <React.StrictMode>
      <PdfPreviewDialogWrapper />
    </React.StrictMode>,
  );
});

