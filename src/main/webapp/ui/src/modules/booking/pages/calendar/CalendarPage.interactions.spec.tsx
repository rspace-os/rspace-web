import "@/modules/common/styles/index.css";
import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { CalendarPageStory } from "./CalendarPage.story";
import { calendar, registerHandlers } from "./calendarPageBrowserTestUtils";

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

beforeEach(() => {
  resetBookingPageRequests();
  registerHandlers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe("Calendar page", () => {
  test("keeps the event expand control fixed when details open", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    const event = calendar.event("Confocal microscope");
    await expect.element(event).toBeVisible();
    const indicator = event.element().querySelector<HTMLElement>("[data-event-expand-indicator]");
    expect(indicator).not.toBeNull();
    const collapsedPosition = indicator?.getBoundingClientRect();

    await calendar.showEventDetails("Confocal microscope").click();
    await expect
      .element(event.getByRole("button", { name: "Hide details", exact: false }))
      .toHaveAttribute("aria-expanded", "true");
    const expandedPosition = indicator?.getBoundingClientRect();

    expect(expandedPosition?.top).toBe(collapsedPosition?.top);
    expect(expandedPosition?.right).toBe(collapsedPosition?.right);
  });

  test("does not expose details for a busy event", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    await calendar.showEventDetails("Busy").click();
    await expect.element(calendar.viewItemDetails).not.toBeInTheDocument();
  });

  test("closes booking and busy cards when the page is clicked", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.timeGridLayout.click();
    await calendar.week.click();

    await calendar.event("Confocal microscope").click();
    await expect
      .element(calendar.event("Confocal microscope").getByRole("button", { name: "Hide details", exact: false }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Confocal microscope")).toHaveAttribute("aria-expanded", "false");

    await calendar.event("Busy").click();
    await expect
      .element(calendar.event("Busy").getByRole("button", { name: "Hide details", exact: false }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Busy")).toHaveAttribute("aria-expanded", "false");
  });
});
