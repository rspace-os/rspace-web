/* eslint-env jest */

import { JSDOM } from "jsdom";
import "@testing-library/jest-dom";
import { setupGlobalContext, setupJQuery } from "./helpers";
import fc from "fast-check";

function setupGlobal(globalContext) {
  const fs = require("fs");
  const vm = require("vm");

  vm.runInContext(fs.readFileSync("../scripts/global.js"), globalContext, {
    filename: "global.js",
  });
}

describe("Global", () => {
  describe("getJsonParamsFromUrl", () => {
    it("Should perform the inverse of URLSearchParam's toString method", () => {
      fc.assert(
        fc.property(
          fc.dictionary(
            fc
              .string({ minLength: 1 })
              .filter(
                (s) =>
                  s !== "__proto__" &&
                  s !== "constructor" &&
                  s !== "valueOf" &&
                  s !== "toString"
              ),
            fc.webQueryParameters(),
            {
              minKeys: 1,
            }
          ),
          (obj) => {
            const dom = new JSDOM("");
            const globalContext = setupGlobalContext(dom);
            setupJQuery(globalContext, dom);
            setupGlobal(globalContext);

            const searchParams = new URLSearchParams(obj);
            const toStringAndBackAgain = globalContext.RS.getJsonParamsFromUrl(
              "?" + searchParams.toString()
            );
            expect(Object.values(toStringAndBackAgain)).toEqual(
              Object.values(obj)
            );
          }
        )
      );
    });
  });
});
