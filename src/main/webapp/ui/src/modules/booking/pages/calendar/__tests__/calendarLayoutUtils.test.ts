import { describe, expect, it } from "vitest";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { ownBooking } from "../../mocks/bookingCalendarFixtures";
import { monthDayCount, nextFreeRange, shiftDate, startOfWeek } from "../calendarLayoutUtils";

describe("calendar date layout", () => {
  it("keeps a retained booking while omitting its redacted target", () => {
    const event = toTimelineEvent(
      { ...ownBooking, target: null, timezone: null, canEdit: false, canViewConfiguration: false },
      "2026-08-17",
      "UTC",
    );

    expect(event).toMatchObject({
      kind: "booking",
      privacy: "full",
      title: "common:values.unknownItem",
      item: { name: "common:values.unknownItem", globalId: null },
      canEdit: false,
    });
  });

  it("starts weeks on Monday using the ISO calendar date", () => {
    expect(startOfWeek("2026-03-29")).toBe("2026-03-23");
    expect(startOfWeek("2026-03-30")).toBe("2026-03-30");
  });

  it("uses the number of days in the ISO month", () => {
    expect(monthDayCount("2024-02-15")).toBe(29);
    expect(monthDayCount("2025-02-15")).toBe(28);
  });

  it("shifts months from the first day across year boundaries", () => {
    expect(shiftDate("2026-03-29", "month", -1)).toBe("2026-02-01");
    expect(shiftDate("2026-01-31", "month", -1)).toBe("2025-12-01");
  });
});

describe("nextFreeRange", () => {
  it("skips unavailable ranges such as closed hours", () => {
    const closed = [
      { startMinute: 0, endMinute: 10 * 60 },
      { startMinute: 16 * 60, endMinute: 24 * 60 },
    ];
    expect(nextFreeRange([], "2026-10-05", "Europe/Berlin", 9 * 60, 19 * 60, 15, closed)).toEqual({
      startMinute: 10 * 60,
      endMinute: 11 * 60,
    });
    expect(nextFreeRange([], "2026-10-05", "Europe/Berlin", 17 * 60, 19 * 60, 15, closed)).toBeUndefined();
  });
});
