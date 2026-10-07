import type { Locator } from "@playwright/test";
import { ExportWizardComponent } from "@/__tests__/e2e/components/shared/ExportWizardComponent";
import { BasePage } from "../BasePage";

export class ExportImportPage extends BasePage {
  readonly path = "/import/archiveImport";
  static readonly TITLE = "Import and Export Archives | ResearchSpace";

  async waitUntilLoaded(): Promise<void> {
    await this.page.getByRole("heading", { name: "Export all" }).waitFor({ state: "visible" });
  }

  get exportAllButton(): Locator {
    return this.page.getByRole("link", { name: "Export all my work" });
  }

  async isExportAllButtonVisible(): Promise<boolean> {
    return this.exportAllButton.isVisible();
  }

  async exportAll(): Promise<ExportWizardComponent> {
    await this.exportAllButton.click();
    const wizard = new ExportWizardComponent(this.page);
    await wizard.waitForOpen();
    return wizard;
  }
}
