import { act, screen, waitFor } from "@testing-library/react";
import { describe, expect, test, vi, beforeEach } from "vitest";
import { render } from "@/__tests__/customQueries";
import {
  OPEN_PDF_PREVIEW_DIALOG,
  PdfPreviewDialogWrapper,
} from "./PdfPreviewDialogEntrypoint";

const { axiosGetMock } = vi.hoisted(() => ({
  axiosGetMock: vi.fn(),
}));

vi.mock("react-pdf", () => {
  type DocumentProps = {
    file: string;
    onLoadSuccess?: (args: { numPages: number }) => void;
    children?: import("react").ReactNode;
  };
  type PageProps = {
    pageNumber: number;
    scale: number;
  };
  return {
    pdfjs: { GlobalWorkerOptions: {} },
    Document: ({ file, onLoadSuccess, children }: DocumentProps) => {
      queueMicrotask(() => {
        onLoadSuccess?.({ numPages: 3 });
      });
      return (
        <div data-file={String(file)} data-testid="mock-pdf-document">
          {children}
        </div>
      );
    },
    Page: ({ pageNumber, scale }: PageProps) => (
      <div data-scale={String(scale)} data-testid={`mock-pdf-page-${pageNumber}`}>
        Page {pageNumber}
      </div>
    ),
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

    expect(await screen.findByLabelText(/pdf preview/i)).toBeVisible();
    expect(screen.getByTestId("mock-pdf-document")).toHaveAttribute(
      "data-file",
      "/public/publicView/Streamfile/17?revision=4",
    );
    expect(screen.getByTestId("mock-pdf-page-1")).toBeVisible();
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
      expect(axiosGetMock).toHaveBeenCalledWith(
        "/Streamfile/ajax/convert/42?outputFormat=pdf&revision=7",
      );
    });
    expect(await screen.findByLabelText(/pdf preview/i)).toBeVisible();
    expect(screen.getByTestId("mock-pdf-document")).toHaveAttribute(
      "data-file",
      "/public/publicView/Streamfile/direct/42?fileName=converted-preview.pdf",
    );
  });
});
