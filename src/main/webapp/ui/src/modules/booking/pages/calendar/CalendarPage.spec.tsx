import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { expectNoAxeViolations } from "@/__tests__/pageObjects/accessibility";
import {
  bookingPageRequests,
  bookingPagesHandlers,
  calendarBookingFields,
  resetBookingPageRequests,
} from "@/modules/booking/pages/mocks/bookingPagesMocks";
import { collectionResponse, noParentBooking } from "./__tests__/calendarTestHarness";
import { CalendarPageStory } from "./CalendarPage.story";
import { currentUser } from "./calendarFixtures";
import { CalendarPage as CalendarPageObject } from "./pageObjects/CalendarPage";

const calendar = new CalendarPageObject();

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

describe("Calendar page", () => {
  test("renders visible booking identities as user badges without leaking busy identities", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    const visibleIdentity = calendar.event("Confocal microscope").getByText("Ada Lovelace (ada)", { exact: true });
    await expect.element(visibleIdentity).toBeVisible();
    expect(visibleIdentity.element().closest('[data-slot="user-badge"]')).not.toBeNull();
    await expect
      .element(calendar.event("Busy").getByText("Ada Lovelace (ada)", { exact: true }))
      .not.toBeInTheDocument();

    await calendar.month.click();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect
      .poll(() => {
        const event = calendar.event("Confocal microscope").element();
        const badge = event.querySelector<HTMLElement>('[data-slot="user-badge"]');
        const title = Array.from(event.querySelectorAll<HTMLElement>("span")).find(
          (element) => element.textContent === "Confocal microscope",
        );
        const badgeBox = badge?.getBoundingClientRect();
        const titleBox = title?.getBoundingClientRect();
        return {
          badgeIsCompact: (badge?.getBoundingClientRect().height ?? Infinity) <= 20,
          contentFits: event.scrollHeight <= event.clientHeight + 1,
          identityOnSeparateLine: badgeBox !== undefined && titleBox !== undefined && badgeBox.top >= titleBox.bottom,
        };
      })
      .toEqual({ badgeIsCompact: true, contentFits: true, identityOnSeparateLine: true });
    await expectNoAxeViolations();
  });

  test("keeps the event expand control fixed when details open", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    const event = calendar.event("Confocal microscope");
    await expect.element(event).toBeVisible();
    const indicator = event.element().querySelector<HTMLElement>("[data-event-expand-indicator]");
    expect(indicator).not.toBeNull();
    const collapsedPosition = indicator?.getBoundingClientRect();

    await calendar.showEventDetails("Confocal microscope").click();
    await expect.element(event.getByRole("button", { name: /Hide details/ })).toHaveAttribute("aria-expanded", "true");
    const expandedPosition = indicator?.getBoundingClientRect();

    expect(expandedPosition?.top).toBe(collapsedPosition?.top);
    expect(expandedPosition?.right).toBe(collapsedPosition?.right);
  });

  test("navigates from a busy event to the bookable item details page", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await calendar.showEventDetails("Busy").click();
    await calendar.viewItemDetails.click();

    await expect.element(calendar.bookableItemDetailsHeading).toBeVisible();
    await expect.element(calendar.bookableItemDetailsTarget).toBeVisible();
    await expect.poll(() => window.location.pathname).toBe("/booking/bookable-items/IN124");
  });

  test("closes booking and busy cards when the page is clicked", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await calendar.event("Confocal microscope").click();
    await expect
      .element(calendar.event("Confocal microscope").getByRole("button", { name: /Hide details/ }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Confocal microscope")).toHaveAttribute("aria-expanded", "false");

    await calendar.event("Busy").click();
    await expect
      .element(calendar.event("Busy").getByRole("button", { name: /Hide details/ }))
      .toHaveAttribute("aria-expanded", "true");
    await calendar.heading.click();
    await expect.element(calendar.showEventDetails("Busy")).toHaveAttribute("aria-expanded", "false");
  });

  test("places new booking beside the Calendar heading and the toolbar beneath it", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(1024, 800);
    try {
      window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
      render(<CalendarPageStory />);

      await expect.element(calendar.newBooking).toBeVisible();
      await expect.element(calendar.toolbar).toBeVisible();
      await expect.element(page.getByText("Browse booking events by day, week, or month.")).not.toBeInTheDocument();
      await expect
        .poll(() => {
          const heading = calendar.heading.element().getBoundingClientRect();
          const creationElement = calendar.newBooking.element();
          const creation = creationElement.getBoundingClientRect();
          const creationStyle = getComputedStyle(creationElement);
          const creationIcon = creationElement.querySelector("svg")?.getBoundingClientRect();
          const toolbar = calendar.toolbar.element().getBoundingClientRect();
          const previousStyle = getComputedStyle(calendar.previous.element());
          const nextStyle = getComputedStyle(calendar.next.element());
          return {
            creationRightOfHeading: creation.left >= heading.right,
            toolbarBelowHeader: toolbar.top >= Math.max(heading.bottom, creation.bottom),
            creationHeight: creation.height,
            creationRadii: [
              creationStyle.borderTopLeftRadius,
              creationStyle.borderTopRightRadius,
              creationStyle.borderBottomRightRadius,
              creationStyle.borderBottomLeftRadius,
            ],
            creationIconSize: creationIcon ? [creationIcon.width, creationIcon.height] : null,
            navigationRadii: [
              previousStyle.borderTopLeftRadius,
              previousStyle.borderTopRightRadius,
              nextStyle.borderTopLeftRadius,
              nextStyle.borderTopRightRadius,
            ],
            timeZoneHeight: calendar.timeZone.element().getBoundingClientRect().height,
          };
        })
        .toEqual({
          creationRightOfHeading: true,
          toolbarBelowHeader: true,
          creationHeight: 32,
          creationRadii: ["4px", "4px", "4px", "4px"],
          creationIconSize: [14, 14],
          navigationRadii: ["4px", "0px", "0px", "4px"],
          timeZoneHeight: 36,
        });
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("flows the calendar toolbar into two bounded rows on a small viewport", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(600, 800);
    try {
      window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
      render(<CalendarPageStory />);
      await expect.element(calendar.toolbar).toBeVisible();

      await expect
        .poll(() => {
          const toolbar = calendar.toolbar.element().getBoundingClientRect();
          const dateControls = calendar.dateControls.element().getBoundingClientRect();
          const displayControls = calendar.displayControls.element().getBoundingClientRect();
          return {
            displayBelowDate: displayControls.top >= dateControls.bottom,
            toolbarInsideViewport: toolbar.left >= 0 && toolbar.right <= window.innerWidth,
            controlsInsideToolbar: displayControls.left >= toolbar.left && displayControls.right <= toolbar.right,
            pageOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
          };
        })
        .toEqual({
          displayBelowDate: true,
          toolbarInsideViewport: true,
          controlsInsideToolbar: true,
          pageOverflow: 0,
        });
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });

  test("uses live booking events across every prototype layout and period", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);

    await expect.element(calendar.heading).toBeVisible();
    await expect.element(calendar.timeGrid).toBeVisible();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    const compactIdentity = calendar.event("Confocal microscope").getByText("Ada Lovelace (ada)", { exact: true });
    await expect.element(compactIdentity).toBeVisible();
    expect(compactIdentity.element().closest('[data-slot="user-badge"]')).not.toBeNull();
    await expect.element(calendar.event("Busy")).toBeVisible();
    await expect
      .element(calendar.event("Busy").getByText("Ada Lovelace (ada)", { exact: true }))
      .not.toBeInTheDocument();
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(1);
    expect(bookingPageRequests.calendarBookingRequests[0].searchParams.get("fields[bookings]")).toBe(
      calendarBookingFields,
    );
    expect(bookingPageRequests.calendarBookingRequests[0].searchParams.get("where")).toContain("state==CONFIRMED");

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
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(3);
    expect(bookingPageRequests.calendarBookingRequests.at(-1)?.searchParams.get("where")).toContain(
      "target=in=(IN123,IN124,IN125,IN126,IN127)",
    );
    await expect.poll(() => page.getByTestId("day-timeline-scroller").all().length).toBe(5);

    await calendar.mine.click();
    await expect.element(calendar.event("Confocal microscope")).toBeVisible();
    await expect.element(calendar.event("Electron microscope")).not.toBeInTheDocument();

    await calendar.agenda.click();
    await expect.element(calendar.bookingAgenda).toBeVisible();
    await calendar.month.click();
    await expect.poll(() => bookingPageRequests.calendarBookingRequests.length).toBe(5);
    await expectNoAxeViolations();
  });

  test("keeps a drag-created booking attached to its first resource and blocks a second drag", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => page.getByTestId("day-timeline-canvas").all().length).toBe(5);
    const canvases = calendar.resourceCanvases;
    await calendar.dragResourceSelection(0, 0.35, 0.4, 1);
    const dialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(dialog).toBeVisible();
    await expect.element(page.getByTestId("compact-booking-draft-marker")).toBeVisible();
    await expect.element(dialog).not.toHaveAttribute("aria-modal", "true");
    await expect.element(dialog.getByText("Confocal microscope", { exact: true })).toBeVisible();
    await expect
      .poll(() => canvases.every((canvas) => canvas.element().dataset.creationDisabled === "true"))
      .toBe(true);

    await calendar.dragResourceSelection(1, 0.5, 0.55, 2);
    await expect.poll(() => page.getByRole("dialog", { name: "New Booking" }).all().length).toBe(1);
    await expect.element(dialog.getByText("Confocal microscope", { exact: true })).toBeVisible();
    await expect.element(dialog.getByText("Electron microscope", { exact: true })).not.toBeInTheDocument();
  });

  test("starts a resource booking from the keyboard with a proposed free hour", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    const addForMassSpectrometer = page.getByRole("button", { name: "Add booking for Mass spectrometer" });
    addForMassSpectrometer.element().focus();
    await expect.element(addForMassSpectrometer).toHaveFocus();
    await userEvent.keyboard("{Enter}");

    const dialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(dialog.getByText("Mass spectrometer", { exact: true })).toBeVisible();
    await expect.element(dialog.getByRole("group", { name: "Start" }).getByLabelText("Time")).toHaveValue("08:00");
    await expect.element(dialog.getByRole("group", { name: "End" }).getByLabelText("Time")).toHaveValue("09:00");
  });

  test("disables resource-row creation when no free window exists", async () => {
    worker.use(
      http.get("/api/v2/bookings", ({ request }) => {
        if (new URL(request.url).searchParams.get("fields[bookings]") !== calendarBookingFields) return undefined;
        return HttpResponse.json(
          collectionResponse([
            {
              ...noParentBooking,
              start: "2026-08-16T00:00:00Z",
              end: "2026-08-19T00:00:00Z",
            },
          ]),
        );
      }),
    );
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    await calendar.resources.click();
    await calendar.day.click();
    await expect.poll(() => calendar.resourceCanvases.length).toBe(5);

    await expect.element(page.getByRole("button", { name: "Add booking for Mass spectrometer" })).toBeDisabled();
  });

  test("creates a booking through the targetless compact form and restores trigger focus", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    const trigger = page.getByRole("button", { name: "New Booking" });
    const dialog = await calendar.openTargetlessBookingDialog();
    expect(getComputedStyle(dialog.element()).borderRadius).toBe("8px");
    expect(getComputedStyle(dialog.getByRole("button", { name: "Cancel" }).element()).borderRadius).toBe("0px");
    await dialog.getByRole("group", { name: "Start" }).getByLabelText("Time").fill("09:00");
    await dialog.getByRole("group", { name: "End" }).getByLabelText("Time").fill("10:00");
    await dialog.getByRole("textbox", { name: "Purpose" }).fill("Live-stack-shaped booking");
    await dialog.getByRole("button", { name: "Book", exact: true }).click();

    await expect.poll(() => bookingPageRequests.createdPayloads.length).toBe(1);
    expect(bookingPageRequests.createdPayloads[0]).toMatchObject({
      target: { relationTo: "booking-instruments", value: 123 },
      kind: "BOOKING",
      purpose: "Live-stack-shaped booking",
    });
    await expect.element(dialog).not.toBeInTheDocument();
    await expect.poll(() => document.activeElement).toBe(trigger.element());
  });

  test("opens the full booking form from compact More options", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    const dialog = await calendar.openTargetlessBookingDialog();

    await dialog.getByRole("button", { name: "More options" }).click();
    await expect.poll(() => window.location.pathname).toBe("/booking/calendar/bookings/add");
    await expect.poll(() => new URLSearchParams(window.location.search).get("target")).toBe("IN123");
    await expect.element(page.getByRole("heading", { name: "Add Booking" })).toBeVisible();
  });

  test("keeps the popover open after outside press and confirms dirty browser navigation", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-16");
    window.history.pushState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    const trigger = page.getByRole("button", { name: "New Booking" });

    await trigger.click();
    const cleanDialog = page.getByRole("dialog", { name: "New Booking" });
    await expect.element(cleanDialog).toBeVisible();
    await calendar.heading.click();
    await expect.element(cleanDialog).toBeVisible();
    await cleanDialog.getByRole("button", { name: "Cancel" }).click();
    await expect.element(cleanDialog).not.toBeInTheDocument();
    await expect.poll(() => document.activeElement).toBe(trigger.element());

    const dirtyDialog = await calendar.openTargetlessBookingDialog();
    await dirtyDialog.getByRole("textbox", { name: "Purpose" }).fill("Keep this draft");
    window.history.back();
    const confirmation = page.getByRole("alertdialog", { name: "Discard this event?" });
    await expect.element(confirmation).toBeVisible();
    await confirmation.getByRole("button", { name: "Keep editing" }).click();
    await expect.element(dirtyDialog.getByRole("textbox", { name: "Purpose" })).toHaveValue("Keep this draft");
    await expect.poll(() => window.location.search).toContain("date=2026-08-17");

    window.history.back();
    await expect.element(confirmation).toBeVisible();
    await confirmation.getByRole("button", { name: "Discard changes" }).click();
    await expect.element(dirtyDialog).not.toBeInTheDocument();
    await expect.poll(() => window.location.search).toContain("date=2026-08-16");
  });

  test("preserves a compact draft across recoverable server conflicts", async () => {
    let code = "errors.api.v2.booking.overlap";
    worker.use(
      http.post("/api/v2/bookings", () =>
        HttpResponse.json({ status: 409, code, detail: "private server detail" }, { status: 409 }),
      ),
    );
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(<CalendarPageStory />);
    const dialog = await calendar.openTargetlessBookingDialog();
    const startTime = dialog.getByRole("group", { name: "Start" }).getByLabelText("Time");
    const endTime = dialog.getByRole("group", { name: "End" }).getByLabelText("Time");
    const submit = dialog.getByRole("button", { name: "Book", exact: true });
    await startTime.fill("09:00");
    await endTime.fill("10:00");
    const purpose = dialog.getByRole("textbox", { name: "Purpose" });
    await purpose.fill("Preserve this draft");

    await submit.click();
    await expect.element(dialog.getByText("This period overlaps another booking.")).toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");
    await expect.element(submit).toBeDisabled();

    await dialog.getByRole("group", { name: "End" }).getByLabelText("Time").fill("10:30");
    code = "errors.api.v2.booking.concurrentModification";
    await submit.click();
    await expect
      .element(
        dialog.getByText("This event changed while you were editing it. Review the latest details and try again."),
      )
      .toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");

    code = "errors.api.v2.booking.target.unavailable";
    await submit.click();
    await expect.element(dialog.getByText("This bookable item is unavailable.")).toBeVisible();
    await expect.element(purpose).toHaveValue("Preserve this draft");
  });

  test("does not expose maintenance creation to a run-as sysadmin", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(
      <CalendarPageStory
        user={{
          ...currentUser,
          hasSysAdminRole: true,
          session: { ...currentUser.session, operatedAs: true },
        }}
      />,
    );

    await expect.element(page.getByRole("button", { name: "New Booking" })).toBeVisible();
    await expect.element(page.getByRole("button", { name: "More event creation options" })).not.toBeInTheDocument();
  });

  test("confirms dirty dismissal and keeps maintenance type immutable for a direct sysadmin", async () => {
    window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
    render(
      <CalendarPageStory
        user={{
          ...currentUser,
          hasSysAdminRole: true,
          session: { ...currentUser.session, operatedAs: false },
        }}
      />,
    );
    const newBooking = page.getByRole("button", { name: "New Booking" });
    const moreCreationOptions = page.getByRole("button", { name: "More event creation options" });
    await expect
      .poll(() => {
        const bookingElement = newBooking.element();
        const moreElement = moreCreationOptions.element();
        const bookingIcon = bookingElement.querySelector("svg")?.getBoundingClientRect();
        const moreIcon = moreElement.querySelector("svg")?.getBoundingClientRect();
        const bookingStyle = getComputedStyle(bookingElement);
        const moreStyle = getComputedStyle(moreElement);
        return {
          bookingRadii: [
            bookingStyle.borderTopLeftRadius,
            bookingStyle.borderTopRightRadius,
            bookingStyle.borderBottomRightRadius,
            bookingStyle.borderBottomLeftRadius,
          ],
          moreOuterRadii: [moreStyle.borderTopRightRadius, moreStyle.borderBottomRightRadius],
          iconSizes: [
            bookingIcon ? [bookingIcon.width, bookingIcon.height] : null,
            moreIcon ? [moreIcon.width, moreIcon.height] : null,
          ],
        };
      })
      .toEqual({
        bookingRadii: ["4px", "0px", "0px", "4px"],
        moreOuterRadii: ["4px", "4px"],
        iconSizes: [
          [14, 14],
          [14, 14],
        ],
      });

    await moreCreationOptions.click();
    const newMaintenance = page.getByRole("menuitem", { name: "New Maintenance Event" });
    await expect
      .poll(() => {
        const icon = newMaintenance.element().querySelector("svg")?.getBoundingClientRect();
        return icon ? [icon.width, icon.height] : null;
      })
      .toEqual([14, 14]);
    await newMaintenance.click();
    const dialog = page.getByRole("dialog", { name: "New Maintenance Event" });
    await expect.element(dialog).toBeVisible();
    await expect.element(dialog.getByRole("radio")).not.toBeInTheDocument();
    await dialog.getByRole("textbox", { name: "Notes" }).fill("Laser service");
    await userEvent.keyboard("{Escape}");

    const confirmation = page.getByRole("alertdialog", { name: "Discard this event?" });
    await expect.element(confirmation).toBeVisible();
    await confirmation.getByRole("button", { name: "Keep editing" }).click();
    await expect.element(dialog.getByRole("textbox", { name: "Notes" })).toHaveValue("Laser service");
    await dialog.getByRole("button", { name: "Cancel" }).click();
    await confirmation.getByRole("button", { name: "Discard changes" }).click();
    await expect.element(dialog).not.toBeInTheDocument();
  });

  test("keeps the compact form readable in a narrow viewport", async () => {
    const originalViewport = { width: window.innerWidth, height: window.innerHeight };
    await page.viewport(390, 667);
    try {
      window.history.replaceState({}, "", "/booking/calendar?date=2026-08-17");
      render(<CalendarPageStory />);
      await page.getByRole("button", { name: "New Booking" }).click();
      const dialog = page.getByRole("dialog", { name: "New Booking" });
      await expect.element(dialog).toBeVisible();
      await expect
        .poll(() => {
          const element = dialog.element();
          const bounds = element.getBoundingClientRect();
          return {
            left: Math.round(bounds.left),
            right: Math.round(bounds.right),
            viewport: window.innerWidth,
            overflow: element.scrollWidth - element.clientWidth,
          };
        })
        .toEqual({ left: 16, right: 374, viewport: 390, overflow: 0 });
      await expect.element(dialog.getByRole("textbox", { name: "Purpose" })).toBeVisible();
      await dialog.getByRole("button", { name: "Cancel" }).click();
    } finally {
      await page.viewport(originalViewport.width, originalViewport.height);
    }
  });
});
