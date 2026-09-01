import type { Locator, Page } from "@playwright/test";
import { BasePage } from "@/__tests__/e2e/pageObjects/BasePage";

export class CreateBookingPage extends BasePage {
  readonly path = "/booking/calendar/bookings/add";

  readonly heading: Locator;
  readonly purpose: Locator;
  readonly submit: Locator;

  constructor(page: Page) {
    super(page);
    this.heading = page.getByRole("heading", { level: 1, name: "Add Booking" });
    this.purpose = page.getByLabel("Purpose");
    this.submit = page.getByRole("button", { name: "Book", exact: true });
  }

  async create(input: {
    globalId: string;
    date: string;
    startTime: string;
    endTime: string;
    purpose: string;
  }): Promise<{ id: number }> {
    const query = new URLSearchParams({ date: input.date, target: input.globalId });
    await this.page.goto(`${this.path}?${query}`);
    await this.heading.waitFor();
    const start = this.page.getByRole("group", { name: "Start" });
    const end = this.page.getByRole("group", { name: "End" });
    await start.getByLabel("Date").fill(input.date);
    await start.getByLabel("Time").fill(input.startTime);
    await end.getByLabel("Date").fill(input.date);
    await end.getByLabel("Time").fill(input.endTime);
    await this.purpose.fill(input.purpose);
    const responsePromise = this.page.waitForResponse(
      (response) => response.request().method() === "POST" && response.url().endsWith("/api/v2/bookings?depth=1"),
    );
    await this.submit.click();
    const response = await responsePromise;
    if (!response.ok()) {
      throw new Error(`Creating a booking failed with status ${response.status()}: ${await response.text()}`);
    }
    await this.page.waitForURL((url) => url.pathname === "/booking/calendar");
    return response.json();
  }
}
