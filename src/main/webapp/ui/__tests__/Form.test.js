/* eslint-env jest */

import { JSDOM } from "jsdom";
import "@testing-library/jest-dom";
import { setupGlobalContext, setupJQuery } from "./helpers";

function setupFormJs(globalContext) {
  const fs = require("fs");
  const vm = require("vm");

  globalContext.editable = "VIEW_MODE";
  vm.runInContext(
    fs.readFileSync("../scripts/pages/workspace/editor/form.js"),
    globalContext,
    { filename: "form.js" }
  );
}

describe("Form", () => {
  it("addError should add passed message to DOM.", () => {
    const dom = new JSDOM("<div id='errorSummary'></div>");
    const globalContext = setupGlobalContext(dom);
    setupJQuery(globalContext, dom);
    setupFormJs(globalContext);

    globalContext.addError(null, "foo");
    expect(
      // eslint-disable-next-line testing-library/no-node-access
      dom.window.document.querySelector("#errorSummary")
    ).toHaveTextContent(/foo/);
  });

  it("When parsing radio options from a csv file, options that have a comma are not supported.", () => {
    const dom = new JSDOM("");
    const globalContext = setupGlobalContext(dom);
    setupJQuery(globalContext, dom);
    setupFormJs(globalContext);

    expect(globalContext.parseRadioOptions('"foo, bar", baz', { isCSV: true })).toEqual([
      '"foo',
      'bar"',
      "baz",
    ]);
  });
});
