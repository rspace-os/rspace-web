import { mapBookingDisplayPreferences } from "@/modules/booking/domain/bookingDisplayPreferences";
import type { BookingCatalogueManagerPage } from "@/modules/common/api/v2/generated/models/bookingCatalogueManagerPage";
import { BookingCatalogueManagerPageSchema } from "@/modules/common/api/v2/generated/valibot/schemas";
import "@/modules/common/styles/index.css";
import { createMemoryHistory, type RouterHistory } from "@tanstack/react-router";
import { cleanup, render } from "@testing-library/react";
import { getResponse, HttpResponse, http } from "msw";
import * as v from "valibot";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { withViewport } from "@/__tests__/pageObjects/viewport";

import { bookingPagesHandlers, resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { bookableItemsHandlers } from "../bookable-items/mocks/bookableItemsMocks";
import { institutionBookingPreferences } from "../preferences/bookingPreferencesFixtures";
import { CalendarPageStory } from "./CalendarPage.story";
import { CalendarAvailabilityPage } from "./pageObjects/CalendarAvailabilityPage";

const pageObj = new CalendarAvailabilityPage();

function registerHandlers(): void {
  worker.use(...bookingPagesHandlers());
}

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

function AllBookableItemsStory({
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?layout=availability&date=2026-08-17"] }),
  containerWidth = 1500,
}: {
  history?: RouterHistory;
  containerWidth?: number;
}) {
  return (
    <div style={{ width: containerWidth, maxWidth: "100%" }}>
      <CalendarPageStory history={history} preferences={mapBookingDisplayPreferences(institutionBookingPreferences)} />
    </div>
  );
}
let history: RouterHistory;
let browserUrl: string;

beforeEach(async () => {
  await page.viewport(1600, 1000);
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-08-17T08:30:00Z"));
  history = createMemoryHistory({ initialEntries: ["/booking/calendar?layout=availability&date=2026-08-17"] });
  browserUrl = window.location.href;
  resetBookingPageRequests();
  registerHandlers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  const actualUrl = window.location.href;
  window.history.replaceState({}, "", "/");
  expect(actualUrl).toBe(browserUrl);
});

describe("the Calendar Availability layout", () => {
  test("keeps owner and availability presets in the expression with the existing badge count", async () => {
    render(<AllBookableItemsStory history={history} />);
    const owner = page.getByRole("button", { name: "Owned Items" });
    await owner.click();
    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where"))
      .toBe("targetOwner==me");
    await expect.element(owner).toHaveAttribute("aria-pressed", "true");
    await pageObj.freeLaterToday.click();
    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where"))
      .toContain("targetOwner==me");
    await expect.element(page.getByRole("button", { name: "Filters 1 applied", exact: true })).toBeVisible();
    await owner.click();
    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where"))
      .toBe("availability==free-later-today");
  });

  test("reserves availability space beside realistic long item and location labels at 1280 px", async () => {
    const name = "Live-cell confocal microscope with environmental chamber";
    worker.use(
      http.get("/api/v2/booking-catalogue", async ({ request }) => {
        const response = await getResponse(
          bookableItemsHandlers(() => {}),
          request,
        );
        if (!response) throw new Error("Expected catalogue fixture response");
        const body = v.parse(BookingCatalogueManagerPageSchema, await response.json());
        if (!body.items) throw new Error("Expected catalogue items");
        return HttpResponse.json<BookingCatalogueManagerPage>({
          ...body,
          items: body.items.map((item, index) =>
            index === 0
              ? {
                  ...item,
                  name,
                  location: { name: "Imaging suite, room 2.14, north laboratory wing", globalId: "IC456" },
                }
              : item,
          ),
        });
      }),
    );
    await withViewport(1280, async () => {
      render(<AllBookableItemsStory history={history} containerWidth={934} />);
      const bar = page.getByRole("img", { name: `${name} availability`, exact: true });
      await expect.element(bar).toBeVisible();
      expect(bar.element().getBoundingClientRect().width).toBeGreaterThanOrEqual(280);
      const cell = bar.element().closest("td");
      if (!cell?.parentElement?.lastElementChild) throw new Error("Expected availability and actions cells");
      const actions = cell.parentElement.lastElementChild;
      expect(bar.element().getBoundingClientRect().right).toBeLessThan(actions.getBoundingClientRect().left);
      const label = page.getByRole("link", { name, exact: true }).first().element();
      expect(label.getBoundingClientRect().width).toBeLessThanOrEqual(240);
      const endLabel = () => {
        const label = bar.element().parentElement?.previousElementSibling?.lastElementChild;
        if (!label) throw new Error("Expected availability end label");
        return label;
      };
      expect(endLabel().getBoundingClientRect().right).toBeLessThan(actions.getBoundingClientRect().left);
    });
  });

  test("contains the whole availability bar and end label in a 320 px card", async () => {
    await withViewport(320, async () => {
      render(<AllBookableItemsStory history={history} containerWidth={320} />);
      await expect.element(pageObj.confocalAvailability).toBeVisible();
      const bar = pageObj.confocalAvailability.element();
      const card = pageObj.card("Confocal microscope").element().getBoundingClientRect();
      const endLabel = bar.parentElement?.previousElementSibling?.lastElementChild;
      if (!endLabel) throw new Error("Expected availability end label");
      for (const element of [bar, endLabel]) {
        const bounds = element.getBoundingClientRect();
        expect(bounds.left).toBeGreaterThanOrEqual(card.left);
        expect(bounds.right).toBeLessThanOrEqual(card.right);
        expect(bounds.right).toBeLessThanOrEqual(320);
      }
    });
  });

  test.each([1280, 1376])("fits availability inside its cell at %s px", async (width) => {
    await withViewport(width, async () => {
      render(<AllBookableItemsStory history={history} containerWidth={width - 346} />);
      await expect.element(pageObj.confocalAvailability).toBeVisible();
      const bar = pageObj.confocalAvailability.element();
      const cell = bar.closest("td");
      expect(cell).not.toBeNull();
      if (!cell) throw new Error("Availability must be in a table cell");
      expect(bar.getBoundingClientRect().right).toBeLessThanOrEqual(cell.getBoundingClientRect().right - 1);
      expect(cell.scrollWidth).toBeLessThanOrEqual(cell.clientWidth);
      const actions = cell.parentElement?.lastElementChild;
      if (!actions) throw new Error("Expected row actions");
      expect(bar.getBoundingClientRect().right).toBeLessThan(actions.getBoundingClientRect().left);
    });
  });

  test.each([390, 1440])("keeps the quick-filter toolbar and results width stable at %s px", async (width) => {
    const response = Promise.withResolvers<void>();
    worker.use(
      // Hold the quick-filtered table page, which the server filters before paging.
      http.get("/api/v2/booking-catalogue", async ({ request }) => {
        if (!new URL(request.url).searchParams.has("availability")) return undefined;
        await response.promise;
        return undefined;
      }),
    );
    await withViewport(width, async () => {
      history.replace(
        "/booking/calendar?layout=availability&date=2026-08-17&calendar-resources.where=availability%3D%3Davailable-now",
      );
      render(<AllBookableItemsStory history={history} containerWidth={width} />);
      try {
        const loading = page.getByText("Finding bookable items…", { exact: true });
        await expect.element(loading).toHaveClass("sr-only");
        await expect.element(page.getByText("Confocal microscope", { exact: true })).not.toBeInTheDocument();
        await document.fonts.ready;
        const results = width === 390 ? pageObj.cards : pageObj.tableElement;
        const before = results.element().getBoundingClientRect();
        const toolbarBefore = pageObj.toolbar.element().getBoundingClientRect();
        await expectNoAxeViolations();
        response.resolve();
        await expect.element(loading).not.toBeInTheDocument();
        await expect.element(pageObj.confocalAvailability).toBeVisible();
        const after = results.element().getBoundingClientRect();
        expect(Math.abs(after.width - before.width)).toBeLessThanOrEqual(2);
        expect(Math.abs(after.left - before.left)).toBeLessThanOrEqual(2);
        expect(Math.abs(after.top - before.top)).toBeLessThanOrEqual(2);
        expect(Math.abs(pageObj.toolbar.element().getBoundingClientRect().top - toolbarBefore.top)).toBeLessThanOrEqual(
          2,
        );
      } finally {
        response.resolve();
      }
    });
  });

  test("navigates through the instrument name to the bookable item details page", async () => {
    render(<AllBookableItemsStory history={history} />);

    await expect.element(pageObj.detailsButton).toBeVisible();
    await pageObj.instrumentName.click();

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => history.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  test("uses standardized cards in a narrow container with full-width availability and all controls", async () => {
    await withViewport(1200, async () => {
      render(<AllBookableItemsStory history={history} containerWidth={600} />);

      await expect.element(pageObj.cards).toBeVisible();
      await expect.element(pageObj.tableElement).not.toBeVisible();
      await expect.element(pageObj.card("Confocal microscope")).toBeVisible();
      await expect.element(pageObj.effectiveTimeZone).not.toBeInTheDocument();
      const availabilityText = pageObj.card("Confocal microscope").getByText("Available until 10:00", { exact: true });
      await expect.element(availabilityText).toBeVisible();
      await expect.element(pageObj.confocalAvailability).toBeVisible();
      await expect
        .poll(
          () =>
            availabilityText.element().getBoundingClientRect().bottom <=
            pageObj.confocalAvailability.element().getBoundingClientRect().top,
        )
        .toBe(true);
      await expect.element(pageObj.search).toBeVisible();
      await expect.element(pageObj.filtersButton).toBeVisible();
      await expect.element(pageObj.availableNow).toBeVisible();
      await expect.element(pageObj.freeLaterToday).toBeVisible();
      await expect.element(pageObj.toolbar).toBeVisible();
      await expect.element(pageObj.dateControls).toBeVisible();
      await expect.element(pageObj.datePicker).toBeVisible();
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
    });
  });

  test("wraps quick-filter buttons in a very narrow container", async () => {
    await withViewport(1200, async () => {
      render(<AllBookableItemsStory history={history} containerWidth={360} />);

      await expect.element(pageObj.availableNow).toBeVisible();
      await expect.element(pageObj.freeLaterToday).toBeVisible();
      await expect
        .poll(
          () =>
            pageObj.freeLaterToday.element().getBoundingClientRect().top >
            pageObj.availableNow.element().getBoundingClientRect().top,
        )
        .toBe(true);
    });
  });

  test("keeps a many-event slice scrollable and usable", async () => {
    render(<AllBookableItemsStory history={history} />);

    await expect.element(pageObj.availabilitySlice("Confocal microscope", 11)).toBeVisible();
    await pageObj.heading.hover();
    await userEvent.keyboard("{Tab}");
    pageObj.focusAvailabilitySlice("Confocal microscope", 11);

    await expect.element(pageObj.availabilityDetails).toBeVisible();
    await expect.poll(() => pageObj.availabilityBookingRows.all().length).toBe(11);
    await expect.poll(() => pageObj.availabilityDetailsIsScrollable()).toBe(true);
    expect(pageObj.availabilityDetailsBorderWidth()).toBe("0px");
    await expect.element(pageObj.availabilityDetails).toMatchTextContent("12:00");
    await expect.element(pageObj.availabilityDetails).toMatchTextContent("14:00");
    await expect.element(pageObj.availabilityDetails).not.toMatchTextContent("UTC");
    await expect.element(pageObj.availabilityDetails.getByText("Booked", { exact: true })).not.toBeInTheDocument();
    await pageObj.availabilityDetails
      .getByLabelText("Show details for Confocal microscope", { exact: false })
      .first()
      .click();
    const detailsLink = pageObj.availabilityDetails.getByRole("link", { name: "Details" });
    await expect.element(detailsLink).toBeVisible();
    expect(getComputedStyle(detailsLink.element()).borderLeftColor).toBe("rgba(0, 0, 0, 0)");
    await expectNoAxeViolations();
  });

  test("shows the fixture maintenance blockout", async () => {
    render(<AllBookableItemsStory history={history} />);

    const slice = pageObj.availabilitySlice("Confocal microscope", 1, "Maintenance", ".*10:00.*11:00.*");
    await slice.hover();

    await expect.element(pageObj.availabilityDetails).toBeVisible();
    await pageObj.availabilityDetails.getByLabelText("Show details for Maintenance,", { exact: false }).click();
    await expect.element(pageObj.availabilityDetails.getByText("Maintenance", { exact: true })).toBeVisible();
    await expect
      .element(pageObj.availabilityDetails.getByText("Outside opening hours", { exact: true }))
      .not.toBeInTheDocument();
    await expect.element(pageObj.availabilityDetails.getByText("Booked", { exact: true })).not.toBeInTheDocument();
    await pageObj.availabilityDetails.hover();
    await expect.element(pageObj.availabilityDetails).toBeVisible();

    await pageObj.heading.hover();
    await expect.element(pageObj.availabilityDetails).not.toBeInTheDocument();
    await pageObj.availabilitySlice("Confocal microscope", 1, "Unavailable", ".*15:00.*18:00.*").hover();
    await expect.element(pageObj.availabilityDetails.getByText("Outside opening hours", { exact: true })).toBeVisible();
    await expect.element(pageObj.availabilityDetails.getByText("Maintenance", { exact: true })).not.toBeInTheDocument();
  });

  test("opens slice details from keyboard focus and restores focus after Escape", async () => {
    render(<AllBookableItemsStory history={history} />);

    await expect.element(pageObj.availabilitySlice("Confocal microscope", 11)).toBeVisible();
    await pageObj.heading.hover();
    await userEvent.keyboard("{Tab}");
    pageObj.focusAvailabilitySlice("Confocal microscope", 11);
    await expect.element(pageObj.availabilityDetails).toBeVisible();

    await userEvent.keyboard("{Escape}");

    await expect.element(pageObj.availabilityDetails).not.toBeInTheDocument();
    expect(document.activeElement).toBe(pageObj.availabilitySlice("Confocal microscope", 11).element());
  });

  test("opens slice details when clicked and keeps a narrow card in the viewport", async () => {
    await withViewport(400, async () => {
      render(<AllBookableItemsStory history={history} containerWidth={360} />);

      // Hover opens the popup before WebKit completes its click actionability check.
      await pageObj.availabilitySlice("Confocal microscope", 11).click({ force: true });
      await expect.element(pageObj.availabilityDetails).toBeVisible();

      const details = pageObj.availabilityDetailsRect();
      expect(details.left).toBeGreaterThanOrEqual(0);
      expect(details.right).toBeLessThanOrEqual(window.innerWidth);
      expect(details.top).toBeGreaterThanOrEqual(0);
      expect(details.bottom).toBeLessThanOrEqual(window.innerHeight);
    });
  });

  test("does not render an unnecessary horizontal scrollbar", async () => {
    await withViewport(1600, async () => {
      render(<AllBookableItemsStory history={history} />);
      await expect.element(pageObj.table).toBeVisible();
      await expect
        .poll(() => {
          const container = pageObj.table.element().parentElement;
          return container ? container.scrollWidth - container.clientWidth : null;
        })
        .toBe(0);
    });
  });

  test("switches cached availability filters using the shared display interval", async () => {
    render(<AllBookableItemsStory history={history} />);

    await expect.element(pageObj.datePicker).toBeVisible();
    await pageObj.filtersButton.click();
    await pageObj.freeLaterToday.click();

    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where"))
      .toBe("availability==free-later-today");
    await expect.element(pageObj.freeLaterToday).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.datePicker).toBeVisible();
    await expect
      .element(pageObj.filtersPanel.getByRole("combobox", { name: "Value for filter 1" }))
      .toHaveValue("Free later");
    await pageObj.filtersPanel.getByRole("button", { name: "Remove filter 1" }).click();
    await pageObj.filtersPanel.getByRole("button", { name: "Apply filters" }).click();
    await expect.poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where")).toBeNull();
    await expect.element(pageObj.freeLaterToday).toHaveAttribute("aria-pressed", "false");

    await pageObj.freeLaterToday.click();
    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where"))
      .toBe("availability==free-later-today");
    await expect.element(pageObj.electronMicroscope).toBeVisible();
    await expect.element(pageObj.item).not.toBeInTheDocument();
    await expect.element(pageObj.massSpectrometer).not.toBeInTheDocument();
    await expect.element(pageObj.flowCytometer).not.toBeInTheDocument();
    const electronBookUrl = new URL(
      pageObj.electronBookButton.element().getAttribute("href") ?? "",
      window.location.origin,
    );
    expect(electronBookUrl.searchParams.get("date")).toBe("2026-08-17");

    await pageObj.availableNow.click();

    await expect
      .poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where"))
      .toBe("availability==available-now");
    await expect.element(pageObj.availableNow).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.item).toBeVisible();
    await expect.element(pageObj.flowCytometer).toBeVisible();
    await expect.element(pageObj.electronMicroscope).not.toBeInTheDocument();
    await expect.element(pageObj.massSpectrometer).not.toBeInTheDocument();
    await pageObj.nextDay.click();

    await expect.poll(() => history.location.search).toContain("date=2026-08-18");
    await expect.poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where")).toBeNull();
    await pageObj.resetTable.click();
    await expect.poll(() => new URLSearchParams(history.location.search).get("calendar-resources.where")).toBeNull();
    await expect.element(page.getByRole("button", { name: "View: By Item · Day", exact: true })).toBeVisible();
    await expectNoAxeViolations();
  });
});

test("names desktop icon actions on hover and keyboard focus", async () => {
  await page.viewport(1376, 795);
  render(<AllBookableItemsStory containerWidth={1100} />);
  for (const control of [pageObj.detailsButton, pageObj.electronBookButton]) {
    await control.hover();
    await expect.element(page.getByRole("tooltip")).toBeVisible();
    await pageObj.heading.hover();
    control.element().focus();
    await userEvent.keyboard("{Tab}{Shift>}{Tab}{/Shift}");
    await expect.element(page.getByRole("tooltip")).toBeVisible();
  }
});
