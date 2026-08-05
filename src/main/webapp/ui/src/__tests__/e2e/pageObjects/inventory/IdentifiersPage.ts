import type { Locator, Page } from "@playwright/test";
import { clickAndWaitDetached } from "@/__tests__/e2e/components/inventory/DialogHelpers";
import { BasePage } from "../BasePage";

export class IdentifiersPage extends BasePage {
  readonly path = "/inventory/identifiers/igsn";

  private readonly main: Locator;
  readonly bulkRegisterButton: Locator;
  readonly searchInput: Locator;
  readonly grid: Locator;
  readonly exportButton: Locator;
  readonly integrationNotEnabledAlert: Locator;

  constructor(page: Page) {
    super(page);
    this.main = page.getByRole("main", { name: "IGSN management main content" });
    this.bulkRegisterButton = this.main.getByRole("button", { name: "Bulk Register" });
    this.searchInput = this.main.getByRole("searchbox", { name: "Search IGSN IDs..." });
    this.grid = this.main.getByRole("grid");
    this.exportButton = this.main.getByRole("button", { name: "Export" });
    this.integrationNotEnabledAlert = page.getByRole("alert", { name: "IGSN integration is not enabled" });
  }

  async isLoaded(): Promise<void> {
    await this.main.getByRole("heading", { level: 2, name: "IGSN Management Page" }).waitFor({ state: "visible" });
  }

  async isIntegrationEnabled(): Promise<boolean> {
    return (await this.integrationNotEnabledAlert.count()) === 0;
  }

  async bulkRegister(count = 1): Promise<void> {
    await this.bulkRegisterButton.click();
    const dialog = this.page.getByRole("dialog", { name: "Bulk Register IGSN IDs" });
    await dialog.getByRole("spinbutton", { name: "Number of new IGSN IDs" }).fill(String(count));
    await clickAndWaitDetached(dialog.getByRole("button", { name: "Register" }), dialog);
  }

  row(doi: string): Locator {
    return this.grid.getByRole("row", { name: doi });
  }
}
