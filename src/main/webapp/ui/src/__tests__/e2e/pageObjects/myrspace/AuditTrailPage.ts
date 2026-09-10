import type { Locator, Page } from "@playwright/test";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { BasePage } from "../BasePage";

export type AuditDomain = "ELN" | "Inventory" | "Other";

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
  readonly header: AppHeader;

  constructor(page: Page) {
    super(page);
    this.header = new AppHeader(page);
  }

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

  async setDomains(domains: AuditDomain[]): Promise<void> {
    const first = this.page.getByRole("checkbox", { name: "ELN", exact: true });
    if (!(await first.isVisible())) {
      await this.page.getByRole("link", { name: "Activity areas" }).click();
      await first.waitFor({ state: "visible" });
    }
    for (const domain of ["ELN", "Inventory", "Other"] as const) {
      const checkbox = this.page.getByRole("checkbox", { name: domain, exact: true });
      if (domains.includes(domain)) {
        await checkbox.check();
      } else {
        await checkbox.uncheck();
      }
    }
  }

  async filterByDateRange(from?: string, to?: string): Promise<void> {
    const fromInput = this.page.getByRole("textbox", { name: "from", exact: true });
    if (!(await fromInput.isVisible())) {
      await this.page.getByRole("link", { name: "Date range" }).click();
      await fromInput.waitFor({ state: "visible" });
    }
    if (from !== undefined) await fromInput.fill(from);
    if (to !== undefined) await this.page.getByRole("textbox", { name: "to", exact: true }).fill(to);
  }

  async filterByUser(username: string): Promise<void> {
    const userInput = this.page.getByRole("textbox", { name: "Enter a user or users to audit" });
    if (!(await userInput.isVisible())) {
      await this.page.getByRole("link", { name: "Users", exact: true }).click();
      await userInput.waitFor({ state: "visible" });
    }
    await userInput.fill(username);
    const suggestions = this.page.locator(".ui-autocomplete:visible");
    await suggestions.getByRole("listitem").filter({ hasText: username }).first().click();
  }

  async downloadReport(): Promise<string> {
    // Every engine hides the payload somewhere different: Chromium/Firefox hand it over as a
    // "download" then shred the response body, WebKit keeps the body but never says "download".
    const context = this.page.context();
    const settleTimeout = 15_000;
    const downloadPromise = context.waitForEvent("download", { timeout: settleTimeout }).catch(() => null);
    const responsePromise = context
      .waitForEvent("response", {
        predicate: (res) => res.url().includes("/audit/download"),
        timeout: settleTimeout,
      })
      .catch(() => null);

    await this.page.getByRole("button", { name: "Download Audit Report" }).click();

    const response = await responsePromise;
    if (response) {
      try {
        return await response.text();
      } catch {
        // Fall through to the download event below.
      }
    }
    const download = await downloadPromise;
    if (!download) throw new Error("downloadReport: neither a response body nor a download event was available.");
    const stream = await download.createReadStream();
    if (!stream) throw new Error("downloadReport: download had no read stream.");
    const chunks: Buffer[] = [];
    for await (const chunk of stream) chunks.push(chunk as Buffer);
    return Buffer.concat(chunks).toString("utf-8");
  }

  async hitCount(): Promise<number> {
    const text = await this.page.getByText("You found", { exact: false }).innerText();
    const match = text.match(/\d+/);
    if (!match) {
      throw new Error(`hitCount: could not find a number in hits text "${text}"`);
    }
    return Number(match[0]);
  }

  get resultRows(): Locator {
    return this.page.locator("#renderedTable tbody tr").filter({ has: this.page.locator("td") });
  }

  rowsWithName(name: string): Locator {
    return this.resultRows.filter({ hasText: name });
  }

  resourceLink(name: string): Locator {
    return this.rowsWithName(name).first().getByRole("link");
  }
}
