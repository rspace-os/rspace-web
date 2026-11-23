import { type Locator, page } from "vitest/browser";

/**
 * Page object for the Carousel as mounted by the stories in Carousel.story.tsx.
 *
 * react-pdf renders its document and pages as <div>s with no accessible role or
 * name, so the PDF queries use its class names.
 */
export class CarouselPage {
  get nextButton(): Locator {
    return page.getByRole("button", { name: /next/i });
  }

  /** The mounted PDF previews, in carousel order, whether shown or hidden. */
  private pdfDocuments(): Array<Element> {
    return Array.from(document.querySelectorAll(".react-pdf__Document"));
  }

  /** The number of page slots in the nth mounted PDF preview; 0 until the document has loaded. */
  slotCount(documentIndex: number): number {
    return this.pdfDocuments()[documentIndex]?.querySelectorAll("[data-page-slot]").length ?? 0;
  }

  /** The number of react-pdf pages mounted in the nth mounted PDF preview. */
  mountedPageCount(documentIndex: number): number {
    return this.pdfDocuments()[documentIndex]?.querySelectorAll(".react-pdf__Page").length ?? 0;
  }

  /**
   * Gives IntersectionObserver time to deliver its initial callbacks and React
   * time to commit any pages they mount, before asserting that none did.
   */
  async settle(): Promise<void> {
    for (let frame = 0; frame < 3; frame++) await new Promise((resolve) => requestAnimationFrame(resolve));
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
}
