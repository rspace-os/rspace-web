import type { Locator, Page } from "@playwright/test";

/**
 * Name / Tags / ID header fragment. Shared across structured-doc edit,
 * structured-doc view, notebook entry edit, and notebook entry view.
 *
 */
export class DocumentHeader {
  readonly name: Locator;
  readonly editNameButton: Locator;
  readonly tags: Locator;
  readonly editTagsButton: Locator;
  readonly uniqueIdLink: Locator;
  readonly recordInfoLink: Locator;
  readonly showLastModifiedCheckbox: Locator;

  constructor(page: Page) {
    this.name = page.locator("#recordNameInHeader");
    this.editNameButton = page.locator("#renameRecordEdit");
    this.tags = page.locator("#notebookTags");
    this.editTagsButton = page.locator("#editTags");
    this.uniqueIdLink = page.locator("a[href*='/globalId/']").first();
    this.recordInfoLink = page.locator("a.recordInfoIcon");
    this.showLastModifiedCheckbox = page.locator(".displayRevisions");
  }

  async getName(): Promise<string> {
    return this.name.innerText();
  }

  async getUniqueId(): Promise<string> {
    return this.uniqueIdLink.innerText();
  }

  async getTags(): Promise<string[]> {
    return this.tags.locator("li").allInnerTexts();
  }
}
