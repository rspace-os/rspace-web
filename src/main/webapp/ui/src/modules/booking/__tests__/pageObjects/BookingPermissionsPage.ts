import type { Locator, Page } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class BookingPermissionsPage extends BasePage {
  readonly path = "/booking/bookable-items";

  readonly accessTab: Locator;
  readonly accessPanel: Locator;

  constructor(page: Page) {
    super(page);
    this.accessTab = page.getByRole("tab", { name: "Access" });
    this.accessPanel = page.getByRole("tabpanel", { name: "Access" });
  }

  async openRecord(globalId: string): Promise<void> {
    await this.page.goto(`${this.path}/${globalId}`);
    await this.page.getByText(globalId, { exact: true }).waitFor();
  }

  async openAccess(): Promise<void> {
    await this.accessTab.click();
    await this.accessPanel.waitFor();
  }
}
