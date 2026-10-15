import * as v from "valibot";
import { describe, expect, it } from "vitest";
import {
  DEFAULT_SCHEDULING_SETTINGS,
  incrementProblem,
  type SchedulingSettings,
  SchedulingSettingsSchema,
} from "@/modules/booking/configuration/schedulingSettings";
import { ALL_ISO_WEEKDAYS, validOpeningHours } from "@/modules/booking/domain/bookingOpeningHours";

const settings = (overrides: Partial<SchedulingSettings>): SchedulingSettings => ({
  ...DEFAULT_SCHEDULING_SETTINGS,
  ...overrides,
});
const allDayExcept = (dayOfWeek: number, start: string, end: string) =>
  settings({
    openingHours: ALL_ISO_WEEKDAYS.map((day) =>
      day === dayOfWeek ? { dayOfWeek: day, start, end } : { dayOfWeek: day, start: "00:00", end: "24:00" },
    ),
  });

describe("incrementProblem", () => {
  it.each([
    ["0 minutes", settings({ slotGranularityMinutes: 0 }), "range"],
    ["more than the absolute cap", settings({ slotGranularityMinutes: 527_041 }), "range"],
    ["1.5 minutes", settings({ slotGranularityMinutes: 1.5 }), "wholeMinutes"],
    ["1.5 days", settings({ slotGranularityMinutes: 2160, incrementUnit: "DAYS" }), "wholeDays"],
    [
      "1 day with a closed weekday",
      settings({
        incrementUnit: "DAYS",
        openingHours: [
          { dayOfWeek: 1, start: "00:00", end: "24:00" },
          { dayOfWeek: 2, start: "00:00", end: "24:00" },
          { dayOfWeek: 3, start: "00:00", end: "24:00" },
          { dayOfWeek: 4, start: "00:00", end: "24:00" },
          { dayOfWeek: 5, start: "00:00", end: "24:00" },
          { dayOfWeek: 6, start: "00:00", end: "24:00" },
        ],
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
        openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "09:15", end: "10:15" })),
      }),
      "alignedOpening",
    ],
    [
      "90 minutes in 09:00-10:00",
      settings({
        slotGranularityMinutes: 90,
        openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "10:00" })),
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
        openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "09:15", end: "10:15" })),
        alignToIncrement: false,
      }),
    ],
    [
      "aligned 60 minutes in 09:00-10:00",
      settings({
        slotGranularityMinutes: 60,
        openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "10:00" })),
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
        openingHours: [
          { dayOfWeek: 1, start: "20:00", end: "24:00" },
          { dayOfWeek: 2, start: "00:00", end: "02:00" },
        ],
      }),
    ],
    // Sunday evening joins Monday morning across the week wrap.
    [
      "6 unaligned hours across the week wrap",
      settings({
        slotGranularityMinutes: 360,
        incrementUnit: "HOURS",
        alignToIncrement: false,
        openingHours: [
          { dayOfWeek: 7, start: "21:00", end: "24:00" },
          { dayOfWeek: 1, start: "00:00", end: "03:00" },
        ],
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
          openingHours: ALL_ISO_WEEKDAYS.map((dayOfWeek) => ({ dayOfWeek, start: "16:00", end: "10:00" })),
        }),
      ),
    ).toBeNull();
  });
});

describe("SchedulingSettingsSchema", () => {
  it("rejects 24:00 as an opening time", () => {
    expect(validOpeningHours("24:00", "24:00")).toBe(false);
  });

  it.each([
    ["no open days", [], "openingHours"],
    ["duplicate weekdays", [1, 1].map((dayOfWeek) => ({ dayOfWeek, start: "09:00", end: "17:00" })), "openingHours"],
    ["an out-of-range weekday", [{ dayOfWeek: 8, start: "09:00", end: "17:00" }], "openingHours.0.dayOfWeek"],
    ["a reversed interval", [{ dayOfWeek: 2, start: "16:00", end: "10:00" }], "openingHours.0.end"],
  ])("rejects scheduling settings with %s", (_, openingHours, path) => {
    const result = v.safeParse(SchedulingSettingsSchema, { ...DEFAULT_SCHEDULING_SETTINGS, openingHours });
    expect(result.success).toBe(false);
    expect(result.issues?.map((issue) => v.getDotPath(issue))).toEqual([path]);
  });

  it("accepts 24:00 in an exception after a non-midnight start", () => {
    const result = v.safeParse(SchedulingSettingsSchema, {
      ...DEFAULT_SCHEDULING_SETTINGS,
      openingHours: DEFAULT_SCHEDULING_SETTINGS.openingHours.map((entry) =>
        entry.dayOfWeek === 2 ? { ...entry, start: "08:00", end: "24:00" } : entry,
      ),
    });
    expect(result.success).toBe(true);
  });

  it("submits only the supplied open weekdays", () => {
    const output = v.parse(SchedulingSettingsSchema, {
      ...DEFAULT_SCHEDULING_SETTINGS,
      openingHours: [{ dayOfWeek: 1, start: "00:00", end: "24:00" }],
    });
    expect(output.openingHours).toEqual([{ dayOfWeek: 1, start: "00:00", end: "24:00" }]);
  });
});
