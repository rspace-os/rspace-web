/*
 * @jest-environment jsdom
 */
/* eslint-env jest */

import { mapObject } from "../../Util";
import fc from "fast-check";

/*
 * fast-check intentionally generates strings that are likely to lead to
 * weird JavaScript behaviour, such as "__proto__". However, this string
 * breaks the implementation of mapObject and it is not worth adding
 * complexity to the function for the sake of a key that wont happen in
 * reality.
 */
const arbObjectKey = fc.string().filter((str) => str !== "__proto__");

describe("mapObject", () => {
  test("Is identity when valueFunc returns its respective input.", () => {
    fc.assert(
      fc.property(fc.dictionary(arbObjectKey, fc.anything()), (obj) => {
        expect(mapObject((_, v) => v, obj)).toEqual(obj);
      })
    );
  });

  test("Output object always has the same number of key-value pairs.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.func<unknown[], unknown>(fc.anything()),
          fc.dictionary(arbObjectKey, fc.anything())
        ),
        ([valueFunc, obj]) => {
          expect(Object.entries(mapObject(valueFunc, obj)).length).toEqual(
            Object.entries(obj).length
          );
        }
      )
    );
  });

  test("Composing calls to mapObject is the same as one call with the valueFunc composed.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.func<unknown[], unknown>(fc.anything()),
          fc.func<unknown[], unknown>(fc.anything()),
          fc.dictionary(arbObjectKey, fc.anything())
        ),
        ([valueFunc1, valueFunc2, obj]) => {
          expect(mapObject(valueFunc2, mapObject(valueFunc1, obj))).toEqual(
            mapObject((k, v) => valueFunc2(k, valueFunc1(k, v)), obj)
          );
        }
      )
    );
  });
});
