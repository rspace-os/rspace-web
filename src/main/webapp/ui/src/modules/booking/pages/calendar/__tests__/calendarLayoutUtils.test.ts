import { describe, expect, it } from "vitest";
import { toTimelineEvent } from "@/modules/booking/components/toTimelineEvent";
import { ownBooking } from "../../mocks/bookingCalendarFixtures";
import { monthDayCount, shiftDate, startOfWeek } from "../calendarLayoutUtils";

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
