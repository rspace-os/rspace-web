import type { Locator, Page } from "@playwright/test";

export class WorkspaceRenameDialog {
  readonly root: Locator;
  readonly nameInput: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog", { name: "Rename" });
    this.nameInput = this.root.getByRole("textbox", { name: "Name" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  /** Submits a rename the server refuses; the dialog stays open behind the error toast. */
  async submitExpectingRejection(newName: string): Promise<void> {
    await this.nameInput.fill(newName);
    await this.root.getByRole("button", { name: "Rename" }).click();
  }

  async cancel(): Promise<void> {
    await this.root.getByRole("button", { name: "Cancel" }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  async submit(newName: string): Promise<void> {
    await this.nameInput.fill(newName);
    await this.root.getByRole("button", { name: "Rename" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
