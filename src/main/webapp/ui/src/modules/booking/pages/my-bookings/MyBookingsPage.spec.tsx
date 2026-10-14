import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import type { Locator } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { bookableItemDetailsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { currentUser } from "../calendar/__tests__/calendarTestHarness";
import { MyBookingsPageStory, myBookingsStoryUrl } from "./MyBookingsPage.story";
import { bookingHandlers, upcomingBooking } from "./mocks/bookingMocks";
import { MyBookingsPageObject } from "./pageObjects/MyBookingsPage";

const pageObj = new MyBookingsPageObject();

function clickWithoutDriverWait(locator: Locator) {
  const element = locator.element();
  if (!(element instanceof HTMLElement)) throw new TypeError("Expected an HTML element");
  element.click();
}

function registerHandlers() {
  worker.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    ...bookableItemDetailsHandlers(),
    ...bookingHandlers(),
    http.get("/api/v2/bookings/41", () => HttpResponse.json(upcomingBooking)),
  );
}

let history: RouterHistory;
let browserUrl: string;

beforeEach(() => {
  history = createMemoryHistory({ initialEntries: [myBookingsStoryUrl] });
  browserUrl = window.location.href;
  registerHandlers();
});

afterEach(() => {
  cleanup();
  expect(window.location.href).toBe(browserUrl);
});

describe("the My Bookings page", () => {
  test("shows a tooltip for every icon-only page control", async () => {
    render(<MyBookingsPageStory history={history} />);

    const controls = [
      [pageObj.confocalDetails, "View details"],
      [pageObj.confocalItemCalendar, "View item calendar"],
      [pageObj.confocalEdit, "Edit"],
      [pageObj.confocalCalendarFile, ".ics file"],
      [pageObj.confocalCancel, "Cancel booking"],
    ] as const;

    for (const [control, label] of controls) {
      await control.hover();
      await expect.element(pageObj.tooltip(label)).toBeVisible();
    }
  });

  test("shows the upcoming count inside its period button", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.upcomingCount).toBeVisible();
    const buttonRect = pageObj.upcoming.element().getBoundingClientRect();
    const badgeRect = pageObj.upcomingCount.element().getBoundingClientRect();

    expect(badgeRect.left).toBeGreaterThanOrEqual(buttonRect.left);
    expect(badgeRect.top).toBeGreaterThanOrEqual(buttonRect.top);
    expect(badgeRect.right).toBeLessThanOrEqual(buttonRect.right);
    expect(badgeRect.bottom).toBeLessThanOrEqual(buttonRect.bottom);
  });

  test("navigates from a booking row to the bookable item details page", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.confocalItemCalendar).toBeVisible();
    clickWithoutDriverWait(pageObj.confocalItemCalendar);

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => history.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  test("navigates from View details to the booking event", async () => {
    render(<MyBookingsPageStory history={history} />);

    await expect.element(pageObj.confocalDetails).toBeVisible();
    clickWithoutDriverWait(pageObj.confocalDetails);

    await expect.poll(() => history.location.pathname).toBe("/booking/calendar/bookings/41");
    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
  });
});
