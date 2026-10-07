import type { Locator, Page } from "@playwright/test";
import { openDialog } from "@/__tests__/e2e/components/inventory/DialogHelpers";
import { ExportDialogComponent } from "@/__tests__/e2e/components/inventory/ExportDialogComponent";
import { IdentifierSettingsDialog } from "@/__tests__/e2e/components/inventory/IdentifierSettingsDialog";
import { InventoryCreateMenu } from "@/__tests__/e2e/components/inventory/InventoryCreateMenu";
import { InventoryDetailsPanel } from "@/__tests__/e2e/components/inventory/InventoryDetailsPanel";
import { InventorySearchPanel } from "@/__tests__/e2e/components/inventory/InventorySearchPanel";
import { InventorySidebar } from "@/__tests__/e2e/components/inventory/InventorySidebar";
import { isWholeNumberSegment } from "@/__tests__/e2e/pathSegments";
import { FieldmarkDialogComponent } from "@/modules/fieldmark/__tests__/pageObjects/FieldmarkDialogComponent";
import { BasePage } from "../BasePage";
import { InventoryImportPage, type InventoryImportRecordType } from "./InventoryImportPage";

type InventoryResultType = "CONTAINER" | "SAMPLE" | "SUBSAMPLE" | "INSTRUMENT" | "INSTRUMENT_TEMPLATE";

const PIDINST_ENABLED_PATH = "/api/inventory/v1/identifiers/pidinstEnabled/";

export class InventoryPage extends BasePage {
  readonly path = "/inventory";

  readonly sidebar: InventorySidebar;
  readonly searchPanel: InventorySearchPanel;
  readonly detailsPanel: InventoryDetailsPanel;
  private readonly heading: Locator;

  constructor(page: Page) {
    super(page);
    this.sidebar = new InventorySidebar(page);
    this.searchPanel = new InventorySearchPanel(page);
    this.detailsPanel = new InventoryDetailsPanel(page);
    this.heading = page.getByRole("heading", { level: 1, name: "Inventory" });
  }

  async isLoaded(): Promise<void> {
    await this.heading.waitFor({ state: "visible" });
  }

  override async open(): Promise<void> {
    await super.open();
    await this.isLoaded();
  }

  // The create menu hides PIDINST import until the app's separate flag request resolves,
  // so absence of the menu item is only meaningful once that response has arrived.
  async openAndReadPidinstEnabled(): Promise<boolean> {
    const flagResponse = this.page.waitForResponse(
      (r) => r.request().method() === "GET" && new URL(r.url()).pathname === PIDINST_ENABLED_PATH,
    );
    await this.open();
    const response = await flagResponse;
    if (!response.ok()) {
      throw new Error(`pidinstEnabled flag request failed: ${response.status()} ${response.statusText()}`);
    }
    return (await response.json()) as boolean;
  }

  async openSearch(resultType: InventoryResultType, parentGlobalId?: string): Promise<void> {
    const search = new URLSearchParams({ resultType });
    if (parentGlobalId) search.set("parentGlobalId", parentGlobalId);
    await this.page.goto(`${this.path}/search?${search}`);
    await this.isLoaded();
  }

  async openRecord(resultType: InventoryResultType, name: string): Promise<void> {
    await this.openSearch(resultType);
    await this.searchPanel.search(name);
    await this.searchPanel.open(name);
  }

  async openInstrument(id: string | number): Promise<void> {
    await this.page.goto(`${this.path}/instrument/${id}`);
    await this.isLoaded();
  }

  /**
   * Runs `trigger` and waits for it to land on a different instrument's page than the current one -
   * the new id isn't known ahead of time - then returns that id. Recording the URL first keeps a
   * caller already on an instrument page from getting the old id back.
   */
  async waitForNewInstrumentPage(trigger: () => Promise<void>): Promise<number> {
    const instrumentPrefix = `${this.path}/instrument/`;
    const instrumentIdOf = (pathname: string): number | undefined => {
      const id = pathname.startsWith(instrumentPrefix) ? pathname.slice(instrumentPrefix.length) : "";
      return isWholeNumberSegment(id) ? Number(id) : undefined;
    };
    const before = new URL(this.page.url()).pathname;
    await trigger();
    await this.page.waitForURL((url) => url.pathname !== before && instrumentIdOf(url.pathname) !== undefined);
    const id = instrumentIdOf(new URL(this.page.url()).pathname);
    if (id === undefined) throw new Error(`Expected an instrument page, got ${this.page.url()}`);
    return id;
  }

  async openNewContainerForm() {
    await this.open();
    return (await this.openCreateMenu()).newContainer();
  }

  async openCreateMenu(): Promise<InventoryCreateMenu> {
    return openDialog(async () => {
      await this.sidebar.ensureOpen();
      await this.sidebar.createButton.click();
    }, new InventoryCreateMenu(this.page));
  }

  async openFieldmarkImport(): Promise<FieldmarkDialogComponent> {
    await this.openCreateMenu();
    return openDialog(
      () => this.page.getByRole("menuitem", { name: "Fieldmark" }).click(),
      new FieldmarkDialogComponent(this.page),
    );
  }

  async openCsvImport(tab: InventoryImportRecordType): Promise<InventoryImportPage> {
    const menu = await this.openCreateMenu();
    await menu.clickCsvImport();
    const importPage = new InventoryImportPage(this.page);
    await importPage.isLoaded();
    if (tab !== "SAMPLES") await importPage.selectTab(tab);
    return importPage;
  }

  async openExportData(): Promise<ExportDialogComponent> {
    return openDialog(() => this.sidebar.navigateTo("Export Data"), new ExportDialogComponent(this.page));
  }

  async openIdentifierSettings(): Promise<IdentifierSettingsDialog> {
    return openDialog(() => this.sidebar.navigateTo("Settings"), new IdentifierSettingsDialog(this.page));
  }
}
