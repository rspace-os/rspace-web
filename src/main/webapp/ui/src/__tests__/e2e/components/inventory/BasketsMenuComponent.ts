import { expect, type Locator, type Page } from "@playwright/test";

export class DeleteBasketDialogComponent {
  readonly root: Locator;
  private readonly confirmButton: Locator;
  private readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Deleting Basket" });
    this.confirmButton = this.root.getByRole("button", { name: "OK" });
    this.cancelButton = this.root.getByRole("button", { name: "CANCEL" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async confirm(): Promise<void> {
    await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/api/inventory/v1/baskets/") && res.request().method() === "DELETE",
      ),
      this.confirmButton.click(),
    ]);
    await this.root.waitFor({ state: "detached" });
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}

export class RenameBasketDialogComponent {
  readonly root: Locator;
  private readonly nameInput: Locator;
  private readonly saveButton: Locator;
  private readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog");
    this.nameInput = this.root.getByRole("textbox");
    this.saveButton = this.root.getByRole("button", { name: "Save" });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async rename(newName: string): Promise<void> {
    await this.nameInput.fill(newName);
    await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/api/inventory/v1/baskets/") && res.request().method() === "PUT",
      ),
      this.saveButton.click(),
    ]);
    await this.root.waitFor({ state: "detached" });
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click();
    await this.root.waitFor({ state: "detached" });
  }
}

export class BasketsMenuComponent {
  readonly root: Locator;
  private readonly emptyAlert: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("menu").filter({ hasNotText: "Skip to Navigation" });
    this.emptyAlert = this.root.getByRole("alert");
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  basketItem(name: string): Locator {
    return this.root.getByRole("menuitem").filter({
      has: this.page.getByText(name, { exact: true }),
    });
  }

  async close(): Promise<void> {
    if (await this.emptyAlert.isVisible().catch(() => false)) {
      await expect(async () => {
        if (await this.emptyAlert.isVisible().catch(() => false)) {
          await this.emptyAlert.getByRole("button", { name: "Close" }).click({ timeout: 2_000 });
        }
      }).toPass();
    } else {
      await this.page.keyboard.press("Escape");
    }
    await this.root.waitFor({ state: "detached" });
  }

  async renameBasket(name: string, newName: string): Promise<void> {
    await this.basketItem(name).getByRole("button", { name: "edit saved item" }).click();
    const dialog = new RenameBasketDialogComponent(this.page);
    await dialog.waitForOpen();
    await dialog.rename(newName);
  }

  async deleteBasket(name: string): Promise<void> {
    await this.basketItem(name).getByRole("button", { name: "delete saved item" }).click();
    const dialog = new DeleteBasketDialogComponent(this.page);
    await dialog.waitForOpen();
    await dialog.confirm();
  }
}
