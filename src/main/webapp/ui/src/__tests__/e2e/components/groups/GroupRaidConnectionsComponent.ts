import type { Locator, Page } from "@playwright/test";

export class GroupRaidConnectionsComponent {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    // #raid-connections is the legacy JSP mount point
    this.root = page.locator("#raid-connections");
  }

  async waitForLoaded(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async isAssociated(): Promise<boolean> {
    return (await this.root.getByRole("button", { name: "Disassociate" }).count()) > 0;
  }

  connectionLabel(label: string): Locator {
    return this.root.getByText(label, { exact: true });
  }

  async addRaidIdentifier(label: string): Promise<void> {
    await this.root.getByRole("button", { name: "Add", exact: true }).click();

    const combobox = this.root.getByRole("combobox", { name: "RAiD Identifier" });
    await combobox.click();
    await combobox.fill(label);
    await this.page.getByRole("option", { name: label, exact: true }).click();

    await this.root.getByRole("button", { name: "Add", exact: true }).click();
    await this.connectionLabel(label).waitFor({ state: "visible" });
  }

  async disassociate(): Promise<void> {
    await this.root.getByRole("button", { name: "Disassociate" }).click();
    const dialog = this.page.getByRole("dialog", { name: "Confirm Disassociation" });
    await dialog.getByRole("button", { name: "Confirm", exact: true }).click();
    await dialog.waitFor({ state: "hidden" });
  }
}
