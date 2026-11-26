import type { Locator, Page } from "@playwright/test";

/** The "Theme" select in the footer of logged-out pages (common/colorModeSwitch.jsp). */
export class ColorModeSelect {
  readonly select: Locator;

  constructor(page: Page) {
    this.select = page.getByLabel("Theme");
  }

  async choose(mode: "System" | "Light" | "Dark"): Promise<void> {
    await this.select.selectOption({ label: mode });
  }
}
