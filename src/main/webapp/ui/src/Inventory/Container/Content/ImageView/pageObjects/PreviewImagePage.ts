import { type Locator, page } from "vitest/browser";

/**
 * Page object for PreviewImage, as mounted by PreviewImage.story.tsx.
 * Encapsulates locators and geometry reads; assertions live in the spec.
 */
export class PreviewImagePage {
  get image(): Locator {
    return page.getByRole("img", { name: "Preview of A visual container" });
  }

  /** The marker for the nth (1-based) location, located by its displayed number. */
  marker(n: number): Locator {
    return page.getByText(String(n), { exact: true });
  }

  imageSize(): { width: number; height: number } {
    const { width, height } = (this.image.element() as HTMLElement).getBoundingClientRect();
    return { width, height };
  }

  /** The nth marker's position relative to the image's top-left, from painted geometry. */
  markerOffset(n: number): { left: number; top: number } {
    const imageRect = (this.image.element() as HTMLElement).getBoundingClientRect();
    const markerRect = (this.marker(n).element() as HTMLElement).getBoundingClientRect();
    return {
      left: markerRect.left - imageRect.left,
      top: markerRect.top - imageRect.top,
    };
  }
}
