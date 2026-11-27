import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { OPEN_PDF_PREVIEW_DIALOG, PdfPreviewDialogWrapper } from "./PdfPreviewDialog";

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
      <div data-scale={String(scale)} data-testid={`mock-pdf-page-${pageNumber}`}>{`Page ${pageNumber}`}</div>
    ),
  };
});

vi.mock("@/common/axios", () => ({
  default: {
    get: axiosGetMock,
  },
}));

/*
 * jsdom has no IntersectionObserver, so this one reports every observed page
 * slot as visible, letting the windowed document mount its pages.
 */
class AlwaysIntersectingObserver {
  constructor(private readonly callback: IntersectionObserverCallback) {}

  observe(target: Element): void {
    this.callback(
      [{ target, isIntersecting: true } as IntersectionObserverEntry],
      this as unknown as IntersectionObserver,
    );
  }

  unobserve(): void {}

  disconnect(): void {}

  takeRecords(): Array<IntersectionObserverEntry> {
    return [];
  }
}

describe("pdf preview dialog island", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("IntersectionObserver", AlwaysIntersectingObserver);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
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

    expect(await screen.findByLabelText("gallery:callablePdfPreview.title")).toBeVisible();
    expect(screen.getByTestId("mock-pdf-document")).toHaveAttribute(
      "data-file",
      "/public/publicView/Streamfile/17?revision=4",
    );
    expect(await screen.findByTestId("mock-pdf-page-1")).toBeVisible();
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
    expect(await screen.findByLabelText("gallery:callablePdfPreview.title")).toBeVisible();
    expect(screen.getByTestId("mock-pdf-document")).toHaveAttribute(
      "data-file",
      "/public/publicView/Streamfile/direct/42?fileName=converted-preview.pdf",
    );
  });
});
