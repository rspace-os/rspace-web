import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { institutionBookingPreferences } from "@/modules/booking/pages/preferences/bookingPreferencesFixtures";
import { BookingDashboardPageStory } from "./BookingDashboardPage.story";
import { bookingDashboardHandlers, dashboardBookings } from "./mocks/bookingDashboardMocks";
import { BookingDashboardPageObject } from "./pageObjects/BookingDashboardPage";

const dashboard = new BookingDashboardPageObject();
let history: RouterHistory;
let browserUrl: string;
let requests: URL[];

function registerHandlers(options: Parameters<typeof bookingDashboardHandlers>[0] = {}): void {
  worker.use(
    ...bookingDashboardHandlers({
      ...options,
      onRequest: (url) => requests.push(url),
    }),
  );
}

async function closePopup(): Promise<void> {
  await userEvent.keyboard("{Escape}");
  await expect.element(dashboard.popup).not.toBeInTheDocument();
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-08-18T00:30:00Z"));
  history = createMemoryHistory({ initialEntries: ["/booking"] });
  browserUrl = window.location.href;
  requests = [];
  registerHandlers();
});

afterEach(() => {
  cleanup();
  expect(window.location.href).toBe(browserUrl);
  vi.useRealTimers();
});

describe("Booking dashboard", () => {
  test("shows quick actions and the first five upcoming bookings", async () => {
    render(<BookingDashboardPageStory history={history} />);

    await expect.element(dashboard.heading).toBeVisible();
    await expect
      .element(dashboard.quickAction("Calendar"))
      .toHaveAttribute("href", "/booking/calendar?date=2026-08-18");
    await expect
      .element(dashboard.quickAction("Find an instrument"))
      .toHaveAttribute("href", "/booking/all-items?date=2026-08-18");
    await expect
      .element(dashboard.quickAction("My Bookings"))
      .toHaveAttribute("href", "/booking/my-bookings?period=upcoming");
    await expect.element(dashboard.upcomingInstrument("Instrument 100")).toBeVisible();
    await expect.element(dashboard.upcomingInstrument("Instrument 104")).toBeVisible();
    await expect.element(dashboard.upcomingInstrument("Instrument 200")).not.toBeInTheDocument();
  });

  test("updates today's links and calendar highlight after midnight", async () => {
    vi.setSystemTime(new Date("2026-08-18T23:59:59.900Z"));
    render(<BookingDashboardPageStory history={history} preferences={institutionBookingPreferences} />);

    await expect
      .element(dashboard.quickAction("Calendar"))
      .toHaveAttribute("href", "/booking/calendar?date=2026-08-18");
    await expect.element(dashboard.calendarDay("Tuesday, August 18, 2026")).toBeVisible();
    expect(dashboard.calendarDay("Tuesday, August 18, 2026").element().closest("[data-today=true]")).not.toBeNull();

    vi.setSystemTime(new Date("2026-08-19T00:00:00.100Z"));
    await expect
      .element(dashboard.quickAction("Calendar"))
      .toHaveAttribute("href", "/booking/calendar?date=2026-08-19");
    await expect
      .poll(() => dashboard.calendarDay("Wednesday, August 19, 2026").element().closest("[data-today=true]"))
      .not.toBeNull();
  });

  test("provides localized calendar navigation and unbooked-day labels", async () => {
    render(<BookingDashboardPageStory history={history} />);

    await expect.element(page.getByRole("button", { name: "Go to previous month", exact: true })).toBeVisible();
    await expect.element(page.getByRole("button", { name: "Go to next month", exact: true })).toBeVisible();
    await expect.element(page.getByRole("button", { name: "Monday, August 17, 2026", exact: true })).toBeVisible();
  });

  test("caps the 99 and 100 booking badges without changing accessible counts", async () => {
    render(<BookingDashboardPageStory history={history} />);

    const day99 = dashboard.calendarDay("Friday, August 21, 2026");
    const day100 = dashboard.calendarDay("Saturday, August 22, 2026");
    await expect.element(day99).toHaveAccessibleName("Friday, August 21, 2026: 99 bookings");
    await expect.element(day100).toHaveAccessibleName("Saturday, August 22, 2026: 100 bookings");
    await expect.element(day99.getByText("99", { exact: true })).toBeVisible();
    await expect.element(day100.getByText("99+", { exact: true })).toBeVisible();
  });

  test("pages days with five, six, and twelve bookings without another request", async () => {
    render(<BookingDashboardPageStory history={history} />);

    await dashboard.calendarDay("Tuesday, August 18, 2026").click();
    await expect.element(dashboard.popup).toBeVisible();
    await expect.element(dashboard.previous).not.toBeInTheDocument();
    await expect.element(dashboard.next).not.toBeInTheDocument();
    await closePopup();

    await dashboard.calendarDay("Wednesday, August 19, 2026").click();
    await expect.element(dashboard.range).toHaveTextContent("1–5 of 6");
    await expect.element(dashboard.previous).toBeDisabled();
    await expect.element(dashboard.next).toBeEnabled();
    const requestCount = requests.length;
    await dashboard.next.click();
    await expect.element(dashboard.range).toHaveTextContent("6–6 of 6");
    await expect.element(dashboard.previous).toBeEnabled();
    await expect.element(dashboard.next).toBeDisabled();
    await expect.poll(() => requests.length).toBe(requestCount);
    await closePopup();

    await dashboard.calendarDay("Thursday, August 20, 2026").click();
    await expect.element(dashboard.range).toHaveTextContent("1–5 of 12");
    await dashboard.next.click();
    await expect.element(dashboard.range).toHaveTextContent("6–10 of 12");
    await dashboard.next.click();
    await expect.element(dashboard.range).toHaveTextContent("11–12 of 12");
    await expect.element(dashboard.next).toBeDisabled();
    await expect.element(dashboard.previous).toBeEnabled();
    expect(dashboard.popup.getByRole("button").all()).toHaveLength(2);
  });

  test("keeps busy bookings private while full bookings retain purpose and details", async () => {
    render(<BookingDashboardPageStory history={history} />);

    await dashboard.calendarDay("Sunday, August 23, 2026").click();
    // WebKit reports descendants of a closed details element as hidden, even
    // inside its visible summary. Assert on the disclosure itself.
    await expect.element(dashboard.summaryDisclosure("Private full booking")).toBeVisible();
    await expect.element(dashboard.summaryDisclosure("Private full booking")).toHaveTextContent("Private full booking");
    await expect.element(dashboard.summary("Private busy booking")).toBeVisible();
    await dashboard.summaryDisclosure("Private full booking").click();
    await expect.element(dashboard.popup.getByText("Private purpose", { exact: true })).toBeVisible();
    await expect
      .element(dashboard.popup.getByRole("link", { name: "Details", exact: true }))
      .toHaveAttribute("href", "/booking/calendar/bookings/700");
    await expect.element(dashboard.popup.getByText("Purpose 701", { exact: true })).not.toBeInTheDocument();
    await expect.poll(() => dashboard.popup.getByRole("link", { name: "Details", exact: true }).all().length).toBe(1);
  });

  test("keeps the calendar available when upcoming bookings fail", async () => {
    registerHandlers({ error: "upcoming" });
    render(<BookingDashboardPageStory history={history} />);

    await expect.element(dashboard.upcomingError).toBeVisible();
    await expect.element(dashboard.calendarDay("Friday, August 21, 2026")).toBeVisible();
  });

  test("keeps upcoming bookings available when the calendar fails", async () => {
    registerHandlers({ error: "monthly" });
    render(<BookingDashboardPageStory history={history} />);

    await expect.element(dashboard.upcomingInstrument("Instrument 100")).toBeVisible();
    await expect.element(dashboard.calendarError).toBeVisible();
  });

  test("leaves one popup open while hovering between booked days", async () => {
    render(<BookingDashboardPageStory history={history} />);

    await dashboard.calendarDay("Friday, August 21, 2026").hover();
    await expect.element(dashboard.popup).toBeVisible();
    await dashboard.calendarDay("Saturday, August 22, 2026").hover();
    await expect.element(dashboard.popup).toBeVisible();
    expect(page.getByRole("dialog").all()).toHaveLength(1);
  });

  test.each(["{Enter}", "{Space}"])(
    "opens a booked day with %s after arrow navigation and restores focus",
    async (key) => {
      render(<BookingDashboardPageStory history={history} />);

      const day18 = dashboard.calendarDay("Tuesday, August 18, 2026");
      const day19 = dashboard.calendarDay("Wednesday, August 19, 2026");
      await expect.element(day18).toBeVisible();
      await expect.element(day19).toBeVisible();
      // The preceding hover test leaves the physical pointer over a booked day.
      await dashboard.heading.hover();
      await expect.element(dashboard.popup).not.toBeInTheDocument();
      day18.element().focus();
      await expect.element(day18).toHaveFocus();
      await userEvent.keyboard("{ArrowRight}");
      await expect.element(day19).toHaveFocus();
      await userEvent.keyboard(key);
      await expect.element(dashboard.popup).toBeVisible();
      await userEvent.keyboard("{Escape}");
      await expect.element(dashboard.popup).not.toBeInTheDocument();
      expect(document.activeElement).toBe(day19.element());
    },
  );

  test("keeps the mobile popup in the viewport and the pager in a stable position", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(320, 800);
    registerHandlers({
      docs: dashboardBookings.map((booking) =>
        booking.id >= 200 && booking.id < 205
          ? {
              ...booking,
              target: {
                ...booking.target,
                value: {
                  ...booking.target.value,
                  name: `Instrument ${booking.id} with a deliberately long name that must not move the pager`,
                },
              },
            }
          : booking,
      ),
    });
    render(<BookingDashboardPageStory history={history} />);
    try {
      await dashboard.calendarDay("Wednesday, August 19, 2026").click();
      await expect.element(dashboard.popup).toBeVisible();
      const firstPagerTop = dashboard.next.element().getBoundingClientRect().top;
      await dashboard.next.click();
      await expect.element(dashboard.range).toHaveTextContent("6–6 of 6");
      const secondPagerTop = dashboard.next.element().getBoundingClientRect().top;
      expect(Math.abs(secondPagerTop - firstPagerTop)).toBeLessThanOrEqual(1);

      await closePopup();
      await dashboard.calendarDay("Saturday, August 22, 2026").click();
      await expect.element(dashboard.popup).toBeVisible();
      const popupBox = dashboard.popup.element().getBoundingClientRect();
      expect(popupBox.left).toBeGreaterThanOrEqual(0);
      expect(popupBox.right).toBeLessThanOrEqual(320);
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });
});
