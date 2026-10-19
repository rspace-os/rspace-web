import type { Buffer } from "node:buffer";
import { type Download, expect, type Locator, type Page } from "@playwright/test";
import { assertOk } from "@/__tests__/e2e/responses";
import { ToastsComponent } from "./ToastsComponent";

export class NotificationsDialogComponent {
  readonly bellButton: Locator;
  readonly headerLink: Locator;
  readonly badgeCount: Locator;
  readonly root: Locator;
  private readonly toasts: ToastsComponent;

  constructor(private readonly page: Page) {
    this.headerLink = page.getByRole("link", { name: "Notifications", exact: true });
    this.bellButton = page.getByRole("button", { name: "Notifications", exact: true });
    this.badgeCount = page.locator(".MuiBadge-root").filter({ has: this.headerLink }).locator(".MuiBadge-badge");
    this.root = page.getByRole("dialog", { name: "Notifications" });
    this.toasts = new ToastsComponent(page);
  }

  async getBadgeCount(): Promise<number> {
    const res = await this.page.request.get("/dashboard/ajax/poll");
    const body = (await res.json()) as { data: { notificationCount: number } };
    return body.data.notificationCount;
  }

  async waitForBadgeCountInUI(minExpected: number, timeoutMs = 30_000): Promise<number> {
    await expect.poll(() => this.readBadgeFromDom(), { timeout: timeoutMs }).toBeGreaterThanOrEqual(minExpected);
    return this.readBadgeFromDom();
  }

  private async readBadgeFromDom(): Promise<number> {
    if ((await this.badgeCount.count()) === 0) {
      return 0;
    }
    const text = (await this.badgeCount.textContent())?.trim();
    return text ? Number.parseInt(text, 10) : 0;
  }

  async open(): Promise<void> {
    await this.toasts.dismissAll();
    await this.bellButton.click();
    await this.root.waitFor({ state: "visible" });
    // AJAX-rendered rows have no accessible name; this class distinguishes notifications.
    await this.root.locator("tr.notificationRow").first().or(this.emptyState).waitFor({ state: "visible" });
  }

  /** Notification rows containing every given text fragment. */
  row(...texts: string[]): Locator {
    return texts.reduce((rows, text) => rows.filter({ hasText: text }), this.root.locator("tr.notificationRow"));
  }

  async getNotificationTexts(): Promise<Array<string>> {
    return this.root.locator("tr.notificationRow").allInnerTexts();
  }

  async downloadExportArchive(rowText: string): Promise<Buffer> {
    // The download link's text is its URL, so it has no stable accessible name to query by.
    const link = this.row(rowText).locator("a[href*='/export/ajax/downloadArchive/']");
    const response = await this.page.request.get(await this.hrefOf(link, rowText));
    await assertOk(response, "GET");
    return response.body();
  }

  async getExportReportHref(rowText: string): Promise<string> {
    return this.hrefOf(this.row(rowText).getByRole("link", { name: "export report page" }), rowText);
  }

  /** Polls the notification UI for this export, then follows its real download link. */
  async downloadExport(fileName: string): Promise<Download> {
    const notification = this.row(fileName);
    await expect(async () => {
      await this.bellButton.click();
      await this.root.waitFor({ state: "visible" });
      await this.root.getByRole("heading", { name: "My Notifications" }).waitFor({ state: "visible" });
      // Rows render by AJAX after the heading; give them time before reopening to re-fetch.
      const arrived = await notification
        .waitFor({ state: "visible", timeout: 5_000 })
        .then(() => true)
        .catch(() => false);
      if (!arrived) {
        await this.close();
        throw new Error(`The export notification for ${fileName} has not arrived.`);
      }
    }).toPass({ timeout: 60_000 });
    const downloadPrefix = new URL("/export/ajax/downloadArchive/", this.page.url()).href;
    const [download] = await Promise.all([
      this.page.waitForEvent("download"),
      notification.locator(`a[href^="${downloadPrefix}"], a[href^="/export/ajax/downloadArchive/"]`).click(),
    ]);
    return download;
  }

  private async hrefOf(link: Locator, rowText: string): Promise<string> {
    const href = await link.getAttribute("href");
    if (!href) {
      throw new Error(`No link found in the notification matching "${rowText}"`);
    }
    return href;
  }

  // Some pages (e.g. Gallery) render the bell as a plain link to /dashboard rather than a
  // button that opens this dialog in place.
  async fetchNotificationTexts(): Promise<Array<string>> {
    const originalUrl = this.page.url();
    await this.headerLink.click();
    await this.page.waitForURL((url) => url.pathname === "/dashboard");
    const texts = await this.page.getByRole("row").allInnerTexts();
    await this.page.goto(originalUrl);
    return texts;
  }

  async close(): Promise<void> {
    // Two controls are named Close; the legacy pane class selects the text action.
    await this.root.locator(".ui-dialog-buttonpane").getByRole("button", { name: "Close" }).click();
    await this.root.waitFor({ state: "hidden" });
  }

  get emptyState(): Locator {
    return this.root.getByText("There are no new notifications.");
  }

  async deleteAll(): Promise<void> {
    await this.root.getByRole("link", { name: "Delete all", exact: true }).click();
    await this.emptyState.waitFor({ state: "visible" });
  }
}
