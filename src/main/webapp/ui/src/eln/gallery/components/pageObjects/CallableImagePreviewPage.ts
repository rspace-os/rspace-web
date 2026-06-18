import { type Locator, page } from "vitest/browser";

/**
 * Page object for the CallableImagePreview component as mounted by the stories
 * in CallableImagePreview.story.tsx. Encapsulates locators and user
 * interactions; assertions live in the tests themselves.
 *
 * PhotoSwipe appends its modal directly to <body>, outside of the mounted
 * React root, so all locators query the full document.
 */
export class CallableImagePreviewPage {
  // ── Trigger buttons (rendered by the story's TestComponent) ──────────────

  get openImageButton(): Locator {
    return page.getByRole("button", { name: /open image preview/i });
  }

  get openImageWithCaptionButton(): Locator {
    return page.getByRole("button", { name: /open image with caption/i });
  }

  get openSmallImageButton(): Locator {
    return page.getByRole("button", { name: /open small image/i });
  }

  get openLargeImageButton(): Locator {
    return page.getByRole("button", { name: /open large image preview/i });
  }

  get openInvalidImageButton(): Locator {
    return page.getByRole("button", { name: /open invalid image/i });
  }

  get openImageWithEmptyCaptionButton(): Locator {
    return page.getByRole("button", { name: /open image with empty caption/i });
  }

  // ── PhotoSwipe modal elements ─────────────────────────────────────────────

  /** The root PhotoSwipe container (present in DOM once the modal is created) */
  get modal(): Locator {
    return page.getByCSS(".pswp");
  }

  /** The open/active PhotoSwipe overlay */
  get openModal(): Locator {
    return page.getByCSS(".pswp--open");
  }

  /** The image element rendered inside the active PhotoSwipe slide */
  get modalImage(): Locator {
    return page.getByCSS(".pswp__img");
  }

  /**
   * The caption container rendered by PhotoSwipe's default caption plugin.
   * PhotoSwipe uses `.pswp__default-caption` (not `.pswp__caption`) for the
   * inline caption element it appends to the modal when `withCaption` is true.
   */
  get caption(): Locator {
    return page.getByCSS(".pswp__default-caption");
  }

  /** The close button inside the PhotoSwipe toolbar */
  get closeButton(): Locator {
    return page.getByCSS(".pswp__button--close");
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  async clickOpenImage(): Promise<void> {
    await this.openImageButton.click();
  }

  async clickOpenImageWithCaption(): Promise<void> {
    await this.openImageWithCaptionButton.click();
  }

  async clickOpenSmallImage(): Promise<void> {
    await this.openSmallImageButton.click();
  }

  async clickOpenLargeImage(): Promise<void> {
    await this.openLargeImageButton.click();
  }

  async clickOpenInvalidImage(): Promise<void> {
    await this.openInvalidImageButton.click();
  }

  async clickOpenImageWithEmptyCaption(): Promise<void> {
    await this.openImageWithEmptyCaptionButton.click();
  }

  async closeModal(): Promise<void> {
    await this.closeButton.click();
  }
}
