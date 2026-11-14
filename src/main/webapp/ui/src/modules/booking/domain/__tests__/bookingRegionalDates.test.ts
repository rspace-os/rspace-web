import { expect, it, vi } from "vitest";
import { formatBookingDateTime } from "../bookingTime";

it("uses the browser region for word dates even when the app language is US English", () => {
  const original = Intl.DateTimeFormat.prototype.resolvedOptions;
  const region = vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions").mockImplementation(function (
    this: Intl.DateTimeFormat,
  ) {
    return { ...original.call(this), locale: "en-GB" };
  });
  try {
    expect(formatBookingDateTime("2026-11-02T13:00:00Z", "Europe/London", undefined, "H24")).toBe("2 Nov 2026, 13:00");
  } finally {
    region.mockRestore();
  }
});
