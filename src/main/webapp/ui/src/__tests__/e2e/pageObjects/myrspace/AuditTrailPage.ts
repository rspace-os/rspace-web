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
    await this.page.getByRole("listitem").filter({ hasText: username }).first().click();
  }

  async downloadReport(): Promise<string> {
    const context = this.page.context();

    const TIMEOUT_MS = 10_000;
    const downloadPromise = this.page.waitForEvent("download", { timeout: TIMEOUT_MS }).then(async (download) => {
      const stream = await download.createReadStream();
      if (!stream) throw new Error("downloadReport: download had no read stream.");
      const chunks: Buffer[] = [];
      for await (const chunk of stream) chunks.push(chunk as Buffer);
      return Buffer.concat(chunks).toString("utf-8");
    });
    const popupPromise = context.waitForEvent("page", { timeout: TIMEOUT_MS }).then(async (popup) => {
      await popup.waitForLoadState();
      const text = await popup.evaluate(() => document.body.innerText);
      await popup.close();
      return text;
    });

    downloadPromise.catch(() => {});
    popupPromise.catch(() => {});

    const [text] = await Promise.all([
      Promise.race([downloadPromise, popupPromise]),
      this.page.getByRole("button", { name: "Download Audit Report" }).click(),
    ]);
    return text;
  }

  async hitCount(): Promise<number> {
    const text = await this.page.getByText("You found", { exact: false }).innerText();
    const count = Number(text.trim().split(" ")[2]);
    if (!Number.isInteger(count)) {
      throw new Error(`hitCount: could not parse hits text "${text}"`);
    }
    return count;
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
