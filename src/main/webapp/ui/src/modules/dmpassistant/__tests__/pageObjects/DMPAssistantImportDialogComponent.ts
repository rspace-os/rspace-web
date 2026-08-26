import type { Locator, Page } from "@playwright/test";

export class DMPAssistantImportDialogComponent {
  readonly root: Locator;
  readonly importButton: Locator;
  private readonly plansBody: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import DMPs into the Gallery" });
    this.importButton = this.root.getByRole("button", { name: "Import", exact: true });
    this.plansBody = this.root.getByRole("grid").getByRole("rowgroup");
  }

  planCheckbox(name: string): Locator {
    return this.plansBody.getByRole("checkbox", { name: `Select plan: ${name}` });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
    await this.root.getByRole("columnheader", { name: "Title" }).waitFor({ state: "visible" });
  }

  async selectPlan(name: string): Promise<void> {
    await this.planCheckbox(name).click();
  }

  async selectFirstPlan(): Promise<string> {
    const checkbox = this.plansBody.getByRole("checkbox").first();
    const label = await checkbox.getAttribute("aria-label");
    if (!label?.startsWith("Select plan: ")) throw new Error("The first DMP Assistant plan has no accessible name.");
    await checkbox.click();
    return label.replace("Select plan: ", "");
  }

  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname === "/apps/dmpassistant/importPlans",
      ),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /apps/dmpassistant/importPlans failed: ${response.status()} ${response.statusText()}`);
    }
  }
}
