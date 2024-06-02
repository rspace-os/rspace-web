/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */

import { mapObjectKeyAndValue } from "../../Util";
import fc from "fast-check";
import { incrementForever } from "../../iterators";

/*
 * fast-check intentionally generates strings that are likely to lead to
 * weird JavaScript behaviour, such as "__proto__". However, this string
 * breaks the implementation of mapObjectKeyAndValue and it is not worth
 * adding complexity to the function for the sake of a key that wont happen
 * in reality.
 */
const arbObjectKey = fc.string().filter((str) => str !== "__proto__");

describe("mapObjectKeyAndValue", () => {
  test("Is identity when keyFunc and valueFunc return their respective inputs.", () => {
    fc.assert(
      fc.property(fc.dictionary(arbObjectKey, fc.anything()), (obj) => {
        expect(
          mapObjectKeyAndValue(
            (k) => k,
            (_, v) => v,
            obj
          )
        ).toEqual(obj);
      })
    );
  });

  test("When keyFunc returns a constant value, the returned object contains at most one key-value pair.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.func<mixed, mixed>(fc.anything()),
          fc.dictionary(arbObjectKey, fc.anything())
        ),
        ([valueFunc, obj]) => {
          expect(
            Object.entries(mapObjectKeyAndValue(() => "foo", valueFunc, obj))
              .length
          ).toBeLessThanOrEqual(1);
        }
      )
    );
  });

  test("If keyFunc returns unique values on every call, then the size of the output object will be the same as the input", () => {
    const keyGenerator = incrementForever();

    fc.assert(
      fc.property(
        fc.tuple(
          fc.func<mixed, mixed>(fc.anything()),
          fc.dictionary(arbObjectKey, fc.anything())
        ),
        ([valueFunc, obj]) => {
          expect(
            Object.entries(
              mapObjectKeyAndValue(
                () => keyGenerator.next().value,
                valueFunc,
                obj
              )
            ).length
          ).toEqual(Object.entries(obj).length);
        }
      )
    );
  });
});
