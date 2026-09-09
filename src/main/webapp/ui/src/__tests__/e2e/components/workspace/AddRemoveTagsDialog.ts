import type { Locator, Page } from "@playwright/test";

export class AddRemoveTagsDialog {
  readonly root: Locator;
  private readonly addTagButton: Locator;
  private readonly filterInput: Locator;
  readonly saveButton: Locator;
  readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Tagging" });
    this.addTagButton = this.root.getByRole("button", { name: "Add Tag", exact: true });
    this.filterInput = page.getByRole("combobox", { name: "Filter suggested tags" });
    this.saveButton = this.root.getByRole("button", { name: "Save", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel", exact: true });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  tagChip(tag: string): Locator {
    return this.root.getByRole("button", { name: tag, exact: true });
  }

  /** Existing tag chips currently applied — excludes the "Add Tag" control itself. */
  async getTags(): Promise<string[]> {
    const chips = await this.root.getByRole("button").allInnerTexts();
    return chips.filter((text) => text !== "Add Tag" && text !== "Cancel" && text !== "Save");
  }

  /** Adds a tag by exact text — selects a matching suggestion if one appears, else free-text via Enter. */
  async addTag(tag: string): Promise<void> {
    await this.addTagButton.click();
    await this.filterInput.fill(tag);
    const option = this.page.getByRole("option", { name: tag, exact: true });
    if (await option.isVisible().catch(() => false)) {
      await option.click();
    } else {
      await this.filterInput.press("Enter");
    }
    await this.tagChip(tag).waitFor({ state: "visible" });
  }

  async removeTag(tag: string): Promise<void> {
    const chip = this.tagChip(tag);
    await chip.locator(".MuiChip-deleteIcon").click();
    await chip.waitFor({ state: "hidden" });
  }

  async noCommonTagsAreDisplayed(): Promise<boolean> {
    return (await this.getTags()).length === 0;
  }

  async save(): Promise<void> {
    await this.saveButton.click();
    await this.root.waitFor({ state: "hidden" });
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
