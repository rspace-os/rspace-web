//@flow
/* eslint-env jest */
import fc from "fast-check";
import { incrementForever } from "../../iterators";
import * as ArrayUtils from "../../ArrayUtils";

describe("incrementForever", () => {
  test("Generates a unique, sorted list of integers of arbitrary length, beginning at 0.", () => {
    fc.assert(
      fc.property(
        // indexAssertion must be less than count to prevent out of bounds error
        fc
          .tuple(fc.nat(1000), fc.nat(1000))
          .map<[number, number]>((t) =>
            t[0] < t[1] ? [t[1], t[0]] : [t[0], t[1]]
          ),
        ([count, indexAssertion]) => {
          // create list from generator
          let list = [];
          let i = 0;
          for (let x of incrementForever()) {
            if (i > count) break;
            i++;
            list.push(x);
          }

          // any arbitrary index will contain that value
          expect(list[indexAssertion]).toBe(indexAssertion);

          // the list will be sorted
          const sorted = list;
          sorted.sort();
          expect(
            ArrayUtils.zipWith(list, sorted, (a, b) => a === b).every((x) => x)
          ).toBe(true);

          // every element in the list will be unique
          const set = new Set(list);
          expect(set.size).toEqual(list.length);
        }
      )
    );
  });
});
