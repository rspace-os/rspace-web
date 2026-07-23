import type { Locator } from "@playwright/test";
import { BasePage } from "../BasePage";

export type AuditAction =
  | "CREATE"
  | "DELETE"
  | "DOWNLOAD"
  | "DUPLICATE"
  | "EXPORT"
  | "MOVE"
  | "READ"
  | "RENAME"
  | "RESTORE"
  | "SEARCH"
  | "SHARE"
  | "SIGN"
  | "TRANSFER"
  | "UNSHARE"
  | "VIEW"
  | "WITNESSED"
  | "WRITE";

export class AuditTrailPage extends BasePage {
  readonly path = "/audit/auditing";

  private get submitButton(): Locator {
    return this.page.getByRole("button", { name: "Get Audit Report" });
  }

  private get globalIdInput(): Locator {
    return this.page.getByRole("textbox", { name: "Enter a global id" });
  }

  async isLoaded(): Promise<void> {
    await this.submitButton.waitFor({ state: "visible" });
  }

  async filterByGlobalId(globalId: string): Promise<void> {
    if (!(await this.globalIdInput.isVisible())) {
      await this.page.getByRole("link", { name: "Identifiers" }).click();
      await this.globalIdInput.waitFor({ state: "visible" });
    }
    await this.globalIdInput.fill(globalId);
  }

  async checkAction(action: AuditAction): Promise<void> {
    const checkbox = this.page.getByRole("checkbox", { name: action, exact: true });
    if (!(await checkbox.isVisible())) {
      await this.page.getByRole("link", { name: "Actions" }).click();
      await checkbox.waitFor({ state: "visible" });
    }
    await checkbox.check();
  }

  async submitQuery(): Promise<void> {
    await Promise.all([
      this.page.waitForResponse((res) => res.url().includes("/audit/query")),
      this.submitButton.click(),
    ]);
  }

  get resultRows(): Locator {
    return this.page.locator("#renderedTable tbody tr").filter({ has: this.page.locator("td") });
  }

  rowsWithName(name: string): Locator {
    return this.resultRows.filter({ hasText: name });
  }
}
