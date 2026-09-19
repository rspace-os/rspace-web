import type { Locator, Page } from "@playwright/test";

export class ArgosImportDialogComponent {
  readonly root: Locator;
  readonly importButton: Locator;
  private readonly plansGrid: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import a DMP into the Gallery" });
    this.importButton = this.root.getByRole("button", { name: "Import" });
    this.plansGrid = this.root.getByRole("grid");
  }

  planRadio(name: string): Locator {
    return this.plansGrid.getByRole("radio", { name: `Select plan: ${name}` });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.root.getByRole("columnheader", { name: "Label" }).waitFor({ state: "visible" });
  }

  async selectPlan(name: string): Promise<void> {
    await this.planRadio(name).click();
  }

  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname.includes("/apps/argos/importPlan/"),
      ),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /apps/argos/importPlan failed: ${response.status()} ${response.statusText()}`);
    }
  }
}
