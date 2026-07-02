import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { server } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  CallablePdfPreviewStory,
  CallablePdfPreviewWithCorruptedFile,
  CallablePdfPreviewWithError,
  CallablePdfPreviewWithLargePdf,
} from "../CallablePdfPreview.story";
import multiPageSamplePdf from "./fixtures/multi-page-sample.pdf?url";
import singlePageSamplePdf from "./fixtures/single-page-sample.pdf?url";
import { CallablePdfPreviewPage } from "./pageObjects/CallablePdfPreviewPage";

/*
 * TODO(firefox): pdf.js uses a Web Worker that does not communicate back in
 * Playwright's headless Firefox environment. The worker spawns but never
 * resolves, so neither `.react-pdf__Page` canvases nor the
 * `.react-pdf__message--error` element ever appear — the Document stays
 * permanently in its "loading" state. All tests that require pdf.js to have
 * finished loading (page render or error render) are skipped on Firefox.
 * Chromium and WebKit retain full coverage.
 */
const isFirefox = server.browser === "firefox";

/*
 * PDF MOCKING: react-pdf (pdf.js) loads the PDF URL via a real fetch. We serve
 * each test-document URL with the bytes of a real sample PDF file (imported via
 * Vite `?url` from ./fixtures): a 1-page sample for the single-page cases and a
 * 5-page sample for the multi-page / large cases. The invalid-URL case returns
 * a 404 so pdf.js fires its onLoadError handler, rendering the "Failed to load
 * PDF file" message. The corrupted-URL case returns bytes that are not a valid
 * PDF, triggering the same error path.
 *
 * Gallery app-shell endpoints (preference*, property*, SVG assets, etc.) are
 * handled by galleryAppShellHandlers(); analyticsProperties is in the global
 * appShellHandlers() from browserSetup.ts.
 */

/*
 * Fetch a sample PDF (served by Vite from ./fixtures via the `?url` import)
 * once and cache its bytes, so each handler invocation can hand pdf.js a fresh
 * response from the same buffer. The fixture URL is not under /test-documents,
 * so MSW lets the fetch pass through to Vite (onUnhandledRequest: "bypass").
 */
const pdfBytesCache = new Map<string, Promise<ArrayBuffer>>();
function loadSamplePdf(url: string): Promise<ArrayBuffer> {
  let bytes = pdfBytesCache.get(url);
  if (!bytes) {
    bytes = fetch(url).then((response) => response.arrayBuffer());
    pdfBytesCache.set(url, bytes);
  }
  return bytes;
}

async function servePdf(url: string) {
  return new HttpResponse(await loadSamplePdf(url), {
    status: 200,
    headers: {
      "Content-Type": "application/pdf",
      "Access-Control-Allow-Origin": "*",
    },
  });
}

const pdfHandlers = () => [
  http.get("/test-documents/sample.pdf", () => servePdf(singlePageSamplePdf)),
  http.get("/test-documents/single-page.pdf", () => servePdf(singlePageSamplePdf)),
  http.get("/test-documents/multi-page.pdf", () => servePdf(multiPageSamplePdf)),
  // The "large PDF" test only asserts the document loads and its first page
  // renders (not a page count), and the singular firstPage locator can't handle
  // a multi-page doc, so serve the single-page sample here.
  http.get("/test-documents/large-document.pdf", () => servePdf(singlePageSamplePdf)),
  http.get("/test-documents/invalid.pdf", () => new HttpResponse("Not Found", { status: 404 })),
  // Deliberately not a valid PDF, to exercise pdf.js's load-error path.
  http.get(
    "/test-documents/corrupted.pdf",
    () =>
      new HttpResponse(new TextEncoder().encode("This is not a valid PDF file"), {
        status: 200,
        headers: {
          "Content-Type": "application/pdf",
          "Access-Control-Allow-Origin": "*",
        },
      }),
  ),
];

const preview = new CallablePdfPreviewPage();

beforeEach(() => {
  worker.use(...galleryAppShellHandlers(), ...pdfHandlers());
});

afterEach(() => {
  cleanup();
});

