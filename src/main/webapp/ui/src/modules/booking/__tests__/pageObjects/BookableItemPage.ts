import type { Locator, Page } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class BookableItemPage extends BasePage {
  readonly path = "/booking/bookable-items";
  readonly accessTab: Locator;
  readonly accessPanel: Locator;
  readonly detailsPanel: Locator;
  readonly edit: Locator;
  readonly hours: Locator;

  constructor(page: Page) {
    super(page);
    this.accessTab = page.getByRole("tab", { name: "Access" });
    this.accessPanel = page.getByRole("tabpanel", { name: "Access" });
    this.detailsPanel = page.getByRole("tabpanel", { name: "Details", exact: true });
    this.edit = this.detailsPanel.getByRole("button", { name: "Edit configuration", exact: true });
    this.hours = this.detailsPanel.getByRole("list", { name: "Opening hours", exact: true });
  }

  async openRecord(globalId: string): Promise<void> {
    await this.page.goto(`${this.path}/${globalId}`);
    await this.page.getByText(globalId, { exact: true }).waitFor();
  }

  async openDetails(globalId: string): Promise<void> {
    await this.page.goto(`${this.path}/${globalId}/details`);
    await this.edit.waitFor();
  }

  async openAccess(): Promise<void> {
    await this.accessTab.click();
    await this.accessPanel.waitFor();
  }

  async openEditor(): Promise<void> {
    await this.edit.click();
    await this.hours.waitFor();
  }

  openDay(day: string): Locator {
    return this.hours.getByRole("checkbox", { name: day, exact: true });
  }

  private dayGroup(day: string): Locator {
    return this.detailsPanel.getByRole("group", { name: day, exact: true });
  }

  startTime(day: string): Locator {
    return this.dayGroup(day).getByLabel("Opening start", { exact: true });
  }

  endTime(day: string): Locator {
    return this.dayGroup(day).getByLabel("Opening end", { exact: true });
  }

  async setHours(day: string, hours: { start: string; end: string }): Promise<void> {
    await this.startTime(day).fill(hours.start);
    await this.endTime(day).fill(hours.end === "24:00" ? "00:00" : hours.end);
  }

  async save(configurationId: number): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (response) =>
        response.request().method() === "PATCH" &&
        new URL(response.url()).pathname === `/api/v2/booking-configurations/${configurationId}`,
    );
    await this.detailsPanel.getByRole("button", { name: "Save", exact: true }).click();
    const response = await responsePromise;
    if (!response.ok()) {
      throw new Error(`Saving the schedule failed with status ${response.status()}: ${await response.text()}`);
    }
    await this.edit.waitFor();
  }

  async cancel(): Promise<void> {
    await this.detailsPanel.getByRole("button", { name: "Cancel", exact: true }).click();
    await this.edit.waitFor();
  }
}
