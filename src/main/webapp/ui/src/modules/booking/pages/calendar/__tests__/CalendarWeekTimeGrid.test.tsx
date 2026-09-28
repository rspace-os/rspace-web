import "@/__tests__/__mocks__/matchMedia";
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

function renderWeek(events: readonly BookingListDocument[], isLoading = false, onShowDay = vi.fn()) {
  return renderWithRealI18n(
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
    />,
    i18nConfig,
  );
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

  it("collapses a crowded overlap group into a +N slot that opens the day", async () => {
    const onShowDay = vi.fn();
    await renderWeek(
      Array.from({ length: 5 }, (_, index) =>
        booking(index + 1, "2026-08-17T10:00:00Z", "2026-08-17T11:00:00Z", `Instrument ${index + 1}`),
      ),
      false,
      onShowDay,
    );

    const monday = dayColumn("2026-08-17");
    expect(within(monday).getAllByRole("button", { name: /^Show details for/ })).toHaveLength(1);
    const more = within(monday).getByRole("button", { name: "Show 4 more bookings on Monday, August 17, 2026" });
    expect(more).toHaveTextContent("+4 more");
    expect(more.closest("li")).toHaveStyle({ top: "560px", left: "50%", width: "50%" });
    await userEvent.click(more);
    expect(onShowDay).toHaveBeenCalledWith("2026-08-17");
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
