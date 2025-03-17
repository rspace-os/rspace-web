/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";

import { match } from "../../Util";

const stringMatcher: (str?: string | null) => number | null = match([
  [(s) => s === "one", 1],
  [(s) => s === "two", 2],
  [(s) => s === "", 0],
  [() => true, null],
]);

describe("match util", () => {
  describe("When passed an array of options (pairs)", () => {
    test("if input matches, then the paired output should be returned.", () => {
      expect(stringMatcher("one")).toStrictEqual(1);
      expect(stringMatcher("two")).toBeGreaterThan(1);
      expect(stringMatcher("")).toEqual(0);
      expect(stringMatcher("x")).toBeNull();
      expect(stringMatcher()).toBeNull();
    });
  });

  test("Has the same short-circuit behaviour as logical OR.", () => {
    fc.assert(
      fc.property(fc.array<unknown>(fc.anything()), (list) => {
        const matcher = match<void, unknown>([
          ...list.map((x) => [() => Boolean(x), x] as [() => boolean, unknown]),
          [() => true, false],
        ]);
        const or = (sublist: Array<unknown>): unknown => {
          if (sublist.length === 0) return false;
          const [head, ...tail] = sublist;
          return head || or(tail);
        };
        expect(matcher()).toEqual(or(list));
      })
    );
  });
});
