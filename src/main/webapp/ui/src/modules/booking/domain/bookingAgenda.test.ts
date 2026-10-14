import { describe, expect, it } from "vitest";
import type { BookingListDocument } from "./booking";
import {
  formatBookingAgendaDate,
  formatBookingAgendaDateTime,
  formatBookingAgendaTimeRange,
  getBookingAgendaRelativeDay,
  groupBookingsByStartDate,
} from "./bookingAgenda";

function booking(id: number, start: string, end: string): BookingListDocument {
  return {
    id,
    version: 1,
    target: null,
    timezone: "Europe/Berlin",
    start,
    end,
    state: "CONFIRMED",
    kind: "BOOKING",
    privacy: "full",
    purpose: null,
    bookedBy: null,
    createdBy: null,
    canViewConfiguration: false,
    requesterId: 7,
    canEdit: false,
    canCancel: false,
    createdAt: start,
    updatedAt: start,
  };
}

describe("booking agenda date helpers", () => {
  it("returns no date groups for an empty agenda", () => {
    expect(groupBookingsByStartDate([], "Europe/Berlin")).toEqual([]);
  });

  it("sorts a copy by instant and ID, then groups each booking by its start date", () => {
    const rows = [
      booking(12, "2026-09-25T09:00:00.000Z", "2026-09-25T10:00:00.000Z"),
      booking(4, "2026-09-24T08:00:00.000Z", "2026-09-24T09:00:00.000Z"),
      booking(3, "2026-09-24T08:00:00.000Z", "2026-09-24T09:00:00.000Z"),
      booking(8, "2026-09-24T23:30:00.000Z", "2026-09-25T01:30:00.000Z"),
    ];

    const groups = groupBookingsByStartDate(rows, "UTC");

    expect(groups.map(({ date, bookings: day }) => [date, day.map(({ id }) => id)])).toEqual([
      ["2026-09-24", [3, 4, 8]],
      ["2026-09-25", [12]],
    ]);
    expect(rows.map(({ id }) => id)).toEqual([12, 4, 3, 8]);
    expect(groups.flatMap(({ bookings: day }) => day)).toHaveLength(rows.length);
  });

  it("groups in the selected timezone and labels calendar days across the spring DST change", () => {
    const rows = [booking(9, "2026-03-28T23:30:00.000Z", "2026-03-29T01:30:00.000Z")];
    const groups = groupBookingsByStartDate(rows, "Europe/Berlin");
    const now = Date.parse("2026-03-28T23:30:00.000Z");

    expect(groups.map(({ date }) => date)).toEqual(["2026-03-29"]);
    expect(getBookingAgendaRelativeDay("2026-03-29", now, "Europe/Berlin")).toBe("today");
    expect(getBookingAgendaRelativeDay("2026-03-30", now, "Europe/Berlin")).toBe("tomorrow");
    expect(getBookingAgendaRelativeDay("2026-03-31", now, "Europe/Berlin")).toBeNull();

    const springRange = formatBookingAgendaTimeRange(
      "2026-03-29T00:30:00.000Z",
      "2026-03-29T01:30:00.000Z",
      "Europe/Berlin",
      "en-US",
    );
    expect(springRange).toContain("GMT+1");
    expect(springRange).toContain("GMT+2");
  });

  it("formats overnight and repeated-hour ranges with enough date and offset detail", () => {
    const overnight = formatBookingAgendaTimeRange(
      "2026-09-25T20:00:00.000Z",
      "2026-09-26T00:00:00.000Z",
      "Europe/Berlin",
      "en-US",
    );
    const repeatedHour = formatBookingAgendaTimeRange(
      "2026-10-25T00:30:00.000Z",
      "2026-10-25T01:30:00.000Z",
      "Europe/Berlin",
      "en-US",
    );

    expect(overnight).toContain("Sep 26");
    expect(repeatedHour).toContain("GMT+2");
    expect(repeatedHour).toContain("GMT+1");
    expect(
      formatBookingAgendaTimeRange("2026-10-25T00:05:00.000Z", "2026-10-25T00:35:00.000Z", "Europe/Berlin", "en-US"),
    ).toMatch(/GMT\+2.*GMT\+2/);
    // The 12- or 24-hour clock follows the browser region (en-US here), not the app language, to match native inputs.
    expect(
      formatBookingAgendaTimeRange("2026-09-24T12:00:00.000Z", "2026-09-24T14:00:00.000Z", "Europe/Berlin", "de-DE"),
    ).toEqual(
      formatBookingAgendaTimeRange("2026-09-24T12:00:00.000Z", "2026-09-24T14:00:00.000Z", "Europe/Berlin", "en-US"),
    );
    expect(
      formatBookingAgendaDateTime(
        "2026-10-25T00:30:00.000Z",
        "2026-10-25T00:30:00.000Z",
        "2026-10-25T01:30:00.000Z",
        "Europe/Berlin",
        "en-US",
      ),
    ).toContain("GMT+2");
    expect(
      formatBookingAgendaDateTime(
        "2026-10-25T01:30:00.000Z",
        "2026-10-25T00:30:00.000Z",
        "2026-10-25T01:30:00.000Z",
        "Europe/Berlin",
        "en-US",
      ),
    ).toContain("GMT+1");
  });

  it("includes the year in headings when the agenda date is outside the current year", () => {
    expect(formatBookingAgendaDate("2027-01-01", "en-US", 2026)).toContain("2027");
  });

  it("finds tomorrow by calendar date across a year boundary", () => {
    const now = Date.parse("2026-12-31T23:30:00.000Z");
    expect(getBookingAgendaRelativeDay("2027-01-01", now, "Europe/Berlin")).toBe("today");
    expect(getBookingAgendaRelativeDay("2027-01-02", now, "Europe/Berlin")).toBe("tomorrow");
  });
});
