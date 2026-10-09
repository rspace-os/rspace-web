import type { Locator, Page } from "@playwright/test";
import type { FilestoreCredentials } from "@/__tests__/e2e/components/gallery/FilestoreLoginDialog";
import { responseTo } from "@/__tests__/e2e/responses";

export class FilestoreExportStepComponent {
  readonly root: Locator;
  readonly scanResults: Locator;
  private readonly loginDialog: Locator;
  private readonly filtersDialog: Locator;

  constructor(private readonly page: Page) {
    this.root = page.getByRole("dialog", { name: "Filestore Links Export Configuration" });
    this.scanResults = page.getByRole("dialog", { name: "Availability scan results" });
    this.loginDialog = page.getByRole("dialog", { name: "File Systems login status" });
    this.filtersDialog = page.getByRole("dialog", { name: "Filtering options for filestore files" });
  }

  async waitForOpen(): Promise<void> {
    await this.root.waitFor({ state: "visible" });
  }

  async loginToFileSystems(credentials: FilestoreCredentials | undefined): Promise<void> {
    if (!credentials) {
      return;
    }
    await this.root.getByRole("button", { name: "Login to remaining File Systems" }).click();
    await this.loginDialog.getByRole("textbox", { name: "Username" }).fill(credentials.username);
    await this.loginDialog.getByRole("textbox", { name: "Password" }).fill(credentials.password);
    const response = await responseTo(
      this.page,
      "POST",
      ({ pathname }) => pathname === "/netFiles/ajax/nfsLoginJson",
      () => this.loginDialog.getByRole("button", { name: "Login" }).click(),
    );
    const body = await response.text();
    if (!body.startsWith("logged.as.")) {
      throw new Error(`Export filestore login failed: ${body}`);
    }
    await this.loginDialog.waitFor({ state: "hidden" });
    await this.root.getByText("You are logged into all File Systems referenced by filestore links.").waitFor();
  }

  async excludeFileTypes(extensions: string): Promise<void> {
    await this.root.getByRole("button", { name: "Change file filtering options" }).click();
    await this.filtersDialog
      .getByRole("textbox", { name: "File types to exclude (comma-separated list)" })
      .fill(extensions);
    await this.filtersDialog.getByRole("button", { name: "OK" }).click();
    await this.filtersDialog.waitFor({ state: "hidden" });
  }

  async scan(): Promise<void> {
    await this.root.getByRole("button", { name: "Scan filestore links" }).click();
    await this.scanResults.waitFor({ state: "visible" });
  }

  skippedFile(fullPath: string): Locator {
    return this.scanResultRow("Reason", fullPath);
  }

  includedFile(fullPath: string): Locator {
    return this.scanResultRow("Size", fullPath);
  }

  async closeScanResults(): Promise<void> {
    await this.scanResults.getByRole("button", { name: "OK" }).click();
    await this.scanResults.waitFor({ state: "hidden" });
  }

  async export(): Promise<void> {
    await responseTo(
      this.page,
      "POST",
      ({ pathname }) => pathname === "/export/ajax/exportArchive",
      () => this.root.getByRole("button", { name: "Export", exact: true }).click(),
    );
    await this.root.waitFor({ state: "hidden" });
  }

  private scanResultRow(distinguishingColumn: "Reason" | "Size", fullPath: string): Locator {
    return this.scanResults
      .getByRole("table")
      .filter({ has: this.page.getByRole("columnheader", { name: distinguishingColumn, exact: true }) })
      .getByRole("row")
      .filter({ has: this.page.getByRole("rowheader", { name: fullPath, exact: true }) });
  }
}
