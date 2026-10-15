import { describe, expect, it } from "vitest";
import { validMaximumBookingDuration } from "@/modules/booking/configuration/schedulingSettings";

describe("validMaximumBookingDuration", () => {
  it("accepts an unlimited duration or a maximum aligned to its increment", () => {
    expect(validMaximumBookingDuration(0, 5)).toBe(true);
    expect(validMaximumBookingDuration(60, 5)).toBe(true);
    expect(validMaximumBookingDuration(7, 5)).toBe(false);
  });
});
