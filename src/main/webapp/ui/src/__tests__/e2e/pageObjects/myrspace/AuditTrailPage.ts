import type { Locator, Page } from "@playwright/test";
import { AppHeader } from "@/__tests__/e2e/components/shared/AppHeader";
import { innerTexts } from "@/__tests__/e2e/locatorTexts";
import { BasePage } from "../BasePage";

export type AuditDomain = "ELN" | "Inventory" | "Requests" | "Other";

export type AuditAction =
  | "CREATE"
  | "DELETE"
  | "DOWNLOAD"
  | "DUPLICATE"
  | "EXPORT"
  | "MOVE"
  | "READ"
  | "RENAME"
  | "REQUEST_APPROVED"
  | "REQUEST_CANCELLED"
  | "REQUEST_FULFILLED"
  | "REQUEST_REJECTED"
  | "REQUEST_SENT"
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

  private async openSection(sectionName: string, indicator: Locator, exact = false): Promise<void> {
    if (!(await indicator.isVisible())) {
      await this.page.getByRole("link", { name: sectionName, exact }).click();
      await indicator.waitFor({ state: "visible" });
    }
  }

  async filterByGlobalId(globalId: string): Promise<void> {
    await this.openSection("Identifiers", this.globalIdInput);
    await this.globalIdInput.fill(globalId);
  }

  async checkAction(action: AuditAction): Promise<void> {
    const checkbox = this.page.getByRole("checkbox", { name: action, exact: true });
    await this.openSection("Actions", checkbox);
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
    await this.openSection("Activity areas", first);
    for (const domain of ["ELN", "Inventory", "Requests", "Other"] as const) {
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
    await this.openSection("Date range", fromInput);
    if (from !== undefined) await fromInput.fill(from);
    if (to !== undefined) await this.page.getByRole("textbox", { name: "to", exact: true }).fill(to);
  }

  async filterByUser(username: string): Promise<void> {
    const userInput = this.page.getByRole("textbox", { name: "Enter a user or users to audit" });
    await this.openSection("Users", userInput, true);
    await userInput.fill(username);
    const suggestions = this.page.locator(".ui-autocomplete:visible");
    await suggestions.getByRole("listitem").filter({ hasText: username }).first().click();
  }

  async downloadReport(): Promise<string> {
    // The button opens /audit/download in a new tab. Engines differ in whether that response keeps
    // its body or becomes a download, and WebKit 26.6 (Playwright 1.65) never finishes the
    // download's stream. Fetch the routed request instead, then pass the response on unchanged.
    const context = this.page.context();
    const url = (target: URL) => target.pathname === "/audit/download";
    const { promise: report, resolve, reject } = Promise.withResolvers<string>();
    await context.route(url, async (route) => {
      try {
        const response = await route.fetch();
        const body = await response.text();
        resolve(body);
        await route.fulfill({ response, body });
      } catch (error) {
        reject(error);
        throw error;
      }
    });
    try {
      await this.page.getByRole("button", { name: "Download Audit Report" }).click();
      return await report;
    } finally {
      await context.unroute(url);
    }
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

  private async columnIndex(headerName: string): Promise<number> {
    const headers = await innerTexts(this.page.locator("#renderedTable").getByRole("columnheader"));
    const index = headers.findIndex((header) => header.trim() === headerName);
    if (index === -1) throw new Error(`columnIndex: no "${headerName}" column header found`);
    return index;
  }

  async actionForRow(name: string): Promise<string> {
    const columnIndex = await this.columnIndex("Action");
    return (await this.rowsWithName(name).first().getByRole("cell").nth(columnIndex).innerText()).trim();
  }

  resourceLink(name: string): Locator {
    return this.rowsWithName(name).first().getByRole("link");
  }
}
