import { act, render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { OPEN_PDF_PREVIEW_DIALOG, PdfPreviewDialogWrapper } from "./PdfPreviewDialogEntrypoint";

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

describe("pdf preview dialog island", () => {
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
    expect(screen.getByTestId("mock-pdf-page-1")).toBeVisible();
  });

  test("opens the converted document preview for supported files", async () => {
    server.use(
      http.get("/Streamfile/ajax/convert/42", ({ request }) => {
        expect(new URL(request.url).searchParams.get("revision")).toBe("7");
        return HttpResponse.json({ data: "converted-preview.pdf" });
      }),
    );

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

    expect(await screen.findByLabelText("gallery:callablePdfPreview.title")).toBeVisible();
    expect(screen.getByTestId("mock-pdf-document")).toHaveAttribute(
      "data-file",
      "/public/publicView/Streamfile/direct/42?fileName=converted-preview.pdf",
    );
  });
  test("shows the localized conversion error from an HTTP failure", async () => {
    server.use(
      http.get("/Streamfile/ajax/convert/42", () =>
        HttpResponse.json({ error: { errorMessages: ["Conversion is busy. Try again shortly."] } }, { status: 429 }),
      ),
    );
    render(<PdfPreviewDialogWrapper />);
    act(() => {
      window.dispatchEvent(
        new CustomEvent(OPEN_PDF_PREVIEW_DIALOG, {
          detail: { documentId: 42, fileExtension: "docx" },
        }),
      );
    });
    expect(await screen.findByText("Conversion is busy. Try again shortly.")).toBeVisible();
    expect(screen.queryByText("Request failed with status code 429")).not.toBeInTheDocument();
  });
});
