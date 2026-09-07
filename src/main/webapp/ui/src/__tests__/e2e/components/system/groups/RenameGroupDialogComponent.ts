import type { Locator, Page } from "@playwright/test";

export class RenameGroupDialogComponent {
  readonly root: Locator;
  readonly nameInput: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Rename Group" });
    this.nameInput = this.root.getByRole("textbox");
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async submit(newName: string): Promise<void> {
    await this.nameInput.fill(newName);
    await this.root.getByRole("button", { name: "Rename", exact: true }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
