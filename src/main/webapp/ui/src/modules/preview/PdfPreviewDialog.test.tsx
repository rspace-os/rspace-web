import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";

import { OPEN_PDF_PREVIEW_DIALOG, PdfPreviewDialogWrapper } from "./PdfPreviewDialog";

const { axiosGetMock } = vi.hoisted(() => ({
  axiosGetMock: vi.fn(),
}));

vi.mock("@/eln/gallery/components/CallablePdfPreview", async () => {
  const React = await import("react");
  const PdfPreviewContext = React.createContext<(url: string) => void>(() => {});

  function CallablePdfPreview({ children }: { children: React.ReactNode }) {
    const [pdfUrl, setPdfUrl] = React.useState<string | null>(null);

    return (
      <PdfPreviewContext.Provider value={setPdfUrl}>
        {children}
        {pdfUrl && (
          <div role="dialog" aria-label="gallery:callablePdfPreview.title">
            {pdfUrl}
          </div>
        )}
      </PdfPreviewContext.Provider>
    );
  }

  return {
    CallablePdfPreview,
    usePdfPreview: () => ({ openPdfPreview: React.useContext(PdfPreviewContext) }),
  };
});

vi.mock("@/common/axios", () => ({
  default: {
    get: axiosGetMock,
  },
}));

describe("pdf preview dialog island", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("opens the callable PDF preview for PDF files", async () => {
    render(<PdfPreviewDialogWrapper />);

    act(() => {
      window.dispatchEvent(
        new CustomEvent(OPEN_PDF_PREVIEW_DIALOG, {
          detail: {
            documentId: 17,
            revisionId: 4,
            name: "example.pdf",
            fileExtension: "pdf",
            publicView: true,
          },
        }),
      );
    });

    const previewDialog = await screen.findByRole("dialog", { name: "gallery:callablePdfPreview.title" });
    expect(previewDialog).toHaveTextContent("/public/publicView/Streamfile/17?revision=4");
  });

  test("opens the callable Aspose preview for convertible files", async () => {
    axiosGetMock.mockResolvedValue({
      data: {
        data: "converted-preview.pdf",
      },
    });

    render(<PdfPreviewDialogWrapper />);

    act(() => {
      window.dispatchEvent(
        new CustomEvent(OPEN_PDF_PREVIEW_DIALOG, {
          detail: {
            documentId: 42,
            revisionId: 7,
            name: "example.docx",
            fileExtension: "docx",
            publicView: true,
          },
        }),
      );
    });

    await waitFor(() => {
      expect(axiosGetMock).toHaveBeenCalledWith("/Streamfile/ajax/convert/42?outputFormat=pdf&revision=7");
    });
    const previewDialog = await screen.findByRole("dialog", { name: "gallery:callablePdfPreview.title" });
    expect(previewDialog).toHaveTextContent("/public/publicView/Streamfile/direct/42?fileName=converted-preview.pdf");
  });
});
