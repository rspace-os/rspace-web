import { describe, expect, it } from "vitest";
import { bookingFormSearch } from "./bookingFormSearch";

describe("booking form search", () => {
  it("keeps valid Calendar return state", () => {
    expect(bookingFormSearch({ date: "2026-10-25", target: "IN42" })).toEqual({
      date: "2026-10-25",
      target: "IN42",
    });
  });

  it("drops invalid dates and targets", () => {
    expect(bookingFormSearch({ date: "2026-02-30", target: "SA42" })).toEqual({ date: undefined });
  });

  it("drops invalid dates while keeping a valid target", () => {
    expect(bookingFormSearch({ date: "2026-02-30", target: "IN42" })).toEqual({ date: undefined, target: "IN42" });
  });
});
