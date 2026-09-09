import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../BasePage";

export class ExportImportPage extends BasePage {
  readonly path = "/import/archiveImport";

  private readonly ontologyForm: Locator;
  readonly ontologyFileChooser: Locator;
  readonly dataColumnInput: Locator;
  readonly uriColumnInput: Locator;
  readonly ontologyNameInput: Locator;
  readonly ontologyVersionInput: Locator;
  readonly ontologyImportButton: Locator;

  constructor(page: Page) {
    super(page);
    this.ontologyForm = page.locator("#importOntologyForm");
    this.ontologyFileChooser = this.ontologyForm.locator("#importOntologyFileChooser");
    this.dataColumnInput = this.ontologyForm.getByRole("spinbutton", { name: "Identify column which holds data" });
    this.uriColumnInput = this.ontologyForm.getByRole("spinbutton", {
      name: "Identify column which holds uris for data",
    });
    this.ontologyNameInput = this.ontologyForm.getByRole("textbox", { name: "Ontology name" });
    this.ontologyVersionInput = this.ontologyForm.getByRole("textbox", { name: "Ontology version" });
    this.ontologyImportButton = this.ontologyForm.getByRole("button", { name: "Import" });
  }

  async isLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Import an ontology file - csv format" }).waitFor({
      state: "visible",
    });
  }

  async importOntologyCsv(
    filePath: string,
    { dataColumn, uriColumn, name, version }: { dataColumn: string; uriColumn: string; name: string; version: string },
  ): Promise<void> {
    const [chooser] = await Promise.all([this.page.waitForEvent("filechooser"), this.ontologyFileChooser.click()]);
    await chooser.setFiles(filePath);
    await this.dataColumnInput.fill(dataColumn);
    await this.uriColumnInput.fill(uriColumn);
    await this.ontologyNameInput.fill(name);
    await this.ontologyVersionInput.fill(version);
    await Promise.all([this.page.waitForURL("**/workspace**"), this.ontologyImportButton.click()]);
  }
}
