//@flow
/* eslint-env jest */
import fc from "fast-check";
import { isUrl } from "../../Util";

describe("isUrl", () => {
  test("When passed any url, isUrl should return true.", () => {
    fc.assert(
      fc.property(fc.webUrl(), (url) => {
        expect(isUrl(url)).toBe(true);
      })
    );
  });

  /*
   * It's not so simple to assert the false case as an arbitrarily generated
   * string could quite easily be a valid URL.
   */
});
