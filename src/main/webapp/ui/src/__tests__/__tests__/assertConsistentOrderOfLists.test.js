/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import fc from "fast-check";
import { toHaveConsistentOrdering } from "../assertConsistentOrderOfLists";
import * as ArrayUtils from "../../util/ArrayUtils";

describe("assertConsistentOrderOfLists", () => {
  test("Singleton list should always return true.", () => {
    fc.assert(
      fc.property(fc.string(), (string) => {
        expect(
          toHaveConsistentOrdering(new Map([["foo", [string]]])).pass
        ).toBe(true);
      })
    );
  });
  test("Mutually exclusive lists should always be true.", () => {
    fc.assert(
      fc.property(
        fc.nat(50).chain((length) =>
          fc.tuple<Array<string>, Array<number>>(
            fc.uniqueArray(fc.string(), {
              minLength: length,
              maxLength: length,
            }),
            fc.uniqueArray(fc.integer({ min: 1, max: Math.max(1, length - 2) }))
          )
        ),
        ([strings, breakPoints]) => {
          const bounds = [0, ...breakPoints.sort(), strings.length];
          const lists = [];
          for (let i = 0; i <= breakPoints.length; i++) {
            lists.push(strings.slice(bounds[i], bounds[i + 1]));
          }
          expect(
            toHaveConsistentOrdering(new Map(lists.map((p) => [p[0], p]))).pass
          ).toBe(true);
        }
      )
    );
  });
  test("{[a,b], [b,c], [a,c]}, for any given number of pairs, should be true.", () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string(), { minLength: 2, maxLength: 100 }),
        (strings) => {
          const pairs = [];
          for (let i = 0; i < strings.length - 1; i++) {
            pairs.push([strings[i], strings[i + 1]]);
          }
          pairs.push([strings[0], strings[strings.length - 1]]);
          expect(
            toHaveConsistentOrdering(new Map(pairs.map((p) => [p[0], p]))).pass
          ).toBe(true);
        }
      )
    );
  });
  test("A cycle of pairs (i.e. {[a,b], [b,c], [c,a]} for any given number of pairs) should be false.", () => {
    fc.assert(
      fc.property(
        fc
          .integer({ min: 2, max: 10 })
          .chain((max) =>
            fc.tuple<Array<string>, Array<string>>(
              fc.uniqueArray(fc.string(), { minLength: max, maxLength: max }),
              fc.uniqueArray(fc.string(), { minLength: max, maxLength: max })
            )
          ),
        ([names, strings]) => {
          const pairs = [];
          for (let i = 0; i < strings.length; i++) {
            pairs.push([strings[i], strings[(i + 1) % strings.length]]);
          }
          expect(
            toHaveConsistentOrdering(
              new Map(ArrayUtils.zipWith(names, pairs, (n, p) => [n, p]))
            ).pass
          ).toBe(false);
        }
      )
    );
  });
});
