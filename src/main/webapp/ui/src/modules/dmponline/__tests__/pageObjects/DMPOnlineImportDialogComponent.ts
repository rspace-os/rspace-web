import type { Locator, Page } from "@playwright/test";

export class DMPOnlineImportDialogComponent {
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
    await this.root.getByRole("columnheader", { name: "Title" }).waitFor({ state: "visible" });
  }

  async selectPlan(name: string): Promise<void> {
    await this.planRadio(name).click();
  }

  async selectFirstPlan(): Promise<string> {
    const radio = this.plansGrid.getByRole("radio").first();
    const label = await radio.getAttribute("aria-label");
    if (!label?.startsWith("Select plan: ")) throw new Error("The first DMPonline plan has no accessible name.");
    await radio.click();
    return label.replace("Select plan: ", "");
  }

  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/apps/dmponline/importPlan",
      ),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /apps/dmponline/importPlan failed: ${response.status()} ${response.statusText()}`);
    }
  }
}
