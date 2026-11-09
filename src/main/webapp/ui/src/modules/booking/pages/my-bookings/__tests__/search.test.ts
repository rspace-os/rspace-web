import { describe, expect, it } from "vitest";
import { myBookingsSearch } from "../search";

describe("My Bookings search", () => {
  it.each([undefined, "later", 42])("defaults absent or invalid period %s to upcoming", (period) => {
    expect(myBookingsSearch({ period })).toEqual({ period: "upcoming" });
  });

  it.each(["past"])("keeps the %s period", (period) => {
    expect(myBookingsSearch({ period })).toEqual({ period });
  });
});
