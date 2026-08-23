import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { bookableItemsHandlers, sampleBookingEvents } from "../bookable-items/mocks/bookableItemsMocks";
import { AllBookableItemsStory } from "./AllBookableItemsPage.story";
import { AllBookableItemsPage } from "./pageObjects/AllBookableItemsPage";

const pageObj = new AllBookableItemsPage();
let collectionQueries: string[] = [];
let bookingRequests = 0;
let bookingQuery: URL | undefined;

function registerHandlers() {
  worker.use(
    ...bookableItemsHandlers((request) => {
      collectionQueries.push(decodeURIComponent(new URL(request.url).search));
    }),
    http.get("/api/v2/bookings", ({ request }) => {
      bookingRequests += 1;
      bookingQuery = new URL(request.url);
      return HttpResponse.json({
        docs: sampleBookingEvents,
        totalDocs: sampleBookingEvents.length,
        totalPages: 1,
        page: 1,
        hasNextPage: false,
      });
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
  window.history.replaceState({}, "", "/");
  registerHandlers();
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("the All Bookable Items page", () => {
  test("uses standardized cards in a narrow container with full-width availability and all controls", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1200, 900);

    try {
      render(<AllBookableItemsStory containerWidth={600} />);

      await expect.element(pageObj.cards).toBeVisible();
      await expect.element(pageObj.tableElement).not.toBeVisible();
      await expect.element(pageObj.card("Confocal microscope")).toBeVisible();
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
    await expect.poll(() => collectionQueries.at(-1)).toContain("enabled==true");
    await expect.poll(() => collectionQueries.at(-1)).toContain("target.deleted==false");
    await expect.poll(() => bookingRequests).toBe(1);
    expect(bookingQuery?.searchParams.get("fields[bookings]")).toBe("id,target,timezone,start,end,state");

    const bookUrl = new URL(pageObj.bookButton.element().getAttribute("href") ?? "", window.location.origin);
    expect(bookUrl.pathname).toBe("/booking/calendar/bookings/add");
    expect(bookUrl.searchParams.get("date")).toBe("2026-08-17");
    expect(bookUrl.searchParams.get("target")).toBe("IN123");
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

  test("switches cached availability filters and uses each item's local date", async () => {
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
    expect(electronBookUrl.searchParams.get("date")).toBe("2026-08-16");

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
