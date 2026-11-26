import { Temporal } from "@js-temporal/polyfill";
import { describe, expect, it } from "vitest";
import { epochMillisecondsOf, instantToWire, parseInstant, truncateToMilliseconds } from "../instant";

describe("parseInstant", () => {
  it("keeps every fractional digit", () => {
    expect(String(parseInstant("2026-08-01T12:00:00.123456+02:00"))).toBe("2026-08-01T10:00:00.123456Z");
  });

  it.each(["", "yesterday", "2026-08-01", "2026-08-01T10:00", "2026-02-30T10:00:00Z"])(
    "returns null for %j",
    (value) => {
      expect(parseInstant(value)).toBeNull();
    },
  );
});

it("prints the wire format with exactly three fractional digits", () => {
  expect(instantToWire(Temporal.Instant.from("2026-08-01T10:00:00.123456Z"))).toBe("2026-08-01T10:00:00.123Z");
  expect(instantToWire(Temporal.Instant.from("2026-08-01T10:00:00Z"))).toBe("2026-08-01T10:00:00.000Z");
  expect(String(truncateToMilliseconds(Temporal.Instant.from("2026-08-01T10:00:00.123999Z")))).toBe(
    "2026-08-01T10:00:00.123Z",
  );
});

it("gives NaN epoch milliseconds for text that is not an instant", () => {
  expect(epochMillisecondsOf("2026-08-01T10:00:00.001Z")).toBe(1785578400001);
  expect(epochMillisecondsOf("2026-08-01")).toBeNaN();
});

it("compares Temporal values by value in toEqual", () => {
  expect(Temporal.Instant.from("2026-01-01T00:00:00Z")).not.toEqual(Temporal.Instant.from("2026-01-02T00:00:00Z"));
  expect({ at: Temporal.Instant.from("2026-01-01T00:00:00Z") }).toEqual({
    at: Temporal.Instant.from("2026-01-01T00:00:00.000Z"),
  });
  expect(Temporal.PlainDate.from("2026-01-01")).not.toEqual(Temporal.Instant.from("2026-01-01T00:00:00Z"));
});
