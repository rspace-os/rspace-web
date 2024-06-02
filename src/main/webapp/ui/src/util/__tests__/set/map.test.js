//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import fc from "fast-check";
import { arbRsSet } from "./helpers";
import RsSet from "../../set";

describe("map", () => {
  test("Size after must be less than or equal size before", () => {
    fc.assert(
      fc.property(
        fc.tuple<RsSet<mixed>, () => mixed>(
          arbRsSet(fc.anything()),
          fc.func(fc.anything())
        ),
        ([set, func]) => {
          expect(set.map(func).size).toBeLessThanOrEqual(set.size);
        }
      )
    );
  });
});
