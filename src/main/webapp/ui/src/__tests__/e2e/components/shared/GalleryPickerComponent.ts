import type { Locator, Page } from "@playwright/test";

export class GalleryPickerComponent {
  readonly root: Locator;
  readonly createButton: Locator;
  readonly addButton: Locator;
  readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Gallery" });
    this.createButton = this.root.getByRole("button", { name: "Create", exact: true });
    this.addButton = this.root.getByRole("button", { name: "Add" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async goToSection(name: string): Promise<void> {
    await this.root.getByRole("button", { name, exact: true }).click();
  }

  async uploadFile(filePath: string, expectedName: string): Promise<void> {
    await this.createButton.click();
    const fileChooserPromise = this.page.waitForEvent("filechooser");
    await this.page.getByRole("menuitem", { name: "Upload Files" }).click();
    const fileChooser = await fileChooserPromise;
    await fileChooser.setFiles(filePath);

    await this.root.getByText(expectedName, { exact: true }).first().waitFor({ state: "visible" });
  }

  async selectItem(name: string): Promise<void> {
    await this.root.getByText(name, { exact: true }).last().click();
  }

  async add(): Promise<void> {
    await this.addButton.click();
    await this.root.waitFor({ state: "hidden" });
  }
}
