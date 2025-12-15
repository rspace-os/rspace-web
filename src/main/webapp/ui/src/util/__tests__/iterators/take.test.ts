/* eslint-env jest */
import { take, incrementForever } from "../../iterators";
import fc from "fast-check";

describe("take", () => {
  test("Returns an iterator, of desired length, from the head of another iterator.", () => {
    // Because take works over any iterator it will take from an array
    expect([...take([1, 2, 3], 2)]).toEqual([1, 2]);

    // Or a set
    expect([...take(new Set([1, 2, 3]), 2)]).toEqual([1, 2]);

    // Or a map
    const m = new Map<string, number>();
    m.set("foo", 3);
    m.set("bar", 7);
    m.set("baz", 13);
    expect([...take(m, 2)]).toEqual([
      ["foo", 3],
      ["bar", 7],
    ]);

    // Or a string
    expect([...take("foo", 2)]).toEqual(["f", "o"]);

    // Or even an infinite generator
    expect([...take(incrementForever(), 2)]).toEqual([0, 1]);

    // It can also be composed with itself
    expect([...take(take([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 5), 3)]).toEqual([
      1, 2, 3,
    ]);
  });

  test("Length should be the minimum of the input iterator and the passed number", () => {
    fc.assert(
      fc.property(
        /// 1000 limit is for performance reasons
        fc.tuple(fc.array(fc.anything(), { maxLength: 1000 }), fc.nat(1000)),
        ([iterator, count]) => {
          expect([...take(iterator, count)].length).toEqual(
            Math.min(iterator.length, count)
          );
        }
      )
    );
  });
});
