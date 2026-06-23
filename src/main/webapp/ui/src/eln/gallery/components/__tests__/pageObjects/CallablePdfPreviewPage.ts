import { vi } from "vitest";
import { type Locator, page, userEvent } from "vitest/browser";

/**
 * Retriable locator for a raw CSS selector under <body>. CSS is a workaround,
 * used ONLY because the third-party react-pdf library renders its document and
 * pages as canvas-wrapping <div>s with no accessible role/name. Vitest exposes
 * no public CSS locator, so we call the provider's `css=` engine via `.locator()`
 * (protected on the type, present at runtime) instead of registering a global one.
 */
function css(selector: string): Locator {
  return (page.elementLocator(document.body) as unknown as { locator(s: string): Locator }).locator(`css=${selector}`);
}

/**
 * Page object for the CallablePdfPreview component as mounted by the stories
 * in CallablePdfPreview.story.tsx. Encapsulates locators and user interactions;
 * assertions live in the tests themselves.
 *
 * The PDF dialog is rendered inside the React tree (not appended to body
 * separately), so all locators query the full document via `page`.
 *
 * Note: the eslint-disable for testing-library/prefer-screen-queries is
 * intentional -- `page` here is Vitest browser-mode's locator root from
 * `vitest/browser`, NOT a destructured render result. The lint rule incorrectly
 * flags `page.getByRole(...)` calls as if they came from `render()`.
 */
export class CallablePdfPreviewPage {
  // ── Trigger buttons (rendered by the story's TestComponent) ──────────────

  get openPdfButton(): Locator {
    return page.getByRole("button", { name: /open pdf preview/i });
  }

  get openMultiPagePdfButton(): Locator {
    return page.getByRole("button", { name: /open multi-page pdf/i });
  }

  get openSinglePagePdfButton(): Locator {
    return page.getByRole("button", { name: /open single page pdf/i });
  }

  get openLargePdfButton(): Locator {
    return page.getByRole("button", { name: /open large pdf/i });
  }

  get openInvalidPdfButton(): Locator {
    return page.getByRole("button", { name: /open invalid pdf/i });
  }

  get openCorruptedPdfButton(): Locator {
    return page.getByRole("button", { name: /open corrupted pdf/i });
  }

  // ── Dialog elements ───────────────────────────────────────────────────────

  get dialog(): Locator {
    return page.getByRole("dialog");
  }

  get pdfDocument(): Locator {
    return css(".react-pdf__Document");
  }

  get firstPage(): Locator {
    return css(".react-pdf__Page");
  }

  /** Locator for the pdf.js load-error text displayed inside the dialog. */
  get loadErrorText(): Locator {
    return page.getByText(/failed to load pdf/i);
  }

  // ── Zoom controls ─────────────────────────────────────────────────────────

  get zoomInButton(): Locator {
    return page.getByRole("button", { name: /zoom in/i });
  }

  get zoomOutButton(): Locator {
    return page.getByRole("button", { name: /zoom out/i });
  }

  get resetZoomButton(): Locator {
    return page.getByRole("button", { name: /reset zoom/i });
  }

  get closeButton(): Locator {
    return page.getByRole("button", { name: /close/i });
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  async clickOpenPdf(): Promise<void> {
    await this.openPdfButton.click();
  }

  async clickOpenMultiPagePdf(): Promise<void> {
    await this.openMultiPagePdfButton.click();
  }

  async clickOpenSinglePagePdf(): Promise<void> {
    await this.openSinglePagePdfButton.click();
  }

  async clickOpenLargePdf(): Promise<void> {
    await this.openLargePdfButton.click();
  }

  async clickOpenInvalidPdf(): Promise<void> {
    await this.openInvalidPdfButton.click();
  }

  async clickOpenCorruptedPdf(): Promise<void> {
    await this.openCorruptedPdfButton.click();
  }

  async clickZoomIn(): Promise<void> {
    await this.zoomInButton.click();
  }

  async clickZoomOut(): Promise<void> {
    await this.zoomOutButton.click();
  }

  async clickResetZoom(): Promise<void> {
    await this.resetZoomButton.click();
  }

  async clickClose(): Promise<void> {
    await this.closeButton.click();
  }

  async pressEscape(): Promise<void> {
    await userEvent.keyboard("{Escape}");
  }

  /**
   * Waits for the PDF document container to appear in the DOM.
   */
  async waitForDocument(): Promise<void> {
    await vi.waitFor(
      () => {
        const doc = document.querySelector(".react-pdf__Document");
        if (!doc) throw new Error("PDF document not loaded yet");
      },
      { timeout: 15000, interval: 100 },
    );
  }

  /**
   * Waits for at least one PDF page canvas to appear.
   */
  async waitForFirstPage(): Promise<void> {
    await vi.waitFor(
      () => {
        const pages = document.querySelectorAll(".react-pdf__Page");
        if (pages.length === 0) throw new Error("No PDF pages rendered yet");
      },
      { timeout: 15000, interval: 100 },
    );
  }

  /**
   * Waits for more than one PDF page canvas to appear (multi-page PDFs).
   * Returns the count of rendered pages.
   */
  async waitForMultiplePages(): Promise<number> {
    let count = 0;
    await vi.waitFor(
      () => {
        const pages = document.querySelectorAll(".react-pdf__Page");
        count = pages.length;
        if (count <= 1) throw new Error(`Only ${count} page(s) rendered, expected more than 1`);
      },
      { timeout: 20000, interval: 100 },
    );
    return count;
  }
}
