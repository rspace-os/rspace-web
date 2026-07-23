import type { Locator, Page } from "@playwright/test";

export class MoreActionsMenu<TAction extends string> {
  private isOpen = false;

  constructor(
    private readonly page: Page,
    private readonly button: Locator,
  ) {}

  async open(): Promise<void> {
    if (this.isOpen) {
      return;
    }
    await this.button.click();
    this.isOpen = true;
  }

  item(name: TAction): Locator {
    return this.page.getByRole("menuitem", { name });
  }

  async click(name: TAction): Promise<void> {
    await this.open();
    await this.item(name).click();
    this.isOpen = false;
  }

  async clickDirectOrFallback(directButton: Locator, name: TAction, directButtonTimeout = 5_000): Promise<void> {
    try {
      await directButton.click({ timeout: directButtonTimeout });
    } catch {
      await this.click(name);
    }
  }
}
