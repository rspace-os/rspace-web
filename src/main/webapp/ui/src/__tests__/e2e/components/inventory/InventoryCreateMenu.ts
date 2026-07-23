import type { Locator, Page } from "@playwright/test";
import { openDialog } from "./DialogHelpers";
import { type NewContainerFormComponent, newContainerFormComponent } from "./NewContainerFormComponent";
import { type NewInstrumentFormComponent, newInstrumentFormComponent } from "./NewInstrumentFormComponent";
import { type NewSampleFormComponent, newSampleFormComponent } from "./NewSampleFormComponent";
import { type NewTemplateFormComponent, newTemplateFormComponent } from "./NewTemplateFormComponent";

export type InventoryCreateMenuItem =
  | "New Sample"
  | "New Container"
  | "New Instrument"
  | "New Template"
  | "New Instrument Template";

export type InventoryCsvImportItem = "Samples" | "Subsamples" | "Containers";

export class InventoryCreateMenu {
  readonly root: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("menu");
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  private menuItem(name: string): Locator {
    return this.root.getByRole("menuitem", { name, exact: true });
  }

  async hasFieldmarkImport(): Promise<boolean> {
    return (await this.menuItem("Fieldmark").count()) > 0;
  }

  async click(item: InventoryCreateMenuItem): Promise<void> {
    await this.menuItem(item).click();
  }

  async clickCsvImport(item: InventoryCsvImportItem): Promise<void> {
    await this.menuItem(item).click();
  }

  async newSample(): Promise<NewSampleFormComponent> {
    return openDialog(() => this.click("New Sample"), newSampleFormComponent(this.page));
  }

  async newContainer(): Promise<NewContainerFormComponent> {
    return openDialog(() => this.click("New Container"), newContainerFormComponent(this.page));
  }

  async newInstrument(): Promise<NewInstrumentFormComponent> {
    return openDialog(() => this.click("New Instrument"), newInstrumentFormComponent(this.page));
  }

  async newSampleTemplate(): Promise<NewTemplateFormComponent> {
    return openDialog(() => this.click("New Template"), newTemplateFormComponent(this.page, "New Template"));
  }

  async newInstrumentTemplate(): Promise<NewTemplateFormComponent> {
    return openDialog(
      () => this.click("New Instrument Template"),
      newTemplateFormComponent(this.page, "New Instrument Template"),
    );
  }
}
