import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  type IncrementSettings,
  type IncrementUnit,
  incrementFitsSchedule,
  incrementRule,
  incrementViolation,
  snapEndToIncrement,
  validIncrement,
  validMaximumForIncrement,
} from "../bookingIncrement";
import type { OpeningHoursEntry } from "../bookingOpeningHours";

type IntervalVector = IncrementSettings & {
  name: string;
  timezone: string;
  start: string;
  end: string;
  expected: "valid" | "precision" | "duration" | "dayEndMissing";
};

type SettingsVector = IncrementSettings & {
  name: string;
  openingHours: OpeningHoursEntry[];
  maxBookingDurationMinutes: number;
  expected: "valid" | "granularity" | "maximumDuration";
};

// Shared with the JUnit suite so both stacks implement one contract.
const scenarios = JSON.parse(
  readFileSync(resolve(process.cwd(), "../../../test/resources/booking/incrementScenarios.json"), "utf8"),
) as { intervals: IntervalVector[]; settings: SettingsVector[] };

describe("shared increment vectors", () => {
  it.each(scenarios.intervals.map((vector) => [vector.name, vector] as const))("interval: %s", (_, vector) => {
    const violation = incrementViolation(incrementRule(vector), vector.start, vector.end, vector.timezone);
    expect(violation ?? "valid").toBe(vector.expected);
  });

  it.each(scenarios.settings.map((vector) => [vector.name, vector] as const))("settings: %s", (_, vector) => {
    const result = !validIncrement(vector)
      ? "granularity"
      : !incrementFitsSchedule(vector)
        ? "granularity"
        : !validMaximumForIncrement(vector.maxBookingDurationMinutes, vector)
          ? "maximumDuration"
          : "valid";
    expect(result).toBe(vector.expected);
  });
});

describe("end snapping", () => {
  const settings = (slotGranularityMinutes: number, incrementUnit: IncrementUnit) =>
    incrementRule({ slotGranularityMinutes, incrementUnit });

  it("chooses the nearest positive elapsed multiple, earlier on ties", () => {
    const rule = settings(15, "MINUTES");
    expect(snapEndToIncrement(rule, "2026-08-17T09:07:00Z", "2026-08-17T09:30:00Z", "UTC")).toEqual({
      kind: "instant",
      instant: "2026-08-17T09:37:00Z",
    });
    expect(snapEndToIncrement(rule, "2026-08-17T09:07:00Z", "2026-08-17T09:29:30Z", "UTC")).toEqual({
      kind: "instant",
      instant: "2026-08-17T09:22:00Z",
    });
    expect(snapEndToIncrement(rule, "2026-08-17T09:07:00Z", "2026-08-17T09:00:00Z", "UTC")).toEqual({
      kind: "instant",
      instant: "2026-08-17T09:22:00Z",
    });
  });

  it("keeps the start's local time for Days and picks the nearest civil date", () => {
    const rule = settings(2880, "DAYS");
    // Monday noon start: Thursday 13:00 is 23 civil hours from Friday noon and 25 from Wednesday noon.
    expect(snapEndToIncrement(rule, "2026-03-30T10:00:00Z", "2026-04-02T11:00:00Z", "Europe/Berlin")).toEqual({
      kind: "instant",
      instant: "2026-04-03T10:00:00Z",
    });
    // Thursday noon is equidistant, so the earlier target wins.
    expect(snapEndToIncrement(rule, "2026-03-30T10:00:00Z", "2026-04-02T10:00:00Z", "Europe/Berlin")).toEqual({
      kind: "instant",
      instant: "2026-04-01T10:00:00Z",
    });
  });

  it("reports a Day end in the spring gap instead of skipping to another date", () => {
    expect(
      snapEndToIncrement(settings(1440, "DAYS"), "2026-03-28T01:30:00Z", "2026-03-29T05:00:00Z", "Europe/Berlin"),
    ).toEqual({ kind: "dayEndMissing", date: "2026-03-29", time: "02:30" });
  });

  it("asks for an occurrence when the Day end repeats", () => {
    expect(
      snapEndToIncrement(settings(1440, "DAYS"), "2026-10-24T00:30:00Z", "2026-10-25T03:00:00Z", "Europe/Berlin"),
    ).toEqual({
      kind: "ambiguous",
      date: "2026-10-25",
      time: "02:30",
      earlier: "2026-10-25T00:30:00Z",
      later: "2026-10-25T01:30:00Z",
    });
  });
});
