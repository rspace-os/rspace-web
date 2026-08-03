import type { Locator, Page } from "@playwright/test";

export class CreateFolderDialog {
  readonly root: Locator;
  readonly nameInput: Locator;
  readonly navigateCheckbox: Locator;
  readonly createButton: Locator;
  readonly cancelButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("dialog").filter({ has: page.getByRole("heading", { name: "Create a folder" }) });
    this.nameInput = this.root.getByRole("textbox", { name: "Folder name" });
    this.navigateCheckbox = this.root.getByRole("checkbox", { name: "Navigate to the created folder" });
    this.createButton = this.root.getByRole("button", { name: "Create" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitUntilVisible(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async create(path: string, { navigate = false }: { navigate?: boolean } = {}): Promise<void> {
    await this.nameInput.fill(path);
    if (navigate) {
      await this.navigateCheckbox.check();
    }
    await this.createButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
