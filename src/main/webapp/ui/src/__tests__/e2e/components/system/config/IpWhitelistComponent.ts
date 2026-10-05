import type { Locator, Page } from "@playwright/test";
import { AppriseAlertComponent } from "@/__tests__/e2e/components/system/AppriseAlertComponent";

type IpWhitelistMutation = "addIpAddress" | "updateIpAddress" | "removeIpAddress";

export class IpWhitelistComponent {
  private readonly root: Locator;
  private readonly heading: Locator;
  private readonly ipAddressField: Locator;
  private readonly descriptionField: Locator;
  private readonly addLink: Locator;
  private readonly alert: AppriseAlertComponent;

  constructor(private readonly page: Page) {
    // The Mustache panel has no landmark or accessible container around the list.
    this.root = page.locator("#ipAddressList");
    this.heading = this.root.getByRole("heading", { name: "Existing white-listed IP addresses" });
    this.ipAddressField = this.root.getByRole("textbox", { name: "Enter an IP address" });
    this.descriptionField = this.root.getByRole("textbox", { name: "Enter a display name" });
    this.addLink = this.root.getByRole("link", { name: "Add", exact: true });
    this.alert = new AppriseAlertComponent(page);
  }

  async waitUntilLoaded(): Promise<void> {
    await this.heading.waitFor();
  }

  row(ipAddress: string): Locator {
    return this.root.getByRole("row").filter({ has: this.page.getByRole("cell", { name: ipAddress, exact: true }) });
  }

  descriptionCell(ipAddress: string): Locator {
    return this.row(ipAddress).getByRole("cell").nth(1);
  }

  async add(ipAddress: string, description: string): Promise<void> {
    await this.fillNewEntry(ipAddress, description);
    await this.submit("addIpAddress", this.addLink);
    await this.row(ipAddress).waitFor();
  }

  async addExpectingError(ipAddress: string, description: string): Promise<AppriseAlertComponent> {
    await this.fillNewEntry(ipAddress, description);
    await this.addLink.click();
    await this.alert.waitUntilVisible();
    return this.alert;
  }

  async editDescription(ipAddress: string, description: string): Promise<void> {
    const row = this.row(ipAddress);
    await row.getByRole("link", { name: "Edit" }).click();
    const input = row.getByRole("textbox");
    await input.fill(description);
    await this.submit("updateIpAddress", row.getByRole("link", { name: "Save" }));
    await this.waitForReload(input);
  }

  async remove(ipAddress: string): Promise<void> {
    const row = this.row(ipAddress);
    await this.submit("removeIpAddress", row.getByRole("link", { name: "Remove" }));
    await this.waitForReload(row);
  }

  private async fillNewEntry(ipAddress: string, description: string): Promise<void> {
    await this.ipAddressField.fill(ipAddress);
    await this.descriptionField.fill(description);
  }

  // The page reloads the list on any 200, even when the body reports a validation error.
  private async submit(mutation: IpWhitelistMutation, trigger: Locator): Promise<void> {
    await trigger.waitFor();
    const responsePromise = this.page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname.startsWith(`/system/config/ajax/${mutation}`),
    );
    await trigger.click();
    const response = await responsePromise;
    const text = await response.text();
    if (!response.ok()) {
      throw new Error(`${mutation} failed: HTTP ${response.status()} ${text.slice(0, 300)}`);
    }
    const body = JSON.parse(text) as { success?: boolean };
    if (body.success !== true) {
      throw new Error(`${mutation} rejected: ${text}`);
    }
  }

  // `previous` leaves only when the list is emptied; the heading returns once it is re-rendered.
  private async waitForReload(previous: Locator): Promise<void> {
    await previous.waitFor({ state: "detached" });
    await this.waitUntilLoaded();
  }
}
