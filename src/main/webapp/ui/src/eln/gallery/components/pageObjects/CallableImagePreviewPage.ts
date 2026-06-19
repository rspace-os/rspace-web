import { type Locator, page } from "vitest/browser";

/**
 * Retriable locator for a raw CSS selector under <body>. CSS is a workaround,
 * used ONLY because the third-party PhotoSwipe library renders these elements
 * with no accessible role/name to query semantically. Vitest exposes no public
 * CSS locator, so we call the provider's `css=` engine via `.locator()`
 * (protected on the type, present at runtime) instead of registering a global one.
 */
function css(selector: string): Locator {
  return (page.elementLocator(document.body) as unknown as { locator(s: string): Locator }).locator(`css=${selector}`);
}

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

  /**
   * The open PhotoSwipe overlay. PhotoSwipe sets `role="dialog"` on its root
   * (`.pswp`) and removes the whole element from the DOM on close, so the dialog
   * role is a semantic stand-in for "preview is open".
   */
  get openModal(): Locator {
    return page.getByRole("dialog");
  }

  /** PhotoSwipe marks its `<img>` `role="presentation"`, so it has no a11y name. */
  get modalImage(): Locator {
    return css(".pswp__img");
  }

  /** PhotoSwipe's caption `<div>`; must match even when empty, so `getByText` can't be used. */
  get caption(): Locator {
    return css(".pswp__default-caption");
  }

  /** The close button inside the PhotoSwipe toolbar */
  get closeButton(): Locator {
    return page.getByRole("button", { name: "Close" });
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
