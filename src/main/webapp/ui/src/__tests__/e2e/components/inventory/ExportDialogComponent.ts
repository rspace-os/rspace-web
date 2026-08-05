import type { Locator, Page } from "@playwright/test";
import type { ApiInventoryExportJob } from "@/__tests__/e2e/api/models/inventoryExport";
import { clickAndWaitDetached } from "./DialogHelpers";

export type ExportMode = "Full" | "Compact";
export type ExportFileType = "ZIP Bundle" | "Single CSV";

export type ExportContainerContent = "Include Content" | "Exclude Content";

export class ExportDialogComponent {
  readonly root: Locator;
  private readonly exportButton: Locator;
  private readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Export Options" });
    this.exportButton = this.root.getByRole("button", { name: "Export", exact: true });
    this.cancelButton = this.root.getByRole("button", { name: "Cancel" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async selectExportMode(mode: ExportMode): Promise<void> {
    await this.root.getByRole("radio", { name: mode, exact: true }).check();
  }

  async selectFileType(type: ExportFileType): Promise<void> {
    await this.root.getByRole("radio", { name: type, exact: true }).check();
  }

  async selectContainerContent(option: ExportContainerContent): Promise<void> {
    await this.root.getByRole("radio", { name: option, exact: true }).check();
  }

  async export(): Promise<ApiInventoryExportJob> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (res) => res.url().includes("/api/inventory/v1/export") && res.request().method() === "POST",
      ),
      this.exportButton.click(),
    ]);
    return response.json() as Promise<ApiInventoryExportJob>;
  }

  async cancel(): Promise<void> {
    await clickAndWaitDetached(this.cancelButton, this.root);
  }
}
