import { describe, expect, it } from "vitest";
import type { BookingListDocument } from "@/modules/booking/domain/booking";
import {
  layoutWeekGridDay,
  WEEK_GRID_MINIMUM_VISUAL_MINUTES,
  wallClockMinute,
  weekGridEventBox,
} from "../calendarWeekLayout";

function booking(id: number, start: string, end: string): BookingListDocument {
  return {
    id,
    target: {
      relationTo: "booking-instruments",
      globalId: "IN123",
      value: {
        id: 123,
        name: "Confocal microscope",
        deleted: false,
        parentContainerName: null,
        parentContainerGlobalId: null,
      },
    },
    canViewConfiguration: true,
    requesterId: 1,
    timezone: "Europe/Berlin",
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

function layoutSummary(events: readonly BookingListDocument[], day: string, timezone = "UTC") {
  return layoutWeekGridDay(events, day, timezone).map(
    ({ event, startMinute, endMinute, lane, laneCount, continuesBefore, continuesAfter }) => ({
      id: event.id,
      startMinute,
      endMinute,
      lane,
      laneCount,
      continuesBefore,
      continuesAfter,
    }),
  );
}

describe("layoutWeekGridDay", () => {
  it("positions and sizes a booking by its start and end on the hour axis", () => {
    const [item] = layoutWeekGridDay([booking(1, "2026-08-17T10:00:00Z", "2026-08-17T12:00:00Z")], "2026-08-17", "UTC");

    expect(item).toMatchObject({ startMinute: 600, endMinute: 720, lane: 0, laneCount: 1 });
    // 56px per hour.
    expect(weekGridEventBox(item)).toEqual({ top: 560, height: 112, left: "0%", width: "100%" });
  });

  it("lays overlapping bookings out side by side and keeps separate groups full width", () => {
    const events = [
      booking(3, "2026-08-17T12:00:00Z", "2026-08-17T13:00:00Z"),
      booking(1, "2026-08-17T10:00:00Z", "2026-08-17T12:00:00Z"),
      booking(2, "2026-08-17T11:00:00Z", "2026-08-17T13:00:00Z"),
      booking(4, "2026-08-17T15:00:00Z", "2026-08-17T16:00:00Z"),
    ];

    const items = layoutWeekGridDay(events, "2026-08-17", "UTC");

    expect(items.map(({ event, lane, laneCount }) => [event.id, lane, laneCount])).toEqual([
      ["1", 0, 2],
      ["2", 1, 2],
      ["3", 0, 2],
      ["4", 0, 1],
    ]);
    expect(weekGridEventBox(items[1])).toMatchObject({ left: "50%", width: "50%" });
  });

  it("clips an overnight booking to each day it touches", () => {
    const overnight = booking(1, "2026-08-17T22:00:00Z", "2026-08-18T02:00:00Z");

    expect(layoutSummary([overnight], "2026-08-17")).toEqual([
      {
        id: "1",
        startMinute: 1320,
        endMinute: 1440,
        lane: 0,
        laneCount: 1,
        continuesBefore: false,
        continuesAfter: true,
      },
    ]);
    expect(layoutSummary([overnight], "2026-08-18")).toEqual([
      { id: "1", startMinute: 0, endMinute: 120, lane: 0, laneCount: 1, continuesBefore: true, continuesAfter: false },
    ]);
    expect(layoutSummary([overnight], "2026-08-19")).toEqual([]);
  });

  it("clips a multi-day booking to the whole of a middle day", () => {
    expect(
      layoutSummary([booking(1, "2026-08-17T10:00:00Z", "2026-08-19T10:00:00Z")], "2026-08-18").map(
        ({ startMinute, endMinute, continuesBefore, continuesAfter }) => ({
          startMinute,
          endMinute,
          continuesBefore,
          continuesAfter,
        }),
      ),
    ).toEqual([{ startMinute: 0, endMinute: 1440, continuesBefore: true, continuesAfter: true }]);
  });

  it("gives a short booking a readable extent and moves the next booking out of its way", () => {
    const items = layoutSummary(
      [
        booking(1, "2026-08-17T10:00:00Z", "2026-08-17T10:10:00Z"),
        booking(2, "2026-08-17T10:15:00Z", "2026-08-17T11:00:00Z"),
      ],
      "2026-08-17",
    );

    expect(items[0]).toMatchObject({ startMinute: 600, endMinute: 600 + WEEK_GRID_MINIMUM_VISUAL_MINUTES, lane: 0 });
    expect(items[1]).toMatchObject({ startMinute: 615, endMinute: 660, lane: 1, laneCount: 2 });
  });

  it("places bookings by wall-clock time on a DST change while the card keeps elapsed minutes", () => {
    // Europe/Berlin falls back from 03:00 CEST to 02:00 CET on 2026-10-25, so 10:00 CET is 660 elapsed minutes.
    const [item] = layoutWeekGridDay(
      [booking(1, "2026-10-25T09:00:00Z", "2026-10-25T10:00:00Z")],
      "2026-10-25",
      "Europe/Berlin",
    );

    expect(item).toMatchObject({ startMinute: 600, endMinute: 660 });
    expect(item.event).toMatchObject({ startMinute: 660, endMinute: 720 });
  });

  it("omits bookings outside the displayed day", () => {
    expect(layoutSummary([booking(1, "2026-08-18T10:00:00Z", "2026-08-18T11:00:00Z")], "2026-08-17")).toEqual([]);
  });
});

describe("wallClockMinute", () => {
  it("counts wall-clock minutes from the day's midnight across a skipped hour", () => {
    // Europe/Berlin springs forward from 02:00 CET to 03:00 CEST on 2026-03-29.
    expect(wallClockMinute("2026-03-29T01:00:00Z", "2026-03-29", "Europe/Berlin")).toBe(180);
    expect(wallClockMinute("2026-03-28T23:00:00Z", "2026-03-29", "Europe/Berlin")).toBe(0);
    expect(wallClockMinute("2026-03-28T22:00:00Z", "2026-03-29", "Europe/Berlin")).toBe(-60);
  });
});
