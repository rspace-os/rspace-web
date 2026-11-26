import type { Locator, Page } from "@playwright/test";
import { AppHeader } from "./AppHeader";

/** The colour mode items in the app bar's account menu. */
export class ColorModeMenu {
  private readonly header: AppHeader;
  readonly switchToDark: Locator;
  readonly switchToLight: Locator;
  readonly useSystem: Locator;

  constructor(private readonly page: Page) {
    this.header = new AppHeader(page);
    // The menu renders in a shadow root, which role locators pierce.
    this.switchToDark = page.getByRole("menuitem", { name: /Switch to dark mode/ });
    this.switchToLight = page.getByRole("menuitem", { name: /Switch to light mode/ });
    this.useSystem = page.getByRole("menuitem", { name: "Use system theme" });
  }

  /** Resolves once the menu has finished opening, so its colours are final. */
  async open(): Promise<void> {
    await this.header.openAccountMenu();
    const menu = this.page.getByRole("menu");
    await menu.waitFor({ state: "visible" });
    // The menu's paper fades in.
    await menu.evaluate(async (list) => {
      const opening = (list.parentElement ?? list)
        .getAnimations({ subtree: true })
        .filter((animation) => animation.effect?.getTiming().iterations !== Number.POSITIVE_INFINITY);
      await Promise.all(opening.map((animation) => animation.finished));
    });
  }

  async close(): Promise<void> {
    await this.page.keyboard.press("Escape");
    await this.page.getByRole("menu").waitFor({ state: "hidden" });
  }

  /** Chooses a mode; resolves once the menu has closed, not once the page has switched. */
  async choose(item: "dark" | "light" | "system"): Promise<void> {
    await this.open();
    const target = { dark: this.switchToDark, light: this.switchToLight, system: this.useSystem }[item];
    await target.click();
    await this.page.getByRole("menu").waitFor({ state: "hidden" });
  }
}
