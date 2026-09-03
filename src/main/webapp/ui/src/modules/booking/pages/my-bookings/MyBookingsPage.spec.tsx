import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import type { Locator } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { bookableItemDetailsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { currentUser } from "../calendar/__tests__/calendarTestHarness";
import { MyBookingsPageStory } from "./MyBookingsPage.story";
import { bookingHandlers } from "./mocks/bookingMocks";
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
  );
}

beforeEach(() => {
  window.history.replaceState({}, "", "/");
  registerHandlers();
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("the My Bookings page", () => {
  test("shows a tooltip for every icon-only page control", async () => {
    render(<MyBookingsPageStory />);

    const controls = [
      [pageObj.upcoming, "Upcoming"],
      [pageObj.past, "Past"],
      [pageObj.confocalDetails, "View details"],
      [pageObj.confocalEdit, "Edit"],
      [pageObj.confocalCalendarFile, ".ics file"],
      [pageObj.confocalCancel, "Cancel booking"],
    ] as const;

    for (const [control, label] of controls) {
      await control.hover();
      await expect.element(pageObj.tooltip(label)).toBeVisible();
    }
  });

  test("navigates from a booking row to the bookable item details page", async () => {
    render(<MyBookingsPageStory />);

    await expect.element(pageObj.confocalDetails).toBeVisible();
    clickWithoutDriverWait(pageObj.confocalDetails);

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123");
  });
});
