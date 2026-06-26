import type { Locator } from "@playwright/test";
import { BasePage } from "./BasePage";

export type SystemPropertyValue = "ALLOWED" | "DENIED_BY_DEFAULT" | "DENIED";

/**
 * The System Config page at `/system/config`, System Settings section.
 *
 * This is a jQuery/Mustache-rendered legacy page with no ARIA roles and no
 * data-test-id attributes. CSS class selectors are used where unavoidable;
 * `data-name` on each setting row acts as the stable anchor.
 *
 */
export class SystemConfigPage extends BasePage {
  readonly path = "/system/config";

  override async open(): Promise<void> {
    await this.page.goto(this.path);
    await this.page.getByRole("link", { name: "System Settings" }).click();
    await this.page.getByText("Loading Settings page...").waitFor({ state: "hidden" });
    await this.settingRow("api.available").waitFor({ state: "visible" });
  }

  async getSetting(name: string): Promise<string> {
    return this.settingRow(name).locator(".settingViewDiv .settingValue").innerText();
  }

  async ensureSetting(name: string, value: SystemPropertyValue): Promise<void> {
    if ((await this.getSetting(name)).trim() !== value) {
      await this.setSetting(name, value);
    }
  }

  async setSetting(name: string, value: SystemPropertyValue): Promise<void> {
    const row = this.settingRow(name);
    await row.locator(".settingViewDiv").click();
    await row.locator(".settingEditDiv").waitFor({ state: "visible" });
    await row.locator("select").selectOption(value);
    await row.getByRole("link", { name: "Save" }).click();
    await row.locator(".settingEditDiv").waitFor({ state: "hidden" });
  }

  private settingRow(name: string): Locator {
    return this.page.locator(`[data-name="${name}"]`);
  }
}
