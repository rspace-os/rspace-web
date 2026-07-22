import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

export class BatchEditFormComponent {
  readonly root: Locator;
  private readonly nameInput: Locator;
  readonly saveButton: Locator;

  constructor(page: Page) {
    this.root = page.getByRole("main");
    this.nameInput = this.root.getByRole("textbox", { name: "Name" });
    this.saveButton = this.root.getByRole("button").filter({
      has: page.getByText("Save", { exact: true }),
    });
  }

  async waitForOpen(): Promise<void> {
    await this.nameInput.waitFor({ state: "visible" });
  }

  async fillName(name: string): Promise<void> {
    await this.nameInput.fill(name);
  }

  async save(): Promise<void> {
    await this.saveButton.evaluate((el) => el.scrollIntoView({ block: "center" }));
    await clickAndWaitDetached(this.saveButton, this.nameInput);
  }
}
