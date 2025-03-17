/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { filterMap } from "../../Util";

describe("filterMap", () => {
  test("Output should always be smaller than or equal to input.", () => {
    fc.assert(
      fc.property(
        fc.tuple(
          fc.array(fc.tuple(fc.anything(), fc.anything())),
          fc.func<unknown[], boolean>(fc.boolean())
        ),
        ([mapContents, boolFunc]) => {
          const map = new Map(mapContents);
          expect(filterMap(map, boolFunc).size).toBeLessThanOrEqual(map.size);
        }
      )
    );
  });
});
