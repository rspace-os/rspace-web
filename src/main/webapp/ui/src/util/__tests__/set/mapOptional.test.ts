/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import { Optional } from "../../optional";
import RsSet from "../../set";

const presentIfTrue =
  <T>(f: (x: T) => boolean): ((x: T) => Optional<T>) =>
  (x) =>
    f(x) ? Optional.present(x) : Optional.empty();

describe("mapOptional", () => {
  test("Idempotence", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), fc.func(fc.boolean())),
        ([set, func]: [RsSet<unknown>, (x: unknown) => boolean]) => {
          expect(
            set
              .mapOptional(presentIfTrue(func))
              .mapOptional(presentIfTrue(func))
              .isSame(set.mapOptional(presentIfTrue(func)))
          ).toBe(true);
        }
      )
    );
  });

  test("A function that always returns Optional.empty will always result in an empty set.", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.mapOptional(() => Optional.empty<unknown>()).size).toEqual(
          0
        );
      })
    );
  });

  test("A function that always returns Optional.present will always result in an unchanged set.", () => {
    fc.assert(
      fc.property(arbRsSet(fc.anything()), (set) => {
        expect(set.mapOptional((x) => Optional.present(x)).size).toEqual(
          set.size
        );
      })
    );
  });

  test("Empty set in, empty set out", () => {
    fc.assert(
      fc.property(
        fc.constantFrom(
          () => Optional.empty<unknown>(),
          (x: unknown) => Optional.present(x)
        ),
        (func) => {
          expect(new RsSet<unknown>().mapOptional(func).size).toEqual(0);
        }
      )
    );
  });

  test("Set before is superset of set after mapOptional i.e. size is less than or equal after", () => {
    fc.assert(
      fc.property(
        fc.tuple(arbRsSet(fc.anything()), fc.func(fc.boolean())),
        ([set, func]: [RsSet<unknown>, (x: unknown) => boolean]) => {
          expect(set.isSupersetOf(set.mapOptional(presentIfTrue(func)))).toBe(
            true
          );
        }
      )
    );
  });
});
