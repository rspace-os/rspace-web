import type { Locator, Page } from "@playwright/test";

export class MoreActionsMenu<TAction extends string> {
  private readonly menu: Locator;

  constructor(
    private readonly page: Page,
    private readonly button: Locator,
  ) {
    this.menu = page.getByRole("menu").filter({ hasNotText: "Skip to Navigation" });
  }

  async open({ timeout }: { timeout?: number } = {}): Promise<void> {
    if (await this.menu.isVisible()) {
      return;
    }
    await this.button.click({ timeout });
    await this.menu.waitFor({ state: "visible", timeout });
  }

  async close({ timeout }: { timeout?: number } = {}): Promise<void> {
    if (!(await this.menu.isVisible())) {
      return;
    }
    await this.page.keyboard.press("Escape");
    await this.menu.waitFor({ state: "hidden", timeout });
  }

  item(name: TAction): Locator {
    return this.page.getByRole("menuitem", { name });
  }

  async click(name: TAction): Promise<void> {
    await this.open();
    await this.item(name).click();
  }

  async clickDirectOrFallback(directButton: Locator, name: TAction, directButtonTimeout = 5_000): Promise<void> {
    try {
      await directButton.click({ timeout: directButtonTimeout });
    } catch {
      await this.click(name);
    }
  }
}
