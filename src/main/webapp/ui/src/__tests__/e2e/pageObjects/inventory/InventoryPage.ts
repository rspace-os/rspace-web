import type { Locator, Page } from "@playwright/test";
import { openDialog } from "@/__tests__/e2e/components/inventory/DialogHelpers";
import { ExportDialogComponent } from "@/__tests__/e2e/components/inventory/ExportDialogComponent";
import {
  InventoryCreateMenu,
  type InventoryCsvImportItem,
} from "@/__tests__/e2e/components/inventory/InventoryCreateMenu";
import { InventoryDetailsPanel } from "@/__tests__/e2e/components/inventory/InventoryDetailsPanel";
import { InventorySearchPanel } from "@/__tests__/e2e/components/inventory/InventorySearchPanel";
import { InventorySidebar } from "@/__tests__/e2e/components/inventory/InventorySidebar";
import { FieldmarkDialogComponent } from "@/modules/fieldmark/__tests__/pageObjects/FieldmarkDialogComponent";
import { BasePage } from "../BasePage";
import { InventoryImportPage } from "./InventoryImportPage";

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

  async openSearch(resultType: "CONTAINER" | "SAMPLE" | "SUBSAMPLE", parentGlobalId?: string): Promise<void> {
    const search = new URLSearchParams({ resultType });
    if (parentGlobalId) search.set("parentGlobalId", parentGlobalId);
    await this.page.goto(`/inventory/search?${search}`);
    await this.isLoaded();
  }

  async openRecord(resultType: "CONTAINER" | "SAMPLE" | "SUBSAMPLE", name: string): Promise<void> {
    await this.openSearch(resultType);
    await this.searchPanel.search(name);
    await this.searchPanel.open(name);
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

  async openCsvImport(item: InventoryCsvImportItem): Promise<InventoryImportPage> {
    const menu = await this.openCreateMenu();
    await menu.clickCsvImport(item);
    const importPage = new InventoryImportPage(this.page);
    await importPage.isLoaded();
    return importPage;
  }

  async openExportData(): Promise<ExportDialogComponent> {
    return openDialog(() => this.sidebar.navigateTo("Export Data"), new ExportDialogComponent(this.page));
  }
}
