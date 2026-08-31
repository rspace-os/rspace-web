import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  bookableItemDetailsHandlers,
  bookableItemsHandlers,
  sampleBookingEvents,
} from "../bookable-items/mocks/bookableItemsMocks";
import {
  busyBooking,
  collectionResponse,
  currentUser,
  otherBooking,
  ownBooking,
} from "../calendar/__tests__/calendarTestHarness";
import { CalendarPageStory } from "../calendar/CalendarPage.story";
import { CalendarPage as CalendarPageObject } from "../calendar/pageObjects/CalendarPage";
import { AllBookableItemsStory } from "./AllBookableItemsPage.story";
import { AllBookableItemsPage } from "./pageObjects/AllBookableItemsPage";

const pageObj = new AllBookableItemsPage();
const calendar = new CalendarPageObject();
const availabilityBookingFields = "id,target,timezone,start,end,state";
const calendarBookingFields =
  "id,target,requesterId,timezone,start,end,state,purpose,bookedBy,privacy,canEdit,createdAt,updatedAt";
let collectionQueries: string[] = [];
let bookingRequests = 0;
let bookingQuery: URL | undefined;
let calendarBookingRequests: URL[] = [];

function registerHandlers() {
  worker.use(
    oauthTokenHandler(),
    http.get("/api/v2/users/me", () => HttpResponse.json(currentUser)),
    ...bookableItemDetailsHandlers(),
    ...bookableItemsHandlers((request) => {
      collectionQueries.push(decodeURIComponent(new URL(request.url).search));
    }),
    http.get("/api/v2/bookings", ({ request }) => {
      const url = new URL(request.url);
      const fields = url.searchParams.get("fields[bookings]");
      if (fields === calendarBookingFields) {
        calendarBookingRequests.push(url);
        return HttpResponse.json(collectionResponse([ownBooking, otherBooking, busyBooking]));
      }
      if (fields === availabilityBookingFields) {
        bookingRequests += 1;
        bookingQuery = url;
        return HttpResponse.json({
          docs: sampleBookingEvents,
          totalDocs: sampleBookingEvents.length,
          totalPages: 1,
          page: 1,
          hasNextPage: false,
        });
      }
      return undefined;
    }),
  );
}

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

beforeEach(() => {
  collectionQueries = [];
  bookingRequests = 0;
  bookingQuery = undefined;
  calendarBookingRequests = [];
  window.history.replaceState({}, "", "/");
  registerHandlers();
});

