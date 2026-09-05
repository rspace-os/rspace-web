import type { Locator, Page } from "@playwright/test";

export class TagUsersDialogComponent {
  private readonly page: Page;
  readonly root: Locator;
  private readonly addTagButton: Locator;
  private readonly saveButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.root = page.getByRole("dialog", { name: "Tagging", exact: false });
    this.addTagButton = this.root.getByRole("button", { name: "Add Tag", exact: true });
    this.saveButton = this.root.getByRole("button", { name: "Save", exact: true });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private get tagSuggestionsCombobox(): Locator {
    return this.page.getByRole("combobox", { name: "Filter suggested tags" });
  }

  async addTag(tag: string, options: { chooseExisting?: boolean } = {}): Promise<void> {
    await this.addTagButton.click();
    const combobox = this.tagSuggestionsCombobox;
    await combobox.fill(tag);
    if (options.chooseExisting) {
      await this.page.getByRole("option", { name: tag, exact: true }).click();
    } else {
      await combobox.press("Enter");
    }
  }

  async removeTag(tag: string): Promise<void> {
    await this.root.locator(".MuiChip-root").filter({ hasText: tag }).locator(".MuiChip-deleteIcon").click();
  }

  async save(): Promise<void> {
    await this.saveButton.click();
    await this.root.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
