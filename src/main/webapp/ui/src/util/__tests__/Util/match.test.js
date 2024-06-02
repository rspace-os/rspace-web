/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";

import { match } from "../../Util";

const stringMatcher: (str: ?string) => ?number = match([
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
      fc.property(fc.array<mixed>(fc.anything()), (list) => {
        const matcher = match<void, mixed>([
          ...list.map((x) => [() => Boolean(x), x]),
          [() => true, false],
        ]);
        const or = (sublist: Array<mixed>): boolean => {
          if (sublist.length === 0) return false;
          const [head, ...tail] = sublist;
          // $FlowExpectedError[incompatible-return] Flow doesn't like as relying on truthiness of || operator
          return head || or(tail);
        };
        expect(matcher()).toEqual(or(list));
      })
    );
  });
});
