/* eslint-env jest */

export function setupGlobalContext(dom) {
  const vm = require("vm");
  const window = dom.window;
  const globalContext = vm.createContext({});
  globalContext.self = window;
  globalContext.window = window;
  globalContext.document = window.document;
  return globalContext;
}

export function setupJQuery(globalContext, dom) {
  const vm = require("vm");
  const fs = require("fs");

  const window = dom.window;
  vm.runInContext(
    fs.readFileSync("../scripts/bower_components/jquery/dist/jquery.min.js"),
    globalContext,
    { filename: "jquery" }
  );
  globalContext.$ = window.$;
  globalContext.jQuery = window.jQuery;
  globalContext.navigator = window.navigator;
  vm.runInContext(
    fs.readFileSync("../scripts/bower_components/jquery-ui/jquery-ui.min.js"),
    globalContext,
    { filename: "jquery-ui" }
  );
  globalContext.$.fn.tinymce = () => {};
}
