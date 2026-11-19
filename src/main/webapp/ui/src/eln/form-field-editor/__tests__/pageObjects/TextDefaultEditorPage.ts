import { type FrameLocator, type Locator, page } from "vitest/browser";

/**
 * Retriable CSS locator. TinyMCE's iframe and contenteditable body have no
 * accessible role or name that is stable across engines (see NewNotePage), so
 * the editor body is the one place this page object uses CSS.
 */
function css(selector: string, within?: Locator | FrameLocator): Locator {
  const root = (within ?? page.elementLocator(document.body)) as unknown as { locator(s: string): Locator };
  return root.locator(`css=${selector}`);
}

export class TextDefaultEditorPage {
  get editButton(): Locator {
    return page.getByRole("button", { name: "Edit default text of Notes" });
  }

  get dialog(): Locator {
    return page.getByRole("dialog", { name: "Default text of Notes" });
  }

  /** The editable body inside TinyMCE's iframe. */
  get editorBody(): Locator {
    return css('body[contenteditable="true"]', page.frameLocator(css(".tox-edit-area__iframe")));
  }

  /** TinyMCE's toolbar appears once the editor has initialised and accepts typing. */
  get boldButton(): Locator {
    return this.dialog.getByRole("button", { name: "Bold" });
  }

  get preview(): Locator {
    return page.getByRole("group", { name: "Default text" });
  }

  async done(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Done" }).click();
  }

  async cancel(): Promise<void> {
    await this.dialog.getByRole("button", { name: "Cancel" }).click();
  }
}
