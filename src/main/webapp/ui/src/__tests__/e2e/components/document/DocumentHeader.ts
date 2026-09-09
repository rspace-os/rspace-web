import { expect, type Locator, type Page } from "@playwright/test";
import { RecordInfoDialog } from "@/__tests__/e2e/components/shared/RecordInfoDialog";

export class DocumentHeader {
  readonly name: Locator;
  readonly editNameButton: Locator;
  readonly tags: Locator;
  readonly editTagsButton: Locator;
  readonly tagInput: Locator;
  readonly tagInfoDialogText: Locator;
  readonly uniqueIdLink: Locator;
  readonly recordInfoLink: Locator;
  readonly showLastModifiedCheckbox: Locator;

  constructor(private readonly page: Page) {
    this.name = page.locator("#recordNameInHeader");
    this.editNameButton = page.locator("#renameRecordEdit");
    // Legacy jQuery tagit widget: same #notebookTags element for both display and editing.
    this.tags = page.locator("#notebookTags");
    this.editTagsButton = page.getByRole("button", { name: "✏", description: "Edit tags" });
    this.tagInput = page
      .getByRole("textbox", { name: "Separate tags by comma..." })
      .or(page.getByRole("textbox", { name: "Ontologies enforced..." }));
    this.tagInfoDialogText = page.locator("#tag-info-dialog-content");
    this.uniqueIdLink = page.locator("a[href*='/globalId/']").first();
    this.recordInfoLink = page.getByRole("link", { name: "Record Info" });
    this.showLastModifiedCheckbox = page.getByRole("checkbox", { name: "Show last modified date" });
  }

  async getName(): Promise<string> {
    return this.name.innerText();
  }

  async getUniqueId(): Promise<string> {
    return this.uniqueIdLink.innerText();
  }

  async getTags(): Promise<string[]> {
    return this.tags.locator(".tagit-choice-inv").allInnerTexts();
  }

  private async openTagEditor(): Promise<void> {
    await expect(async () => {
      if (!(await this.tagInput.isVisible().catch(() => false))) {
        await this.editTagsButton.click();
        await this.tagInput.waitFor({ state: "visible", timeout: 3_000 });
      }
    }).toPass({ timeout: 15_000 });
  }

  async addTag(tag: string): Promise<void> {
    await this.openTagEditor();
    await this.tagInput.fill(tag);
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/tagRecord")),
      this.tagInput.press("Enter"),
    ]);
    if (!response.ok()) {
      throw new Error(`Adding tag '${tag}' failed: ${response.status()} ${response.statusText()}`);
    }
  }

  /** Submits a tag expected to be rejected for containing a forbidden character. */
  async addForbiddenTag(tag: string): Promise<void> {
    await this.openTagEditor();
    await this.tagInput.fill(tag);
    await this.tagInput.press("Enter");
    await this.tagInfoDialogText.waitFor({ state: "visible" });
  }

  tagChip(tag: string): Locator {
    return this.tags.getByRole("listitem").filter({ has: this.page.getByText(tag, { exact: true }) });
  }

  async removeTag(tag: string): Promise<void> {
    await this.openTagEditor();
    const chip = this.tagChip(tag);
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/tagRecord")),
      chip.getByText("×", { exact: true }).click(),
    ]);
    await chip.waitFor({ state: "hidden" });
  }

  async getSuggestedTags(): Promise<string[]> {
    await this.openTagEditor();
    await this.tagInput.click();
    const items = this.page.locator(".ui-autocomplete li.ui-menu-item");
    await items
      .first()
      .waitFor({ state: "visible", timeout: 3_000 })
      .catch(() => undefined);
    const texts = await items.allInnerTexts();
    return texts.map((text) => text.trim()).filter((text) => text.length > 0 && text !== "&nbsp;");
  }

  async openRecordInfo(): Promise<RecordInfoDialog> {
    await this.recordInfoLink.click();
    const dialog = new RecordInfoDialog(this.page);
    await dialog.waitUntilVisible();
    return dialog;
  }

  async rename(newName: string): Promise<void> {
    await this.name.click();
    const input = this.page.locator("#recordNameInHeaderEditor");
    await input.waitFor({ state: "visible" });
    await input.fill(newName);
    const [response] = await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/ajax/rename")),
      this.page.locator("#renameRecordSubmit").click(),
    ]);
    if (!response.ok()) {
      throw new Error(`Renaming to '${newName}' failed: ${response.status()} ${response.statusText()}`);
    }
    const body = await response.json().catch(() => null);
    if (body?.errorMsg) {
      throw new Error(`Renaming to '${newName}' failed: ${JSON.stringify(body.errorMsg)}`);
    }
    await this.name.filter({ hasText: newName }).waitFor({ state: "visible" });
  }
}
