import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "./DialogHelpers";

export class AddToBasketDialogComponent {
  readonly root: Locator;
  private readonly basketSelect: Locator;
  private readonly customNameInput: Locator;
  private readonly confirmButton: Locator;
  private readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog").filter({
      has: page.getByRole("button", { name: "Add to Basket", exact: true }),
    });
    this.basketSelect = this.root.getByRole("combobox", { name: "Choose a Basket" });
    this.customNameInput = this.root.getByRole("textbox", { name: "Custom Name (optional)" });
    this.confirmButton = this.root.getByRole("button", { name: "Add to Basket" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async enterNewBasketName(name: string): Promise<void> {
    await this.customNameInput.fill(name);
  }

  async chooseExistingBasket(name: string): Promise<void> {
    await this.basketSelect.click();
    await this.page.getByRole("option", { name, exact: true }).click();
  }

  async confirm(): Promise<void> {
    await clickAndWaitDetached(this.confirmButton, this.root);
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
