import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { worker } from "@/__tests__/browserSetup";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { bookableItemDetailsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { currentUser } from "../calendar/__tests__/calendarTestHarness";
import { MyBookingsPageStory } from "./MyBookingsPage.story";
import { bookingHandlers } from "./mocks/bookingMocks";
import { MyBookingsPageObject } from "./pageObjects/MyBookingsPage";

const pageObj = new MyBookingsPageObject();
let listRequests: URL[] = [];

function registerHandlers() {
  worker.use(
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    ...bookableItemDetailsHandlers(),
    ...bookingHandlers((url) => listRequests.push(url)),
  );
}

beforeEach(() => {
  listRequests = [];
  window.history.replaceState({}, "", "/");
  registerHandlers();
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("the My Bookings page", () => {
  test("navigates from a booking row to the bookable item details page", async () => {
    render(<MyBookingsPageStory />);

    await pageObj.confocalDetails.click();

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  test("switches periods while preserving TableList controls and fixed requester scope", async () => {
    render(<MyBookingsPageStory />);

    await expect.element(pageObj.heading).toBeVisible();
    await expect.element(pageObj.upcoming).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.upcomingCount).toHaveTextContent("2");
    await expect.element(pageObj.confocal).toBeVisible();
    await expect.element(pageObj.confocalDetails).toHaveAttribute("href", "/booking/bookable-items/IN123");
    await expect.poll(() => listRequests.at(-1)?.searchParams.get("where")).toContain("requesterId==84");
    await expect.poll(() => listRequests.at(-1)?.searchParams.get("where")).toContain("end=gt=");
    const columnsBeforePeriodChange = new URLSearchParams(window.location.search).get("my-bookings.columns");

    await pageObj.selectPast();

    await expect.element(pageObj.past).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.electron).toBeVisible();
    await expect.poll(() => window.location.search).toContain("period=past");
    const parameters = new URLSearchParams(window.location.search);
    expect(parameters.get("my-bookings.q")).toBe("confocal");
    expect(parameters.get("my-bookings.where")).toBe("target.name=contains=scope");
    expect(parameters.get("my-bookings.columns")).toBe(columnsBeforePeriodChange);
    expect(parameters.get("my-bookings.sort")).toBe("-start");
    await expect.poll(() => listRequests.at(-1)?.searchParams.get("where")).toContain("end=le=");

    await pageObj.resetView();

    await expect.poll(() => new URLSearchParams(window.location.search).has("my-bookings.q")).toBe(false);
    await expect.poll(() => new URLSearchParams(window.location.search).has("my-bookings.where")).toBe(false);
    await expect.poll(() => new URLSearchParams(window.location.search).get("period")).toBe("past");
    await expect.poll(() => listRequests.at(-1)?.searchParams.get("where")).toContain("requesterId==84");
    await expect.poll(() => listRequests.at(-1)?.searchParams.get("where")).toContain("end=le=");
    await expectNoAxeViolations();
  });

  test("restores the default upcoming scope after TableList rewrites the URL", async () => {
    render(<MyBookingsPageStory />);

    await expect.element(pageObj.confocal).toBeVisible();
    await pageObj.resetView();

    await expect.poll(() => new URLSearchParams(window.location.search).get("period")).toBe("upcoming");
  });
});
