import type { Locator, Page } from "@playwright/test";

export class DSWImportDialogComponent {
  readonly root: Locator;
  readonly importButton: Locator;
  /**
   * The dismiss button. Its label depends on state -- "Cancel" while a plan is
   * selected, "Close" otherwise -- but a successful import calls
   * setSelectedPlan(null) in the same commit that raises the success toast, so
   * by the time `dismiss()` is reachable the label is deterministically
   * "Close".
   */
  readonly dismissButton: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Import a DMP into the Gallery" });
    this.importButton = this.root.getByRole("button", { name: "Import" });
    this.dismissButton = this.root.getByRole("button", { name: "Close", exact: true });
  }

  planRadio(name: string): Locator {
    return this.root.getByRole("radio", { name: `Select plan: ${name}` });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });

    await this.root.getByRole("columnheader", { name: "Name" }).waitFor({ state: "visible" });
  }

  async selectPlan(name: string): Promise<void> {
    await this.planRadio(name).click();
  }

  async selectFirstPlan(): Promise<string> {
    const radio = this.root.getByRole("radio").first();
    const label = await radio.getAttribute("aria-label");
    if (!label?.startsWith("Select plan: ")) throw new Error("The first DSW plan has no accessible name.");
    await radio.click();
    return label.replace("Select plan: ", "");
  }

  async dismiss(): Promise<void> {
    await this.dismissButton.click();
    await this.root.waitFor({ state: "detached" });
  }

  async clickImport(): Promise<void> {
    const [response] = await Promise.all([
      this.page.waitForResponse(
        (r) => r.request().method() === "POST" && new URL(r.url()).pathname.endsWith("/apps/dsw/importPlan"),
      ),
      this.importButton.click(),
    ]);
    if (!response.ok()) {
      throw new Error(`POST /apps/dsw/importPlan failed: ${response.status()} ${response.statusText()}`);
    }
  }
}
