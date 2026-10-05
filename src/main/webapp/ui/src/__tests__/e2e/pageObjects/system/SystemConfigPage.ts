import type { Locator, Page } from "@playwright/test";
import { IpWhitelistComponent } from "@/__tests__/e2e/components/system/config/IpWhitelistComponent";
import { BasePage } from "../BasePage";

export type SystemPropertyValue = "ALLOWED" | "DENIED_BY_DEFAULT" | "DENIED";
type ConfigPanel = "System Settings" | "Sysadmin IP White List";

export class SystemConfigPage extends BasePage {
  readonly path = "/system/config";
  readonly ipWhitelist: IpWhitelistComponent;

  constructor(page: Page) {
    super(page);
    this.ipWhitelist = new IpWhitelistComponent(page);
  }

  override async open(): Promise<void> {
    await this.openPanel("System Settings");
    await this.page.getByText("Loading Settings page...").waitFor({ state: "hidden" });
    await this.settingRow("api.available").waitFor({ state: "visible" });
  }

  async openIpWhitelist(): Promise<IpWhitelistComponent> {
    await this.openPanel("Sysadmin IP White List");
    await this.ipWhitelist.waitUntilLoaded();
    return this.ipWhitelist;
  }

  async getSetting(name: string): Promise<string> {
    // Legacy Mustache rows expose values only through these classes.
    return this.settingRow(name).locator(".settingViewDiv .settingValue").innerText();
  }

  async ensureSetting(name: string, value: SystemPropertyValue): Promise<void> {
    if ((await this.getSetting(name)).trim() !== value) {
      await this.setSetting(name, value);
    }
  }

  async ensureSettings(settings: Record<string, SystemPropertyValue>): Promise<void> {
    for (const [name, value] of Object.entries(settings)) {
      await this.ensureSetting(name, value);
    }
  }

  async setSetting(name: string, value: SystemPropertyValue): Promise<void> {
    const row = this.settingRow(name);
    // View/edit state has no semantic hook; these classes are stable within the data-name row.
    await row.locator(".settingViewDiv").click();
    await row.locator(".settingEditDiv").waitFor({ state: "visible" });
    await row.locator("select").selectOption(value);
    await row.getByRole("link", { name: "Save" }).click();
    await row.locator(".settingEditDiv").waitFor({ state: "hidden" });
  }

  private async openPanel(name: ConfigPanel): Promise<void> {
    await this.page.goto(this.path);
    await this.page.getByRole("link", { name }).click();
  }

  private settingRow(name: string): Locator {
    return this.page.locator(`[data-name="${name}"]`);
  }
}
