import type { Locator, Page } from "@playwright/test";

export class CreateNotebookDialog {
  readonly root: Locator;
  readonly nameInput: Locator;
  readonly createButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Create a notebook" }) });
    this.nameInput = this.root.getByRole("textbox", { name: "New notebook name" });
    this.createButton = this.root.getByRole("button", { name: "Create" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async create(name: string): Promise<void> {
    await this.nameInput.fill(name);
    await this.createButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
