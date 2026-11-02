import { describe, expect, it } from "vitest";
import { allBookableItemsSearch } from "./search";

describe("All items search", () => {
  it("drops removed availability and date parameters", () => {
    expect(allBookableItemsSearch({ date: "2026-08-17", availability: "available-now", mine: true })).toEqual({});
  });
  it("preserves item scope and supported pagination", () => {
    expect(
      allBookableItemsSearch({ target: "IN42", q: "scope", where: "targetOwner==me", page: 2, pageSize: 50 }),
    ).toEqual({ target: "IN42", q: "scope", where: "targetOwner==me", page: 2, pageSize: 50 });
  });
});
