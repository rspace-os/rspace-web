import type { Locator, Page } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class BookingPermissionsPage extends BasePage {
  readonly path = "/booking/bookable-items";

  readonly accessTab: Locator;
  readonly assignments: Locator;
  readonly addUserOrGroup: Locator;
  readonly search: Locator;
  readonly saveChanges: Locator;
  readonly savedStatus: Locator;

  constructor(page: Page) {
    super(page);
    this.accessTab = page.getByRole("tab", { name: "Access" });
    this.assignments = page.getByRole("list", { name: "Access assignments" });
    this.addUserOrGroup = page.getByRole("textbox", { name: "Add user or group" });
    this.search = page.getByRole("button", { name: "Search" });
    this.saveChanges = page.getByRole("button", { name: "Save changes" });
    this.savedStatus = page.getByText("Access changes saved.", { exact: true });
  }

  heading(name: string): Locator {
    return this.page.getByRole("heading", { level: 1, name });
  }

  assignment(detail: string): Locator {
    return this.assignments.getByRole("listitem").filter({ hasText: detail });
  }

  async openRecord(globalId: string): Promise<void> {
    await this.page.goto(`${this.path}/${globalId}`);
    await this.page.getByText(globalId, { exact: true }).waitFor();
  }

  async openAccess(): Promise<void> {
    await this.accessTab.click();
    await this.addUserOrGroup.waitFor();
  }

  async addUser(username: string, displayName: string, role: string): Promise<void> {
    await this.addUserOrGroup.fill(username);
    await this.search.click();
    const add = this.page.getByRole("button", { name: `Add ${displayName}` });
    await add.waitFor({ timeout: 15_000 });
    await add.click();
    const assignment = this.assignment(username);
    await assignment.waitFor({ timeout: 15_000 });
    await assignment.getByRole("combobox", { name: `Direct role for ${displayName}` }).selectOption(role);
    await this.save();
  }

  async removeAssignment(detail: string, displayName: string): Promise<void> {
    await this.assignment(detail)
      .getByRole("button", { name: `Remove ${displayName}` })
      .click();
    await this.save();
  }

  async createBooking(input: {
    globalId: string;
    date: string;
    startTime: string;
    endTime: string;
    purpose: string;
  }): Promise<{ id: number }> {
    const query = new URLSearchParams({ date: input.date, target: input.globalId });
    await this.page.goto(`/booking/calendar/bookings/add?${query}`);
    await this.page.getByRole("heading", { level: 1, name: "Add Booking" }).waitFor();
    const start = this.page.getByRole("group", { name: "Start" });
    const end = this.page.getByRole("group", { name: "End" });
    await start.getByLabel("Date").fill(input.date);
    await start.getByLabel("Time").fill(input.startTime);
    await end.getByLabel("Date").fill(input.date);
    await end.getByLabel("Time").fill(input.endTime);
    await this.page.getByLabel("Purpose").fill(input.purpose);
    const responsePromise = this.page.waitForResponse(
      (response) => response.request().method() === "POST" && response.url().endsWith("/api/v2/bookings?depth=1"),
    );
    await this.page.getByRole("button", { name: "Book", exact: true }).click();
    const response = await responsePromise;
    if (!response.ok()) {
      throw new Error(`Creating a booking failed with status ${response.status()}: ${await response.text()}`);
    }
    await this.page.waitForURL((url) => url.pathname === "/booking/calendar");
    return response.json() as Promise<{ id: number }>;
  }

  async openMyBookings(): Promise<void> {
    await this.page.goto("/booking/my-bookings");
    await this.page.getByRole("heading", { level: 1, name: "My Bookings" }).waitFor();
  }

  private async save(): Promise<void> {
    const responsePromise = this.page.waitForResponse(
      (response) =>
        response.request().method() === "PUT" && /\/api\/v2\/booking-configurations\/\d+\/access$/.test(response.url()),
      { timeout: 15_000 },
    );
    await this.saveChanges.click({ timeout: 15_000 });
    const response = await responsePromise;
    if (!response.ok()) {
      throw new Error(`Saving booking access failed with status ${response.status()}: ${await response.text()}`);
    }
    await this.savedStatus.waitFor({ timeout: 15_000 });
  }
}