describe("CallablePdfPreview", () => {
  describe("Component mounting and rendering", () => {
    test("Should render the component without errors", async () => {
      render(<CallablePdfPreviewStory />);
      await expect.element(preview.openPdfButton).toBeVisible();
    });

    test("Should render all interactive buttons", async () => {
      render(<CallablePdfPreviewStory />);
      await expect.element(preview.openPdfButton).toBeVisible();
      await expect.element(preview.openPdfButton).toBeEnabled();
      await expect.element(preview.openMultiPagePdfButton).toBeVisible();
      await expect.element(preview.openMultiPagePdfButton).toBeEnabled();
      await expect.element(preview.openSinglePagePdfButton).toBeVisible();
      await expect.element(preview.openSinglePagePdfButton).toBeEnabled();
    });
  });

  describe("PDF dialog functionality", () => {
    test("Should open PDF dialog when preview is triggered", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
    });

    test.skipIf(isFirefox)("Should load and display PDF document", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForDocument();
      await expect.element(preview.pdfDocument).toBeVisible();
      await preview.waitForFirstPage();
      await expect.element(preview.firstPage).toBeVisible();
    });

    test("Should close dialog with close button", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.clickClose();
      await expect.element(preview.dialog).not.toBeInTheDocument();
    });

    test("Should close dialog with escape key", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.pressEscape();
      await expect.element(preview.dialog).not.toBeInTheDocument();
    });
  });

  describe("Zoom functionality", () => {
    test("Should display zoom controls", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await expect.element(preview.zoomInButton).toBeVisible();
      await expect.element(preview.zoomOutButton).toBeVisible();
      await expect.element(preview.resetZoomButton).toBeVisible();
    });

    test("Should have reset zoom button disabled initially", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await expect.element(preview.resetZoomButton).toBeDisabled();
    });

    test.skipIf(isFirefox)("Should zoom in when zoom in button is clicked", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForFirstPage();

      const pageEl = preview.firstPage.element();
      const initialWidth = pageEl.getBoundingClientRect().width;

      await preview.clickZoomIn();

      await expect.poll(() => preview.firstPage.element().getBoundingClientRect().width).toBeGreaterThan(initialWidth);
    });

    test.skipIf(isFirefox)("Should enable reset zoom button after zooming", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForFirstPage();
      await preview.clickZoomIn();
      await expect.element(preview.resetZoomButton).toBeEnabled();
    });

    test.skipIf(isFirefox)("Should zoom out when zoom out button is clicked", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForFirstPage();

      // Zoom in twice first so there is room to zoom back out
      await preview.clickZoomIn();
      await preview.clickZoomIn();

      const pageEl = preview.firstPage.element();
      const zoomedInWidth = pageEl.getBoundingClientRect().width;

      await preview.clickZoomOut();

      await expect.poll(() => preview.firstPage.element().getBoundingClientRect().width).toBeLessThan(zoomedInWidth);
    });

    test.skipIf(isFirefox)("Should reset zoom level when reset button is clicked", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForFirstPage();

      const pageEl = preview.firstPage.element();
      const initialWidth = pageEl.getBoundingClientRect().width;

      await preview.clickZoomIn();
      await expect.poll(() => preview.firstPage.element().getBoundingClientRect().width).toBeGreaterThan(initialWidth);

      await preview.clickResetZoom();

      // After reset the button should be disabled again
      await expect.element(preview.resetZoomButton).toBeDisabled();
    });

    test.skipIf(isFirefox)("Should be able to click reset zoom button when enabled", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForFirstPage();
      await preview.clickZoomIn();
      await expect.element(preview.resetZoomButton).toBeEnabled();
    });
  });

  describe("Multi-page PDF support", () => {
    test("Should open dialog for multi-page PDF", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenMultiPagePdf();
      await expect.element(preview.dialog).toBeVisible();
    });

    test("Should load multi-page PDF document", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenMultiPagePdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForDocument();
      await expect.element(preview.pdfDocument).toBeVisible();
    });

    test.skipIf(isFirefox)("Should render multiple pages for multi-page PDF", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenMultiPagePdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForDocument();
      const pageCount = await preview.waitForMultiplePages();
      expect(pageCount).toBeGreaterThan(1);
    });

    test("Should open dialog for single page PDF", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenSinglePagePdf();
      await expect.element(preview.dialog).toBeVisible();
    });

    test("Should load single page PDF document", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenSinglePagePdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForDocument();
      await expect.element(preview.pdfDocument).toBeVisible();
    });

    test.skipIf(isFirefox)("Should display single page PDF", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenSinglePagePdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForDocument();
      await preview.waitForFirstPage();
      await expect.element(preview.firstPage).toBeVisible();
    });
  });

  describe("Large PDF handling", () => {
    test.skipIf(isFirefox)("Should handle large PDF files", async () => {
      render(<CallablePdfPreviewWithLargePdf />);
      await preview.clickOpenLargePdf();
      await expect.element(preview.dialog).toBeVisible();
      await preview.waitForDocument();
      await preview.waitForFirstPage();
      await expect.element(preview.firstPage).toBeVisible();
    });
  });

  describe("Error handling", () => {
    test("Should open dialog for invalid PDF URLs", async () => {
      render(<CallablePdfPreviewWithError />);
      await preview.clickOpenInvalidPdf();
      await expect.element(preview.dialog).toBeVisible();
    });

    test.skipIf(isFirefox)("Should display error message for invalid PDF URLs", async () => {
      render(<CallablePdfPreviewWithError />);
      await preview.clickOpenInvalidPdf();
      await expect.element(preview.dialog).toBeVisible();
      await expect.element(preview.loadErrorText).toBeVisible();
    });

    test("Should open dialog for corrupted PDF files", async () => {
      render(<CallablePdfPreviewWithCorruptedFile />);
      await preview.clickOpenCorruptedPdf();
      await expect.element(preview.dialog).toBeVisible();
    });

    test.skipIf(isFirefox)("Should display error message for corrupted PDF files", async () => {
      render(<CallablePdfPreviewWithCorruptedFile />);
      await preview.clickOpenCorruptedPdf();
      await expect.element(preview.dialog).toBeVisible();
      await expect.element(preview.loadErrorText).toBeVisible();
    });
  });

  describe("Accessibility", () => {
    test("Should have no axe violations", async () => {
      render(<CallablePdfPreviewStory />);
      await expect.element(preview.openPdfButton).toBeVisible();
      await expectNoAxeViolations();
    });

    test("Should have no axe violations in open dialog", async () => {
      render(<CallablePdfPreviewStory />);
      await preview.clickOpenPdf();
      await expect.element(preview.dialog).toBeVisible();
      await expectNoAxeViolations();
    });
  });
});
