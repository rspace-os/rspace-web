/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import fc from "fast-check";
import { toHaveConsistentOrdering } from "../assertConsistentOrderOfLists";
import * as ArrayUtils from "../../util/ArrayUtils";

// Extend Jest with the custom matcher
expect.extend({
  toHaveConsistentOrdering,
});

describe("assertConsistentOrderOfLists", () => {
  test("Singleton list should always return true.", () => {
    fc.assert(
      fc.property(fc.string(), (string) => {
        const map = new Map<string, string[]>([["foo", [string]]]);
        expect(map).toHaveConsistentOrdering();
      })
    );
  });

  test("Mutually exclusive lists should always be true.", () => {
    fc.assert(
      fc.property(
        fc.nat(50).chain((length) =>
          fc.tuple(
            fc.uniqueArray(fc.string(), {
              minLength: length,
              maxLength: length,
            }),
            fc.uniqueArray(fc.integer({ min: 1, max: Math.max(1, length - 2) }))
          )
        ),
        ([strings, breakPoints]) => {
          const sortedBreakPoints = [...breakPoints].sort((a, b) => a - b);
          const bounds = [0, ...sortedBreakPoints, strings.length];
          const lists: Array<Array<string>> = [];
          for (let i = 0; i <= breakPoints.length; i++) {
            lists.push(strings.slice(bounds[i], bounds[i + 1]));
          }

          const listsWithKeys = lists
            .filter((list) => list.length > 0)
            .map((list) => [list[0], list] as [string, string[]]);

          const map = new Map<string, string[]>(listsWithKeys);
          expect(map).toHaveConsistentOrdering();
        }
      )
    );
  });

  test("{[a,b], [b,c], [a,c]}, for any given number of pairs, should be true.", () => {
    fc.assert(
      fc.property(
        fc.uniqueArray(fc.string(), { minLength: 2, maxLength: 100 }),
        (strings) => {
          const pairs: Array<[string, string[]]> = [];
          for (let i = 0; i < strings.length - 1; i++) {
            pairs.push([strings[i], [strings[i], strings[i + 1]]]);
          }
          pairs.push([strings[0], [strings[0], strings[strings.length - 1]]]);

          const map = new Map<string, string[]>(pairs);
          expect(map).toHaveConsistentOrdering();
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
            fc.tuple(
              fc.uniqueArray(fc.string(), { minLength: max, maxLength: max }),
              fc.uniqueArray(fc.string(), { minLength: max, maxLength: max })
            )
          ),
        ([names, strings]) => {
          // Create cycle pairs
          const stringPairs: Array<string[]> = [];
          for (let i = 0; i < strings.length; i++) {
            stringPairs.push([strings[i], strings[(i + 1) % strings.length]]);
          }

          // Make sure we have the same number of names and pairs
          const nameArray = names.slice(0, stringPairs.length);

          // Create the map with named pairs
          const map = new Map<string, string[]>(
            ArrayUtils.zipWith(nameArray, stringPairs, (name, pair) => [
              name,
              pair,
            ])
          );
          expect(map).not.toHaveConsistentOrdering();
        }
      )
    );
  });
});
