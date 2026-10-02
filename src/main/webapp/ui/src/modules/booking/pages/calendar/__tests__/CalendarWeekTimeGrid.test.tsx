import "@/__tests__/__mocks__/matchMedia";
import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  Outlet,
  RouterProvider,
} from "@tanstack/react-router";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import bookingEnglish from "@/modules/common/i18n/locales/en-US/booking.json";
import commonEnglish from "@/modules/common/i18n/locales/en-US/common.json";
import { CalendarTimeGrid } from "../CalendarTimeGrid";
import { calendarDates, formatDate } from "../calendarLayoutUtils";

const i18nConfig = { resources: { booking: bookingEnglish, common: commonEnglish }, defaultNS: "common" };

function booking(id: number, start: string, end: string, name = "Confocal microscope"): BookingListDocument {
  return {
    id,
    target: {
      relationTo: "booking-instruments",
      globalId: `IN${100 + id}`,
      value: { id: 100 + id, name, deleted: false, parentContainerName: null, parentContainerGlobalId: null },
    },
    canViewConfiguration: true,
    requesterId: 1,
    timezone: "UTC",
    start,
    end,
    state: "CONFIRMED",
    purpose: "Cell imaging",
    bookedBy: "Ada Lovelace (ada)",
    privacy: "full",
    canEdit: true,
    version: 0,
    kind: "BOOKING",
    createdAt: "2026-08-01T09:00:00Z",
    updatedAt: "2026-08-01T09:00:00Z",
    canCancel: false,
  };
}

function weekGrid(events: readonly BookingListDocument[], isLoading: boolean, onShowDay: (day: string) => void) {
  return (
    <CalendarTimeGrid
      date="2026-08-19"
      view="week"
      events={events}
      timezone="UTC"
      today="2026-08-19"
      availabilityStartMinute={8 * 60}
      availabilityEndMinute={18 * 60}
      onShowDay={onShowDay}
      isLoading={isLoading}
    />
  );
}

function renderWeek(events: readonly BookingListDocument[], isLoading = false, onShowDay = vi.fn()) {
  return renderWithRealI18n(weekGrid(events, isLoading, onShowDay), i18nConfig);
}

