import { expect, type Locator, type Page } from "@playwright/test";
import { ToastsComponent } from "./ToastsComponent";

export class NotificationsDialogComponent {
  readonly bellButton: Locator;
  readonly badgeCount: Locator;
  readonly root: Locator;
  private readonly toasts: ToastsComponent;

  constructor(private readonly page: Page) {
    const headerLink = page.getByRole("link", { name: "Notifications", exact: true });
    this.bellButton = page.getByRole("button", { name: "Notifications", exact: true });
    this.badgeCount = page.locator(".MuiBadge-root").filter({ has: headerLink }).locator(".MuiBadge-badge");
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
    await this.root.locator("tr.notificationRow").first().waitFor({ state: "visible" });
  }

  async getNotificationTexts(): Promise<Array<string>> {
    return this.root.locator("tr.notificationRow").allInnerTexts();
  }

  async close(): Promise<void> {
    // Two controls are named Close; the legacy pane class selects the text action.
    await this.root.locator(".ui-dialog-buttonpane").getByRole("button", { name: "Close" }).click();
    await this.root.waitFor({ state: "hidden" });
  }
}
