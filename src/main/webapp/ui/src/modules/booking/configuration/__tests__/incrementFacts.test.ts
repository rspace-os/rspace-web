import { describe, expect, it } from "vitest";
import { createTestI18n } from "@/__tests__/helpers/createTestI18n";
import booking from "@/modules/common/i18n/locales/en-US/booking.json";
import { formatBookingLength } from "../incrementFacts";

const i18n = await createTestI18n({ booking }, "booking");
const t = i18n.getFixedT("en-US", "booking");

describe("formatBookingLength", () => {
  it.each([
    ["MINUTES", "2026-08-17T09:00Z", "2026-08-17T10:40Z", "100 minutes"],
    ["HOURS", "2026-08-17T09:00Z", "2026-08-17T13:00Z", "4 hours"],
    ["DAYS", "2026-08-17T09:00Z", "2026-08-19T13:00Z", "2 days, 4 hours"],
    ["DAYS", "2026-03-28T11:00Z", "2026-03-29T10:00Z", "1 day"],
    ["HOURS", "2026-03-28T11:00Z", "2026-03-29T10:00Z", "23 hours"],
  ] as const)("formats %s from %s to %s", (unit, start, end, expected) => {
    expect(formatBookingLength(unit, { start, end }, "Europe/Berlin", t, "en-US")).toBe(expected);
  });
});
