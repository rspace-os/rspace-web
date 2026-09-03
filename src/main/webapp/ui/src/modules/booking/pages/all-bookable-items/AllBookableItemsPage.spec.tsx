import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import { bookingPagesHandlers, resetBookingPageRequests } from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { currentUser } from "../calendar/calendarFixtures";
import { AllBookableItemsStory } from "./AllBookableItemsPage.story";
import { AllBookableItemsPage } from "./pageObjects/AllBookableItemsPage";

const pageObj = new AllBookableItemsPage();

function registerHandlers(): void {
  worker.use(...bookingPagesHandlers());
}

// Register before this file's browserSetup beforeAll starts the per-file worker. Firefox can
// otherwise race the first runtime handler update when this spec follows another file.
registerHandlers();

beforeEach(() => {
  resetBookingPageRequests();
  window.history.replaceState({}, "", "/");
  registerHandlers();
});

afterEach(() => {
  window.history.replaceState({}, "", "/");
  cleanup();
});

describe("the All Bookable Items page", () => {
  test("navigates to the bookable item details page", async () => {
    render(<AllBookableItemsStory />);

    await expect.element(pageObj.detailsButton).toBeVisible();
    await pageObj.detailsButton.click();

    await expect.element(pageObj.bookableItemDetailsHeading).toBeVisible();
    await expect.element(pageObj.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN123");
  });

  test("starts immutable maintenance creation from bookable item details for a direct sysadmin", async () => {
    worker.use(
      http.get("/api/v2/users/me", () =>
        HttpResponse.json({
          ...currentUser,
          hasSysAdminRole: true,
          session: { ...currentUser.session, operatedAs: false },
        }),
      ),
    );
    render(<AllBookableItemsStory />);
    await pageObj.detailsButton.click();

    await page.getByRole("button", { name: "More event creation options" }).click();
    await page.getByRole("menuitem", { name: "New Maintenance Event" }).click();
    const dialog = page.getByRole("dialog", { name: "New Maintenance Event" });
    await expect.element(dialog).toBeVisible();
    await expect.element(dialog.getByText("Confocal microscope", { exact: true })).toBeVisible();
    await expect.element(dialog.getByRole("combobox", { name: "Bookable item" })).not.toBeInTheDocument();
    await expect.element(dialog.getByRole("radio")).not.toBeInTheDocument();
    await dialog.getByRole("button", { name: "Cancel" }).click();
  });

  test("uses standardized cards in a narrow container with full-width availability and all controls", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1200, 900);

    try {
      render(<AllBookableItemsStory containerWidth={600} />);

      await expect.element(pageObj.cards).toBeVisible();
      await expect.element(pageObj.tableElement).not.toBeVisible();
      await expect.element(pageObj.card("Confocal microscope")).toBeVisible();
      await expect.element(pageObj.effectiveTimeZone).toBeVisible();
      await expect.element(pageObj.confocalAvailability).toBeVisible();
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

  test("keeps merged slice details open while moving from the slice into the card", async () => {
    render(<AllBookableItemsStory />);

    const slice = pageObj.availabilitySlice("Confocal microscope", 2);
    await slice.hover();

    await expect.element(pageObj.availabilityDetails).toBeVisible();
    await expect.poll(() => pageObj.availabilityBookingRows.all().length).toBe(2);
    await expect.element(pageObj.availabilityDetails).toHaveTextContent("12:00");
    await expect.element(pageObj.availabilityDetails).toHaveTextContent("14:00");
    await expect.element(pageObj.availabilityDetails).not.toHaveTextContent("UTC");
    await expect.element(pageObj.availabilityDetails.getByText("Booked", { exact: true })).not.toBeInTheDocument();
    await pageObj.availabilityDetails.hover();
    await new Promise((resolve) => window.setTimeout(resolve, 250));
    await expect.element(pageObj.availabilityDetails).toBeVisible();
    await expectNoAxeViolations();

    await pageObj.heading.hover();
    await expect.element(pageObj.availabilityDetails).not.toBeInTheDocument();
  });

  test("shows the fixture maintenance blockout", async () => {
    render(<AllBookableItemsStory />);

    const slice = pageObj.availabilitySlice("Confocal microscope", 1, "Outside opening hours");
    await slice.hover();

    await expect.element(pageObj.availabilityDetails).toBeVisible();
    await expect.element(pageObj.availabilityDetails.getByText("Outside opening hours", { exact: true })).toBeVisible();
    await expect.element(pageObj.availabilityDetails.getByText("Booked", { exact: true })).not.toBeInTheDocument();
  });

  test("opens slice details from keyboard focus and restores focus after Escape", async () => {
    render(<AllBookableItemsStory />);

    await expect.element(pageObj.availabilitySlice("Confocal microscope", 2)).toBeVisible();
    pageObj.focusAvailabilitySlice("Confocal microscope", 2);
    await expect.element(pageObj.availabilityDetails).toBeVisible();

    await pageObj.pressEscape();

    await expect.element(pageObj.availabilityDetails).not.toBeInTheDocument();
    expect(document.activeElement).toBe(pageObj.availabilitySlice("Confocal microscope", 2).element());
  });

  test("opens slice details when clicked and keeps a narrow card in the viewport", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(400, 900);

    try {
      render(<AllBookableItemsStory containerWidth={360} />);

      await pageObj.availabilitySlice("Confocal microscope", 2).click();
      await expect.element(pageObj.availabilityDetails).toBeVisible();

      const details = pageObj.availabilityDetailsRect();
      expect(details.left).toBeGreaterThanOrEqual(0);
      expect(details.right).toBeLessThanOrEqual(window.innerWidth);
      expect(details.top).toBeGreaterThanOrEqual(0);
      expect(details.bottom).toBeLessThanOrEqual(window.innerHeight);
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
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

    await expect.element(pageObj.datePicker).toBeVisible();
    await pageObj.freeLaterToday.click();

    await expect.poll(() => window.location.search).toContain("availability=free-later-today");
    await expect.element(pageObj.freeLaterToday).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.quickFilterScope).toBeVisible();
    await expect.element(pageObj.datePicker).not.toBeInTheDocument();
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

    await expect.poll(() => window.location.search).toContain("availability=available-now");
    await expect.element(pageObj.availableNow).toHaveAttribute("aria-pressed", "true");
    await expect.element(pageObj.item).toBeVisible();
    await expect.element(pageObj.flowCytometer).toBeVisible();
    await expect.element(pageObj.electronMicroscope).not.toBeInTheDocument();
    await expect.element(pageObj.massSpectrometer).not.toBeInTheDocument();
    await pageObj.availableNow.click();

    await expect.poll(() => window.location.search).not.toContain("availability=");
    await expect.element(pageObj.datePicker).toBeVisible();
    await expect.element(pageObj.electronMicroscope).toBeVisible();
    await expect.element(pageObj.massSpectrometer).toBeVisible();
    await expectNoAxeViolations();
  });
});
