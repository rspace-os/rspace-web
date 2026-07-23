import type { Locator, Page } from "@playwright/test";

export type InventorySidebarSection =
  | "Containers"
  | "Samples"
  | "Subsamples"
  | "Instruments"
  | "Sample Templates"
  | "Instrument Templates"
  | "IGSN IDs"
  | "Export Data"
  | "Settings";

const RESULT_TYPE_BY_SECTION: Partial<Record<InventorySidebarSection, string>> = {
  Containers: "CONTAINER",
  Samples: "SAMPLE",
  Subsamples: "SUBSAMPLE",
  Instruments: "INSTRUMENT",
  "Sample Templates": "SAMPLE_TEMPLATE",
  "Instrument Templates": "INSTRUMENT_TEMPLATE",
};

export class InventorySidebar {
  readonly root: Locator;
  readonly createButton: Locator;
  private readonly myBenchButton: Locator;
  private readonly openSidebarButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.locator('[aria-label="Inventory Sidebar Navigation"]');
    this.createButton = this.root.getByRole("button", { name: "Create", exact: true });

    this.myBenchButton = this.root.getByRole("button", { name: "My Bench" });
    this.openSidebarButton = page.getByRole("button", { name: "open sidebar" });
  }

  async ensureOpen(): Promise<void> {
    await this.root.or(this.openSidebarButton).first().waitFor({ state: "visible" });
    if (await this.openSidebarButton.isVisible()) {
      await this.openSidebarButton.click();
      await this.root.waitFor({ state: "visible" });
    }
  }

  async navigateToMyBench(): Promise<void> {
    await this.ensureOpen();
    await Promise.all([
      this.page.waitForURL(
        (url) => url.pathname.endsWith("/inventory/search") && url.searchParams.has("parentGlobalId"),
      ),
      this.myBenchButton.click(),
    ]);
  }

  async navigateTo(section: InventorySidebarSection): Promise<void> {
    await this.ensureOpen();
    const button = this.root.getByRole("button", { name: section, exact: true });
    if (section === "IGSN IDs") {
      await Promise.all([this.page.waitForURL("**/inventory/identifiers/igsn**"), button.click()]);
      return;
    }
    if (section === "Export Data") {
      await button.click();
      await this.page.getByRole("dialog", { name: "Export Options" }).waitFor({ state: "visible" });
      return;
    }
    if (section === "Settings") {
      await button.click();
      await this.page
        .getByRole("dialog", { name: "Configure Inventory (for System Administrators)" })
        .waitFor({ state: "visible" });
      return;
    }
    const resultType = RESULT_TYPE_BY_SECTION[section];
    await Promise.all([
      this.page.waitForURL(
        (url) => url.pathname.endsWith("/inventory/search") && url.searchParams.get("resultType") === resultType,
      ),
      button.click(),
    ]);
  }
}
