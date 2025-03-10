/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import * as ArrayUtils from "../../ArrayUtils";
import { Optional } from "../../optional";
import fc from "fast-check";

const presentIfTrue =
  <T>(f: (t: T) => boolean): ((t: T) => Optional<T>) =>
  (x) =>
    f(x) ? Optional.present(x) : Optional.empty();

describe("mapOptional", () => {
  test("Example", () => {
    const data: Array<
      { tag: "hasNumber"; value: number } | { tag: "hasString"; value: string }
    > = [
      { tag: "hasNumber", value: 4 },
      { tag: "hasString", value: "hello" },
      { tag: "hasNumber", value: 3 },
    ];

    // note how the type has changed, which it would not have done using filter
    const justNumbers: Array<{ tag: "hasNumber"; value: number }> =
      ArrayUtils.mapOptional(
        (obj) =>
          obj.tag === "hasNumber" ? Optional.present(obj) : Optional.empty(),
        data
      );

    expect(justNumbers).toEqual([
      { tag: "hasNumber", value: 4 },
      { tag: "hasNumber", value: 3 },
    ]);
  });

  test("Idempotence", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.array(fc.anything()), fc.func(fc.boolean())),
        ([array, func]) => {
          expect(
            ArrayUtils.mapOptional(
              presentIfTrue(func),
              ArrayUtils.mapOptional(presentIfTrue(func), array)
            )
          ).toEqual(ArrayUtils.mapOptional(presentIfTrue(func), array));
        }
      )
    );
  });

  test("A function that always returns Optional.empty will always result in an empty array.", () => {
    fc.assert(
      fc.property(fc.array(fc.anything()), (array) => {
        expect(ArrayUtils.mapOptional(() => Optional.empty(), array)).toEqual(
          []
        );
      })
    );
  });

  test("A function that always returns Optional.present will always result in an unchanged array.", () => {
    fc.assert(
      fc.property(fc.array(fc.anything()), (array) => {
        expect(
          ArrayUtils.mapOptional((x) => Optional.present(x), array)
        ).toEqual(array);
      })
    );
  });

  test("Empty list in, empty list out", () => {
    fc.assert(
      fc.property(
        fc.constantFrom(
          () => Optional.empty<unknown>(),
          (x: unknown) => Optional.present(x)
        ),
        (func) => {
          expect(ArrayUtils.mapOptional(func, [])).toEqual([]);
        }
      )
    );
  });

  test("Length of output will always be less than or equal to length of input.", () => {
    fc.assert(
      fc.property(
        fc.tuple(fc.array(fc.anything()), fc.func(fc.boolean())),
        ([array, func]) => {
          expect(
            ArrayUtils.mapOptional(presentIfTrue(func), array).length
          ).toBeLessThanOrEqual(array.length);
        }
      )
    );
  });
});
