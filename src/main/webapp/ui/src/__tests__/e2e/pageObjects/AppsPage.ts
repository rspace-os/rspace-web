import type { Page } from "@playwright/test";

/**
 * The Apps page. Enabling/disabling by card name + the ENABLE/DISABLE dialog
 * is identical across integrations; only the name differs (e.g. "Chemistry",
 * "Box"). `setEnabled` is idempotent — if the integration is already in the
 * desired state the dialog close button is used instead.
 */
export class AppsPage {
  constructor(private readonly page: Page) {}

  async setEnabled(name: string, enabled: boolean): Promise<void> {
    await this.page.goto("/apps");
    await this.page.locator(`div[aria-label="${name}"]`).click();
    const dialog = this.page.getByRole("dialog");
    const btn = dialog.getByRole("button", {
      name: enabled ? "ENABLE" : "DISABLE",
    });
    if (await btn.isVisible().catch(() => false)) {
      await btn.click();
    } else {
      await dialog.getByRole("button", { name: "Close" }).click();
    }
  }
}
