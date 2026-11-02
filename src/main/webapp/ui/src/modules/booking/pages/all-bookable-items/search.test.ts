import { describe, expect, it } from "vitest";
import { allBookableItemsSearch } from "./search";

describe("All Bookable Items search", () => {
  it.each(["available-now", "free-later-today"])('keeps the valid availability mode "%s"', (availability) => {
    expect(allBookableItemsSearch({ date: "2026-08-17", availability })).toEqual({
      date: "2026-08-17",
      availability,
    });
  });

  it.each([["invalid"], [[]]])("drops invalid date %s while keeping unrelated filters", (date) => {
    const search = allBookableItemsSearch({ date, target: "IN42", availability: "available-now" });
    expect(search).toMatchObject({ target: "IN42", availability: "available-now" });
    expect(search).toHaveProperty("date", undefined);
  });

  it("drops absent and invalid availability modes", () => {
    expect(allBookableItemsSearch({ date: "2026-08-17" })).toEqual({ date: "2026-08-17" });
    expect(allBookableItemsSearch({ date: "2026-08-17", availability: "tomorrow" })).toEqual({
      date: "2026-08-17",
    });
  });

  it("drops the removed location filter from the URL", () => {
    expect(allBookableItemsSearch({ date: "2026-08-17", locations: ["IC456"] })).toEqual({
      date: "2026-08-17",
    });
  });
});
