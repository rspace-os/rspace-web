import type { Locator, Page } from "@playwright/test";
import { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";

export class DocumentHeader {
  readonly name: Locator;
  readonly editNameButton: Locator;
  readonly tags: Locator;
  readonly editTagsButton: Locator;
  readonly uniqueIdLink: Locator;
  readonly recordInfoLink: Locator;
  readonly showLastModifiedCheckbox: Locator;

  constructor(private readonly page: Page) {
    this.name = page.locator("#recordNameInHeader");
    this.editNameButton = page.locator("#renameRecordEdit");
    this.tags = page.locator("#notebookTags");
    this.editTagsButton = page.locator("#editTags");
    this.uniqueIdLink = page.locator("a[href*='/globalId/']").first();
    this.recordInfoLink = page.getByRole("link", { name: "Record Info" });
    this.showLastModifiedCheckbox = page.getByRole("checkbox", { name: "Show last modified date" });
  }

  async getName(): Promise<string> {
    return this.name.innerText();
  }

  async rename(newName: string): Promise<void> {
    await this.editNameButton.click();
    await this.page.getByRole("textbox", { name: "Name:" }).fill(newName);
    await this.page.keyboard.press("Enter");
    await this.name.filter({ hasText: newName }).waitFor({ state: "visible" });
  }

  async getUniqueId(): Promise<string> {
    return this.uniqueIdLink.innerText();
  }

  async getTags(): Promise<string[]> {
    return this.tags.locator("li").allInnerTexts();
  }

  async openRecordInfo(): Promise<RecordInfoDialog> {
    await this.recordInfoLink.click();
    const dialog = new RecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }
}