/** The overflow list links each booking to its page, so it needs a router. */
function renderRoutedWeek(events: readonly BookingListDocument[], onShowDay = vi.fn()) {
  const root = createRootRoute({ component: Outlet });
  const index = createRoute({
    getParentRoute: () => root,
    path: "/",
    component: () => weekGrid(events, false, onShowDay),
  });
  const details = createRoute({ getParentRoute: () => root, path: "/booking/calendar/bookings/$id" });
  const router = createRouter({
    routeTree: root.addChildren([index, details]),
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  return renderWithRealI18n(<RouterProvider router={router as never} />, i18nConfig);
}

function dayColumn(day: string) {
  return screen.getByRole("region", { name: formatDate(day, { dateStyle: "full" }) });
}

describe("CalendarTimeGrid week", () => {
  it("renders seven day columns on one hour axis, opened at the availability window", async () => {
    await renderWeek([]);

    const grid = screen.getByRole("region", { name: "Calendar grid" });
    for (const day of calendarDates("2026-08-19", "week")) {
      expect(within(grid).getByRole("region", { name: formatDate(day, { dateStyle: "full" }) })).toBeInTheDocument();
    }
    // 08:00 at 56px per hour, less the clearance that keeps its label below the sticky header.
    expect(grid.scrollTop).toBe(8 * 56 - 12);
    expect(grid.querySelectorAll("[data-week-grid-hour]")).toHaveLength(23);
  });

  it("positions overlapping bookings side by side as focusable event cards", async () => {
    await renderWeek([
      booking(1, "2026-08-17T10:00:00Z", "2026-08-17T12:00:00Z"),
      booking(2, "2026-08-17T11:00:00Z", "2026-08-17T13:00:00Z", "Electron microscope"),
    ]);

    const monday = dayColumn("2026-08-17");
    const confocal = within(monday).getByRole("button", { name: /^Show details for Confocal microscope · Ada/ });
    const electron = within(monday).getByRole("button", { name: /^Show details for Electron microscope · Ada/ });
    const confocalSlot = confocal.closest("li");
    const electronSlot = electron.closest("li");
    expect(confocalSlot).toHaveStyle({ top: "560px", height: "112px", left: "0%", width: "50%" });
    expect(electronSlot).toHaveStyle({ top: "616px", height: "112px", left: "50%", width: "50%" });
    expect(within(dayColumn("2026-08-18")).queryByRole("button")).not.toBeInTheDocument();
  });

  it("collapses a crowded overlap group into a +N slot that lists the collapsed bookings", async () => {
    const user = userEvent.setup();
    const onShowDay = vi.fn();
    await renderRoutedWeek(
      Array.from({ length: 5 }, (_, index) =>
        booking(index + 1, "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z", `Instrument ${index + 1}`),
      ),
      onShowDay,
    );

    const monday = await screen.findByRole("region", { name: formatDate("2026-08-17", { dateStyle: "full" }) });
    expect(within(monday).getAllByRole("button", { name: /^Show details for/ })).toHaveLength(1);
    const more = within(monday).getByRole("button", { name: "Show 4 more bookings on Monday, August 17, 2026" });
    expect(more).toHaveTextContent("+4 more");
    expect(more.closest("li")).toHaveStyle({ top: "560px", left: "50%", width: "50%" });

    await user.click(more);
    const list = await screen.findByRole("dialog", { name: "Monday, August 17, 2026" });
    expect(list).toHaveAccessibleDescription("4 more bookings");
    // The booking still shown in the grid is not repeated.
    expect(within(list).queryByText("Instrument 1")).not.toBeInTheDocument();
    for (const name of ["Instrument 2", "Instrument 3", "Instrument 4", "Instrument 5"]) {
      expect(within(list).getByText(name)).toBeVisible();
    }
    // The week mixes people's bookings, so an expanded booking names who booked it.
    await user.click(within(list).getByText("Instrument 2"));
    const expanded = within(list).getByText("Instrument 2").closest("details");
    if (!expanded) throw new Error("Each listed booking must be an expandable summary");
    expect(expanded).toHaveAttribute("open");
    expect(within(expanded).getAllByText("Ada Lovelace (ada)")[0]).toBeVisible();
    expect(within(expanded).getByRole("link", { name: "Details" })).toHaveAttribute(
      "href",
      "/booking/calendar/bookings/2",
    );

    await user.click(within(list).getByRole("button", { name: "Open day" }));
    expect(onShowDay).toHaveBeenCalledWith("2026-08-17");
  });

  it("keeps a short last page of collapsed bookings as tall as a full page", async () => {
    const user = userEvent.setup();
    await renderRoutedWeek(
      Array.from({ length: 8 }, (_, index) =>
        booking(index + 1, "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z", `Instrument ${index + 1}`),
      ),
    );

    await user.click(await screen.findByRole("button", { name: "Show 7 more bookings on Monday, August 17, 2026" }));
    const list = await screen.findByRole("dialog", { name: "Monday, August 17, 2026" });
    expect(within(list).getAllByRole("listitem")).toHaveLength(5);
    await user.click(within(list).getByRole("button", { name: "Next bookings" }));
    expect(within(list).getByText("6–7 of 7")).toBeVisible();
    // Two bookings, and three hidden rows that hold the height of the other three.
    expect(within(list).getAllByRole("listitem")).toHaveLength(2);
    expect(list.querySelectorAll("li[aria-hidden='true']")).toHaveLength(3);
  });

  it("clips an overnight booking into both days and marks the continuation", async () => {
    await renderWeek([booking(1, "2026-08-19T22:00:00Z", "2026-08-20T02:00:00Z")]);

    const wednesday = dayColumn("2026-08-19");
    const thursday = dayColumn("2026-08-20");
    expect(
      within(wednesday)
        .getByRole("button", { name: /^Show details for Confocal/ })
        .closest("li"),
    ).toHaveStyle({
      top: `${22 * 56}px`,
      height: `${2 * 56}px`,
    });
    expect(within(wednesday).getByRole("img", { name: "Continues into the next day" })).toBeInTheDocument();
    expect(
      within(thursday)
        .getByRole("button", { name: /^Show details for Confocal/ })
        .closest("li"),
    ).toHaveStyle({
      top: "0px",
      height: `${2 * 56}px`,
    });
    expect(within(thursday).getByRole("img", { name: "Continues from the previous day" })).toBeInTheDocument();
  });

  it("hides the grid from assistive technology while events load", async () => {
    await renderWeek([], true);
    expect(screen.queryByRole("region", { name: "Calendar grid" })).not.toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Time grid" })).toHaveAttribute("aria-busy", "true");
  });
});
