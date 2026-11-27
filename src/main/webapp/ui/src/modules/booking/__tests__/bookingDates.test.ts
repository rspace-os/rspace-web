import { Temporal } from "@js-temporal/polyfill";
import { expect, it } from "vitest";
import { pickerDate, plainDateFromPicker } from "../bookingDates";

it("reads a picker day in the picker's zone when the browser is already on another day", () => {
  // Midnight on 29 August in Kiritimati (UTC+14) is still 28 August in UTC and in every zone west of it.
  const day = pickerDate(Temporal.PlainDate.from("2026-08-29"), "Pacific/Kiritimati");
  expect(day.getTime()).toBe(Temporal.Instant.from("2026-08-28T10:00:00Z").epochMilliseconds);
  expect(String(plainDateFromPicker(day, "Pacific/Kiritimati"))).toBe("2026-08-29");
  expect(String(plainDateFromPicker(new Date("2026-08-28T22:00:00Z"), "Pacific/Kiritimati"))).toBe("2026-08-29");
});
