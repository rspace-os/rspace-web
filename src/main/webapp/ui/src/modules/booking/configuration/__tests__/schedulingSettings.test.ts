import { describe, expect, it } from "vitest";
import {
  DEFAULT_SCHEDULING_SETTINGS,
  incrementProblem,
  type SchedulingSettings,
} from "@/modules/booking/configuration/schedulingSettings";

const settings = (overrides: Partial<SchedulingSettings>): SchedulingSettings => ({
  ...DEFAULT_SCHEDULING_SETTINGS,
  ...overrides,
});
const allDayExcept = (dayOfWeek: number, start: string, end: string) =>
  settings({
    openingHours: [1, 2, 3, 4, 5, 6, 7]
      .map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))
      .map((entry) => [{ dayOfWeek, start, end }].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry),
  });

describe("incrementProblem", () => {
  it.each([
    ["0 minutes", settings({ slotGranularityMinutes: 0 }), "range"],
    ["more than the absolute cap", settings({ slotGranularityMinutes: 527_041 }), "range"],
    ["1.5 minutes", settings({ slotGranularityMinutes: 1.5 }), "wholeMinutes"],
    ["1.5 days", settings({ slotGranularityMinutes: 2160, incrementUnit: "DAYS" }), "wholeDays"],
    ["0.35 days", settings({ slotGranularityMinutes: 504, incrementUnit: "DAYS" }), "wholeDays"],
    [
      "1 day with a closed weekday",
      settings({
        incrementUnit: "DAYS",
        openingHours: [1, 2, 3, 4, 5, 6, 7]
          .map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))
          .filter((entry) => [1, 2, 3, 4, 5, 6].some((dayOfWeek) => dayOfWeek === entry.dayOfWeek)),
        slotGranularityMinutes: 1440,
      }),
      "daysNeedFullWeek",
    ],
    [
      "1 day with partial hours",
      { ...allDayExcept(3, "08:00", "18:00"), slotGranularityMinutes: 1440, incrementUnit: "DAYS" as const },
      "daysNeedFullWeek",
    ],
    [
      "aligned 60 minutes in 09:15-10:15",
      settings({
        slotGranularityMinutes: 60,
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:15", end: "10:15" })),
      }),
      "alignedOpening",
    ],
    [
      "90 minutes in 09:00-10:00",
      settings({
        slotGranularityMinutes: 90,
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "10:00" })),
        alignToIncrement: false,
      }),
      "opening",
    ],
  ] as const)("reports %s", (_, value, problem) => {
    expect(incrementProblem(value)).toBe(problem);
  });

  it.each([
    [
      "unaligned 60 minutes in 09:15-10:15",
      settings({
        slotGranularityMinutes: 60,
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:15", end: "10:15" })),
        alignToIncrement: false,
      }),
    ],
    [
      "aligned 60 minutes in 09:00-10:00",
      settings({
        slotGranularityMinutes: 60,
        openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "10:00" })),
      }),
    ],
    [
      "1 day open all week with a redundant full-day exception",
      { ...allDayExcept(3, "00:00", "24:00"), slotGranularityMinutes: 1440, incrementUnit: "DAYS" as const },
    ],
    ["24 hours open all week", settings({ slotGranularityMinutes: 1440, incrementUnit: "HOURS" })],
    // 20:00 to midnight joins the next day's 00:00 to 02:00, so the 20:00 grid start fits 5 hours.
    [
      "5 aligned hours across midnight",
      settings({
        slotGranularityMinutes: 300,
        incrementUnit: "HOURS",
        openingHours: [1, 2, 3, 4, 5, 6, 7]
          .map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))
          .filter((entry) => [1, 2].some((dayOfWeek) => dayOfWeek === entry.dayOfWeek))
          .map(
            (entry) =>
              [
                { dayOfWeek: 1, start: "20:00", end: "24:00" },
                { dayOfWeek: 2, start: "00:00", end: "02:00" },
              ].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry,
          ),
      }),
    ],
    // Sunday evening joins Monday morning across the week wrap.
    [
      "6 unaligned hours across the week wrap",
      settings({
        slotGranularityMinutes: 360,
        incrementUnit: "HOURS",
        alignToIncrement: false,
        openingHours: [1, 2, 3, 4, 5, 6, 7]
          .map((dayOfWeek) => ({ dayOfWeek, start: "00:00", end: "24:00" }))
          .filter((entry) => [7, 1].some((dayOfWeek) => dayOfWeek === entry.dayOfWeek))
          .map(
            (entry) =>
              [
                { dayOfWeek: 7, start: "21:00", end: "24:00" },
                { dayOfWeek: 1, start: "00:00", end: "03:00" },
              ].find((day) => day.dayOfWeek === entry.dayOfWeek) ?? entry,
          ),
      }),
    ],
  ])("accepts %s", (_, value) => {
    expect(incrementProblem(value)).toBeNull();
  });

  it("leaves a malformed schedule to the opening-hours fields", () => {
    expect(
      incrementProblem(
        settings({
          slotGranularityMinutes: 60,
          openingHours: [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => ({ dayOfWeek, start: "16:00", end: "10:00" })),
        }),
      ),
    ).toBeNull();
  });
});
