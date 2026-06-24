import type { Page } from "@playwright/test";

/**
 * The Apps page, shared by every integration e2e. Enabling/disabling by card
 * name + the ENABLE/DISABLE dialog is identical across integrations; only the
 * name differs (e.g. "Chemistry", "Box"). chemistryEnabled = enabled &&
 * available — `available` comes from the running service, this sets `enabled`.
 * Prefer DB seeding in setup where speed matters.
 */
export class AppsPage {
  constructor(private readonly page: Page) {}

  async setEnabled(name: string, enabled: boolean): Promise<void> {
    await this.page.goto("/apps");
    await this.page.locator(`div[aria-label="${name}"]`).click();
    const dialog = this.page.getByRole("dialog");
    const btn = dialog.getByRole("button", { name: enabled ? "ENABLE" : "DISABLE" });
    if (await btn.isVisible().catch(() => false)) {
      await btn.click();
    } else {
      await dialog.getByRole("button", { name: "Close" }).click();
    }
  }
}
