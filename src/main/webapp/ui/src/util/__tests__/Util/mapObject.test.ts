import { describe, expect, test } from 'vitest';
import { mapObject } from "../../Util";
import fc from "fast-check";
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

