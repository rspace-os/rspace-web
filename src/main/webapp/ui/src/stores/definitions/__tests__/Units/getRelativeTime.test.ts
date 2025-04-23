/* eslint-env jest */
import { getRelativeTime } from "../../Units";
import fc from "fast-check";

describe("getRelativeTime", () => {
  test("Simple example", () => {
    const now = new Date();
    const futureDate = new Date();
    futureDate.setHours(now.getHours() + 2);
    jest.spyOn(global, "Date").mockImplementation(() => now);
    expect(getRelativeTime(futureDate)).toBe("in 2 hours");
  });

  // this is important so that callers get the grammar right
  test("Always starts with 'in'", () => {
    const futureDate = fc.date().filter((d) => d > new Date());
    fc.assert(
      fc.property(futureDate, (date) => {
        expect(getRelativeTime(date)).toMatch(/^in /);
      })
    );
  });
});
