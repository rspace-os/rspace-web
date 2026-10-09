import type { Locator, Page } from "@playwright/test";
import { FileSystemsComponent } from "@/__tests__/e2e/components/system/config/FileSystemsComponent";
import { IpWhitelistComponent } from "@/__tests__/e2e/components/system/config/IpWhitelistComponent";
import { RorRegistryComponent } from "@/__tests__/e2e/components/system/config/RorRegistryComponent";
import { BasePage } from "../BasePage";

export type SystemPropertyValue = "ALLOWED" | "DENIED_BY_DEFAULT" | "DENIED";
type ConfigPanel = "System Settings" | "Sysadmin IP White List" | "Institutional File Systems" | "ROR Registry";

export class SystemConfigPage extends BasePage {
  readonly path = "/system/config";
  readonly ipWhitelist: IpWhitelistComponent;
  readonly fileSystems: FileSystemsComponent;
  readonly rorRegistry: RorRegistryComponent;

  constructor(page: Page) {
    super(page);
    this.ipWhitelist = new IpWhitelistComponent(page);
    this.fileSystems = new FileSystemsComponent(page);
    this.rorRegistry = new RorRegistryComponent(page);
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

  async openFileSystems(): Promise<FileSystemsComponent> {
    await this.openPanel("Institutional File Systems");
    await this.fileSystems.waitUntilLoaded();
    return this.fileSystems;
  }

  async openRorRegistry(): Promise<RorRegistryComponent> {
    await this.openPanel("ROR Registry");
    await this.rorRegistry.waitUntilLoaded();
    return this.rorRegistry;
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
