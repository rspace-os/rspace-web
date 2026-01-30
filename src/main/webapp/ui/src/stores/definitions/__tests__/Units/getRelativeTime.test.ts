import { describe, expect, it, vi } from "vitest";
import { getRelativeTime } from "../../Units";
import fc from "fast-check";

describe("getRelativeTime", () => {
  it("Simple example", () => {
    const now = new Date();
    const futureDate = new Date();
    futureDate.setHours(now.getHours() + 2);
    vi.useFakeTimers();
    vi.setSystemTime(now);
    expect(getRelativeTime(futureDate)).toBe("in 2 hours");
    vi.useRealTimers();
  });

  // this is important so that callers get the grammar right
  it("Always starts with 'in'", () => {
    const now = new Date();
    vi.useFakeTimers();
    vi.setSystemTime(now);
    const futureDate = fc.date().filter((d) => d > new Date());
    fc.assert(
      fc.property(futureDate, (date) => {
        expect(getRelativeTime(date)).toMatch(/^in /);
      })
    );
    vi.useRealTimers();
  });
});

