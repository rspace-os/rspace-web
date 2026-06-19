import { expect } from "vitest";
import { type FrameLocator, type Locator, page, userEvent } from "vitest/browser";

/**
 * Retriable locator for a raw CSS selector, scoped to `within` (default <body>).
 * CSS is a workaround for the third-party TinyMCE editor, whose iframe and
 * contenteditable body expose no cross-browser accessible role/name. Vitest has
 * no public CSS locator, so we call the provider's `css=` engine via `.locator()`
 * (protected on the type, present at runtime) instead of registering a global one.
 */
function css(selector: string, within?: Locator | FrameLocator): Locator {
  const root = (within ?? page.elementLocator(document.body)) as unknown as { locator(s: string): Locator };
  return root.locator(`css=${selector}`);
}

/**
 * Page object for the NewNote component, as mounted by the story in
 * NewNote.story.tsx. Encapsulates the locators and user interactions;
 * assertions live in the tests themselves.
 *
 * TinyMCE renders its editable area inside an iframe; `editorBody` uses
 * `page.frameLocator(locator)` to pierce the iframe boundary.
 */
export class NewNotePage {
  /**
   * The TinyMCE contenteditable body inside its iframe. TinyMCE sets the iframe
   * `title` only on Firefox and the body `aria-label` only on non-Firefox, so
   * CSS is the only handle stable across all engines.
   */
  get editorBody(): Locator {
    return css('body[contenteditable="true"]', page.frameLocator(css(".tox-edit-area__iframe")));
  }

  get createNoteButton(): Locator {
    return page.getByRole("button", { name: "Create note" });
  }

  get clearButton(): Locator {
    return page.getByRole("button", { name: "Clear", exact: true });
  }

  errorPopover(message: string): Locator {
    return page.getByRole("dialog").filter({ hasText: message });
  }

  /**
   * Type text into the TinyMCE editor.
   * Uses `userEvent.type` which dispatches keyboard events one character at a
   * time, matching the original test's `pressSequentially` behaviour.
   * Note: `pressSequentially` is a raw Playwright Locator API method that is
   * NOT available on Vitest browser Locators.
   */
  async typeInEditor(text: string): Promise<void> {
    await userEvent.type(this.editorBody, text);
  }

  /**
   * Fill the editor with a long text then append one character to ensure the
   * TinyMCE change handler fires (mirrors the original spec's fill +
   * pressSequentially("a") pattern for the 2000-char limit test).
   */
  async fillEditorLong(text: string): Promise<void> {
    await userEvent.fill(this.editorBody, text);
    await userEvent.type(this.editorBody, "a");
  }

  async clearEditor(): Promise<void> {
    await userEvent.fill(this.editorBody, "");
  }

  async clickCreateNote(): Promise<void> {
    await this.createNoteButton.click();
  }

  async clickClear(): Promise<void> {
    await this.clearButton.click();
  }

  /**
   * Click "Create note", expect the error popover with the given message to
   * appear, then dismiss it with Escape.
   */
  async expectErrorOnSubmit(message: string): Promise<void> {
    await this.clickCreateNote();
    const popover = this.errorPopover(message);
    await expect.element(popover).toBeVisible();
    await userEvent.keyboard("{Escape}");
  }

  /**
   * Click "Create note", confirm no error dialog appears, then press Escape
   * to dismiss any residual focus traps.
   * Uses `not.toBeInTheDocument()` because after a successful creation the
   * Popover is never mounted; `not.toBeVisible()` would throw if the element
   * does not exist at all.
   */
  async expectNoErrorOnSubmit(): Promise<void> {
    await this.clickCreateNote();
    await expect.element(page.getByRole("dialog")).not.toBeInTheDocument();
    await userEvent.keyboard("{Escape}");
  }
}