describe("Calendar page", () => {
  test("navigates from a busy event to the bookable item details page", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await calendar.showEventDetails("Busy").click();
    await calendar.viewItemDetails.click();

    await expect.element(calendar.bookableItemDetailsHeading).toBeVisible();
    await expect.element(calendar.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN124");
  });

  test("uses live booking events across every prototype layout and period", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await expect.element(calendar.heading).toBeVisible();
    await expect.element(calendar.timeGrid).toBeVisible();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.event("Busy")).toBeVisible();
    await expect.poll(() => calendarBookingRequests.length).toBe(1);
    expect(calendarBookingRequests[0].searchParams.get("fields[bookings]")).toBe(calendarBookingFields);
    expect(calendarBookingRequests[0].searchParams.get("where")).toContain("state==CONFIRMED");

    await calendar.showEventDetails("Busy").click();
    await expect.element(calendar.viewItemDetails).toHaveAttribute("href", "/booking/bookable-items/IN124");
    await expect.element(calendar.editBooking).not.toBeInTheDocument();

    await calendar.searchFor("Grace");
    await expect.element(calendar.event("Confocal microscope")).not.toBeInTheDocument();
    await expect.element(calendar.event("Electron microscope")).toBeVisible();
    await page.getByRole("button", { name: "Clear search" }).click();

    await calendar.resources.click();
    await expect.element(calendar.resourceSchedule).toBeVisible();
    await calendar.day.click();
    await expect.poll(() => calendarBookingRequests.length).toBe(2);
    await expect.poll(() => page.getByTestId("day-timeline-scroller").all().length).toBe(2);

    await calendar.mine.click();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.event("Electron microscope")).not.toBeInTheDocument();

    await calendar.agenda.click();
    await expect.element(calendar.bookingAgenda).toBeVisible();
    await calendar.month.click();
    await expect.poll(() => calendarBookingRequests.length).toBe(3);
    await expectNoAxeViolations();
  });
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("the All Bookable Items page", () => {
  test("navigates to the bookable item details page", async () => {
    render(<AllBookableItemsStory />);

    await pageObj.detailsButton.click();

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  test("uses standardized cards in a narrow container with full-width availability and all controls", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1200, 900);

    try {
      render(<AllBookableItemsStory containerWidth={600} />);

      await expect.element(pageObj.cards).toBeVisible();
      await expect.element(pageObj.tableElement).not.toBeVisible();
      await expect.element(pageObj.card("Confocal microscope")).toBeVisible();
      await expect.element(pageObj.cardParentContainer("Confocal microscope", "Imaging lab")).toBeVisible();
      await expect.element(pageObj.effectiveTimeZone).toBeVisible();
      await expect.element(pageObj.confocalAvailability).toBeVisible();
      await expect.element(pageObj.search).toBeVisible();
      await expect.element(pageObj.filtersButton).toBeVisible();
      await expect.element(pageObj.availableNow).toBeVisible();
      await expect.element(pageObj.freeLaterToday).toBeVisible();
      await expect.element(pageObj.date).toBeVisible();
      await expect
        .poll(() => pageObj.availabilityWidth("Confocal microscope"))
        .toBeGreaterThan(pageObj.cardWidth("Confocal microscope") - 50);

      const bookUrl = new URL(
        pageObj.cardBookButton("Confocal microscope").element().getAttribute("href") ?? "",
        window.location.origin,
      );
      expect(bookUrl.pathname).toBe("/booking/calendar/bookings/add");
      expect(bookUrl.searchParams.get("target")).toBe("IN123");
      await expect
        .element(pageObj.cardDetailsButton("Confocal microscope"))
        .toHaveAttribute("href", "/booking/bookable-items/IN123");
      await expectNoAxeViolations();
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("wraps quick-filter buttons in a very narrow container", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1200, 900);

    try {
      render(<AllBookableItemsStory containerWidth={360} />);

      await expect.element(pageObj.availableNow).toBeVisible();
      await expect.element(pageObj.freeLaterToday).toBeVisible();
      await expect
        .poll(
          () =>
            pageObj.freeLaterToday.element().getBoundingClientRect().top >
            pageObj.availableNow.element().getBoundingClientRect().top,
        )
        .toBe(true);
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("shows availability for fixture items and routes new bookings", async () => {
    render(<AllBookableItemsStory />);

    await expect.element(pageObj.heading).toBeVisible();
    await expect.element(pageObj.item).toBeVisible();
    await expect.element(pageObj.electronMicroscope).toBeVisible();
    await expect.element(pageObj.massSpectrometer).toBeVisible();
    await expect.element(pageObj.flowCytometer).toBeVisible();
    await expect.element(pageObj.confocalAvailability).toBeVisible();
    await expect.element(pageObj.electronAvailability).toBeVisible();
    await expect.element(pageObj.massSpectrometerAvailability).toBeVisible();
    await expect.element(pageObj.flowCytometerAvailability).toBeVisible();
    await expect.element(pageObj.parentContainer("Imaging lab")).toHaveAttribute("href", "/globalId/IC456");
    await expect.element(pageObj.parentContainer("Workbench")).toHaveAttribute("href", "/globalId/BE457");
    await expect.element(pageObj.parentContainer("Mass spectrometry lab")).toHaveAttribute("href", "/globalId/IC458");
    await expect.element(pageObj.parentContainer("Screening lab")).toHaveAttribute("href", "/globalId/IC459");
    await expect.element(pageObj.nowMarker("Confocal microscope")).toBeVisible();
    await expect.element(pageObj.effectiveTimeZone).toBeVisible();
    await expect
      .poll(
        () =>
          new Set(
            ["Confocal microscope", "Electron microscope", "Mass spectrometer", "Flow cytometer"].map(
              (itemName) => pageObj.nowMarker(itemName).element().style.left,
            ),
          ).size,
      )
      .toBe(1);
    await expect.poll(() => collectionQueries.at(-1)).toContain("enabled==true");
    await expect.poll(() => collectionQueries.at(-1)).toContain("target.deleted==false");
    await expect.poll(() => bookingRequests).toBe(1);
    expect(bookingQuery?.searchParams.get("fields[bookings]")).toBe("id,target,timezone,start,end,state");

    const bookUrl = new URL(pageObj.bookButton.element().getAttribute("href") ?? "", window.location.origin);
    expect(bookUrl.pathname).toBe("/booking/calendar/bookings/add");
    expect(bookUrl.searchParams.get("date")).toBe("2026-08-17");
    expect(bookUrl.searchParams.get("target")).toBe("IN123");
    await expect.element(pageObj.detailsButton).toHaveAttribute("href", "/booking/bookable-items/IN123");

    await pageObj.detailsButton.hover();
    await expect.element(page.getByRole("tooltip")).toHaveTextContent("View details");
    await pageObj.detailsButton.unhover();
    await pageObj.bookButton.hover();
    await expect.element(page.getByRole("tooltip")).toHaveTextContent("Book");
  });

  test("does not render an unnecessary horizontal scrollbar", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1600, 900);

    try {
      render(<AllBookableItemsStory />);
      await expect.element(pageObj.table).toBeVisible();
      await expect
        .poll(() => {
          const container = pageObj.table.element().parentElement;
          return container ? container.scrollWidth - container.clientWidth : null;
        })
        .toBe(0);
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("switches cached availability filters using the shared display interval", async () => {
    render(<AllBookableItemsStory />);

    await expect.element(pageObj.date).toBeVisible();
    await pageObj.freeLaterToday.click();

    await expect.poll(() => window.location.search).toContain("availability=free-later-today");
    await expect.element(pageObj.freeLaterToday).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.quickFilterScope).toBeVisible();
    await expect.element(pageObj.date).not.toBeInTheDocument();
    await expect.element(pageObj.electronMicroscope).toBeVisible();
    await expect.element(pageObj.item).not.toBeInTheDocument();
    await expect.element(pageObj.massSpectrometer).not.toBeInTheDocument();
    await expect.element(pageObj.flowCytometer).not.toBeInTheDocument();
    await expect.poll(() => collectionQueries.some((query) => query.includes("target=in=(IN124)"))).toBe(true);

    const electronBookUrl = new URL(
      pageObj.electronBookButton.element().getAttribute("href") ?? "",
      window.location.origin,
    );
    expect(electronBookUrl.searchParams.get("date")).toBe("2026-08-17");

    await pageObj.availableNow.click();

    await expect.poll(() => window.location.search).toContain("availability=available-now");
    await expect.element(pageObj.availableNow).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.item).toBeVisible();
    await expect.element(pageObj.flowCytometer).toBeVisible();
    await expect.element(pageObj.electronMicroscope).not.toBeInTheDocument();
    await expect.element(pageObj.massSpectrometer).not.toBeInTheDocument();
    await expect.poll(() => bookingRequests).toBe(2);

    const candidateRequests = collectionQueries.filter((query) => query.includes("limit=100"));
    expect(candidateRequests).toHaveLength(1);

    await pageObj.availableNow.click();

    await expect.poll(() => window.location.search).not.toContain("availability=");
    await expect.element(pageObj.date).toBeVisible();
    await expect.element(pageObj.electronMicroscope).toBeVisible();
    await expect.element(pageObj.massSpectrometer).toBeVisible();
    await expectNoAxeViolations();
  });
});
